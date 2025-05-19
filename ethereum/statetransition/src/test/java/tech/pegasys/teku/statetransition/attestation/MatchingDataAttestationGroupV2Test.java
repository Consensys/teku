/*
 * Copyright Consensys Software Inc., 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.attestation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.statetransition.attestation.AggregatorUtil.aggregateAttestations;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
class MatchingDataAttestationGroupV2Test {
  private static final UInt64 SLOT = UInt64.valueOf(1234);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private AttestationSchema<Attestation> attestationSchema;

  private AttestationData attestationData;

  private MatchingDataAttestationGroupV2 group;
  private Int2IntMap committeeSizes;

  @BeforeEach
  public void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    attestationSchema = spec.getGenesisSchemaDefinitions().getAttestationSchema();
    dataStructureUtil = specContext.getDataStructureUtil();
    attestationData = dataStructureUtil.randomAttestationData(SLOT);
    committeeSizes = new Int2IntOpenHashMap();
    committeeSizes.put(0, 10);
    committeeSizes.put(1, 10);
    group =
        new MatchingDataAttestationGroupV2(
            spec, System::nanoTime, attestationData, Optional.of(committeeSizes), false);
  }

  @TestTemplate
  public void isEmpty_shouldBeEmptyInitially() {
    assertThat(group.isEmpty()).isTrue();
  }

  @TestTemplate
  public void isEmpty_shouldNotBeEmptyWhenAnAttestationIsAdded() {
    addPooledAttestation(1);
    assertThat(group.isEmpty()).isFalse();
  }

  @TestTemplate
  public void isEmpty_shouldBeEmptyAfterAttestationRemoved() {
    final Attestation attestation = toAttestation(addPooledAttestation(1));
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, attestation);

    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @TestTemplate
  public void onAttestationIncludedInBlock_shouldRemoveAttestationEvenWhenInstanceIsDifferent() {
    final Attestation attestation = toAttestation(addPooledAttestation(1));
    final Attestation copy = attestationSchema.sszDeserialize(attestation.sszSerialize());
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, copy);

    verifyGroupContainsExactly(); // empty
    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @TestTemplate
  public void onAttestationIncludedInBlock_multipleCallsShouldAggregate() {

    // Create attestations that will be removed
    final PooledAttestation attestation1 = createPooledAttestation(1);
    final PooledAttestation attestation2 = createPooledAttestation(2);

    // Add some attestations
    final PooledAttestation attestation3 = addPooledAttestation(3);
    addPooledAttestation(1, 2); // This will be an aggregate, not single

    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation1));
    assertThat(numRemoved).isEqualTo(0); // Attestation (1) is covered by (1,2) which is still there
    numRemoved += group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation2));
    // Attestation (2) is covered by (1,2).
    // If attestation (1,2) was added, and (1) and (2) were added:
    // onAttestationIncludedInBlock(attestation1) -> if (1,2) is present, (1) is covered.
    // includedValidators will get bit 1. Attestation (1) is removed.
    // onAttestationIncludedInBlock(attestation2) -> includedValidators gets bit 2. Attestation (2)
    // is removed.
    // The attestation (1,2) will be removed if both 1 and 2 are included.
    // The original test structure had `addPooledAttestation(1,2)` which creates an aggregate.
    // This aggregate (1,2) will be removed once both 1 and 2 are included.

    // Let's trace:
    // add(3) -> group has (3)
    // add(1,2) -> group has (3), (1,2)
    // onBlock(att1=(1)): includedValidators=(1). (3) not removed. (1,2) not removed (2 not
    // covered). size=2. numRemoved=0
    // onBlock(att2=(2)): includedValidators=(1,2). (3) not removed. (1,2) is removed. size=1.
    // numRemoved for this call = 1
    // Total numRemoved = 1.

    // Original test `assertThat(numRemoved).isEqualTo(1);` implies total.
    // My trace for V2:
    // add(3) -> singleA_by_idx {0:[3]} or att_by_count {1:[(3)]} depending on Electra
    // add(1,2) -> att_by_count {2:[(1,2)]}
    // onBlock(att1=toAttestation(createPooledAttestation(1))):
    //   includedValidators gets bit 1.
    //   single (3) not removed. aggregate (1,2) not removed.
    //   If (1) was also added explicitly, it would be removed.
    //   numRemoved = 0.
    // onBlock(att2=toAttestation(createPooledAttestation(2))):
    //   includedValidators gets bit 1,2.
    //   single (3) not removed. aggregate (1,2) is now fully covered and removed.
    //   numRemoved for this call = 1.
    // Total numRemoved = 0 + 1 = 1. This matches.

    assertThat(numRemoved).isEqualTo(1);
    verifyGroupContainsExactly(toPooledAttestationWithData(attestation3));
  }

  @TestTemplate
  public void
      onAttestationIncludedInBlock_shouldRemoveAttestationsThatAreAggregatedIntoRemovedAttestation() {
    final PooledAttestation attestation1 = addPooledAttestation(1);
    final PooledAttestation attestation2 = addPooledAttestation(2);
    final PooledAttestation attestation3 = addPooledAttestation(3);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(toAttestation(attestation1), toAttestation(attestation2)));
    // aggregateAttestations(att1, att2) creates an attestation with bits (1,2)
    // This makes attestation1 and attestation2 redundant.
    verifyGroupContainsExactly(toPooledAttestationWithData(attestation3));
    assertThat(numRemoved).isEqualTo(2);
  }

  @TestTemplate
  public void add_shouldIgnoreAttestationWhoseBitsHaveAllBeenRemoved() {
    final PooledAttestation attestation1 = createPooledAttestation(1);
    final PooledAttestation attestation2 = createPooledAttestation(2);
    final PooledAttestation attestationToIgnore = createPooledAttestation(1, 2);

    group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation1));
    group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation2));
    // After these, includedValidators has bits 1 and 2.

    assertThat(group.add(attestationToIgnore, Optional.empty())).isFalse();
    verifyGroupContainsExactly(); // empty
  }

  // TODO: is this an aggregate flow test?

  @TestTemplate
  public void add_shouldAggregateAttestationsFromSameCommittee(final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation attestation1 = addPooledAttestation(Optional.of(0), 1); // single
    final PooledAttestation attestation2 = addPooledAttestation(Optional.of(1), 2); // single
    final PooledAttestation attestation3 = addPooledAttestation(Optional.of(1), 3); // single

    assertThat(group.streamForAggregationProduction(Optional.of(UInt64.ZERO), Long.MAX_VALUE))
        .containsExactly(toPooledAttestationWithData(attestation1));

    final Attestation expected =
        aggregateAttestations(toAttestation(attestation2), toAttestation(attestation3));

    verifyGroupContainsExactly(
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expected, committeeSizes))));
  }

  @TestTemplate
  public void add_shouldIgnoreDuplicateAttestations() {
    final PooledAttestation attestation = addPooledAttestation(1, 2);
    final PooledAttestation copy =
        PooledAttestation.fromValidatableAttestation(
            ValidatableAttestation.from(
                spec,
                attestationSchema.sszDeserialize(toAttestation(attestation).sszSerialize()),
                committeeSizes),
            validatorBitToValidatorIndex(1, 2));

    assertThat(group.add(copy, Optional.empty())).isFalse();

    verifyGroupContainsExactly(toPooledAttestationWithData(attestation));
  }

  // TODO: migrate iterator_ to apiRequest?

  @TestTemplate
  public void iterator_shouldAggregateAttestationsWhereValidatorsDoNotOverlap() {
    final PooledAttestation attestation1 = addPooledAttestation(1);
    final PooledAttestation attestation2 = addPooledAttestation(2);

    final Attestation expected =
        aggregateAttestations(toAttestation(attestation1), toAttestation(attestation2));

    verifyGroupContainsExactly(
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expected, committeeSizes))));
  }

  // TODO: block production flow

  @TestTemplate
  public void iterator_shouldAggregateAttestationsWithMoreValidatorsFirst() {
    final PooledAttestation bigAttestation = addPooledAttestation(1, 3, 5, 7);
    final PooledAttestation mediumAttestation = addPooledAttestation(3, 5, 9);
    final PooledAttestation littleAttestation = addPooledAttestation(2, 4);

    verifyGroupContainsExactly(
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec,
                    aggregateAttestations(
                        toAttestation(bigAttestation), toAttestation(littleAttestation)),
                    committeeSizes))),
        toPooledAttestationWithData(mediumAttestation));
  }

  private PooledAttestationWithData toPooledAttestationWithDataWithSortedValidatorIndices(
      final PooledAttestationWithData attestation) {
    return new PooledAttestationWithData(
        attestation.data(),
        new PooledAttestation(
            attestation.pooledAttestation().bits(),
            attestation.pooledAttestation().validatorIndices().map(TreeSet::new).map(List::copyOf),
            attestation.pooledAttestation().aggregatedSignature(),
            attestation.pooledAttestation().isSingleAttestation()));
  }

  void verifyGroupContainsExactly(final PooledAttestationWithData... expectedAttestations) {
    // streamForApiRequest with no committee index is the only stream that gives us all attestations
    assertThat(
            group
                .streamForApiRequest(
                    Optional.empty(),
                    spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(ELECTRA))
                // we convert to a sorted ValidatorIndices list so we can compare
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  @TestTemplate
  public void iterator_electra_shouldAggregateSkipSingleAttestationsInBlockProduction(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation bigAttestation = addPooledAttestation(1, 3, 5, 7); // aggregate
    final PooledAttestation mediumAttestation = addPooledAttestation(3, 5, 9); // aggregate
    addPooledAttestation(2); // single, goes to singleAttestationsByCommitteeIndex in V2

    // streamForBlockProduction in V2 sources from attestationsByValidatorCount,
    // so it won't see the single attestation.
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactly(
            toPooledAttestationWithData(bigAttestation),
            toPooledAttestationWithData(mediumAttestation));
  }

  @TestTemplate
  public void iterator_shouldNotAggregateAttestationsWhenValidatorsOverlap() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 5);
    final PooledAttestation attestation2 = addPooledAttestation(1, 2, 3);

    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactlyInAnyOrder(
            toPooledAttestationWithData(attestation1), toPooledAttestationWithData((attestation2)));
  }

  @TestTemplate
  public void iterator_shouldOmitAttestationsThatAreAlreadyIncludedInTheAggregate() {
    final PooledAttestation aggregate = addPooledAttestation(1, 2, 3);
    addPooledAttestation(2); // This would be a single attestation

    // streamForBlockProduction will only show 'aggregate' (if not Electra, or if Electra and it's
    // an aggregate)
    // If Electra, addPooledAttestation(2) is single, not seen by streamForBlockProduction.
    // If not Electra, addPooledAttestation(2) is an aggregate with 1 bit.
    //   The AggregatingIterator for streamForBlockProduction should handle this.
    //   The iterator first processes (1,2,3). Then (2) is redundant.
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactly(toPooledAttestationWithData(aggregate));
  }

  @TestTemplate
  void iterator_shouldOmitAttestationsThatOverlapWithFirstAttestationAndAreRedundantWithCombined() {
    final PooledAttestation useful1 = addPooledAttestation(1, 2, 3);
    addPooledAttestation(2, 4);
    final PooledAttestation useful2 = addPooledAttestation(4);

    final PooledAttestationWithData expected =
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec,
                    aggregateAttestations(toAttestation(useful1), toAttestation(useful2)),
                    committeeSizes)));
    // streamForAggregationProduction with committeeIndex 0 will be used here as per original test.
    // This assumes pre-Electra behavior for this test logic w.r.t committee index 0,
    // or that these attestations are for committee 0.
    verifyGroupContainsExactly(expected);
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldRemoveAttestationsMadeRedundant() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 3, 4);
    final PooledAttestation attestation2 = addPooledAttestation(1, 5, 7);
    final PooledAttestation attestation3 = addPooledAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactlyInAnyOrder(
            toPooledAttestationWithData(attestation1),
            toPooledAttestationWithData(attestation2),
            toPooledAttestationWithData(attestation3));

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6, 7)));

    assertThat(group.size()).isZero();
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE)).isEmpty();
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldNotRemoveAttestationsWithAdditionalValidators() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 3, 4);
    final PooledAttestation attestation2 = addPooledAttestation(1, 5, 7);
    final PooledAttestation attestation3 = addPooledAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactlyInAnyOrder(
            toPooledAttestationWithData(attestation1),
            toPooledAttestationWithData(attestation2),
            toPooledAttestationWithData(attestation3));

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    // Validator 7 is still relevant (from attestation2)
    assertThat(group.size()).isEqualTo(1);
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactly(toPooledAttestationWithData(attestation2));
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldNotAddAttestationsAlreadySeenInBlocks() {
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    assertThat(group.add(createPooledAttestation(1), Optional.empty())).isFalse();
    assertThat(group.add(createPooledAttestation(1, 2, 3, 4, 5, 6), Optional.empty())).isFalse();
    assertThat(group.add(createPooledAttestation(2, 3), Optional.empty())).isFalse();
  }

  @TestTemplate
  void onReorg_shouldAllowReadingAttestationsThatAreNoLongerRedundant() {
    final PooledAttestation attestation = createPooledAttestation(3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    assertThat(group.add(attestation, Optional.empty())).isFalse();

    group.onReorg(UInt64.ZERO);

    assertThat(group.add(attestation, Optional.empty())).isTrue();
    assertThat(group.size()).isEqualTo(1);
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactly(toPooledAttestationWithData(attestation));
  }

  @TestTemplate
  void onReorg_shouldNotAllowReadingAttestationsThatAreStillRedundant() {
    final PooledAttestation attestation1 = createPooledAttestation(3, 4);
    final PooledAttestation attestation2 = createPooledAttestation(1, 2, 3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(2, 3, 4)));
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(3), toAttestation(createPooledAttestation(1, 2, 3, 4)));

    assertThat(group.add(attestation1, Optional.empty())).isFalse();
    assertThat(group.add(attestation2, Optional.empty())).isFalse();

    group.onReorg(UInt64.valueOf(2)); // Block at slot 3 removed, block at slot 1 remains.

    // Attestation from slot 1 (2,3,4) still makes (3,4) redundant.
    assertThat(group.add(attestation1, Optional.empty())).isFalse();

    // Attestation (1,2,3,4) has validator 1, which is not in (2,3,4). So it can be added.
    assertThat(group.add(attestation2, Optional.empty())).isTrue();
    assertThat(group.size()).isEqualTo(1);
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactly(toPooledAttestationWithData(attestation2));
  }

  @TestTemplate
  public void size() {
    assertThat(group.size()).isEqualTo(0);
    final PooledAttestationWithData attestation1Data =
        toPooledAttestationWithData(addPooledAttestation(1));
    assertThat(group.size()).isEqualTo(1);
    final PooledAttestationWithData attestation2Data =
        toPooledAttestationWithData(addPooledAttestation(2));
    assertThat(group.size()).isEqualTo(2);
    addPooledAttestation(3, 4);
    assertThat(group.size()).isEqualTo(3);
    addPooledAttestation(1, 2);
    assertThat(group.size()).isEqualTo(4);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(
                attestation1Data.toAttestation(attestationSchema),
                attestation2Data.toAttestation(attestationSchema)));
    // Aggregate of (1) and (2) has bits (1,2).
    // This covers original (1), original (2), and (1,2).
    // Attestation (3,4) remains.
    assertThat(numRemoved).isEqualTo(3);
    assertThat(group.size()).isEqualTo(1);
  }

  void verifyStreamForAggregationProductionContainsExactly(
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForAggregationProduction(Optional.of(UInt64.ZERO), Long.MAX_VALUE)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  void verifyStreamForAggregationProductionPhase0ContainsExactly(
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForAggregationProduction(Optional.empty(), Long.MAX_VALUE)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  void verifyStreamForBlockProductionContainsExactly(
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForBlockProduction(Long.MAX_VALUE)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  void verifyStreamForApiRequest(
      final Optional<UInt64> committeeIndex,
      final boolean requiresCommitteeBits,
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForApiRequest(committeeIndex, requiresCommitteeBits)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  private PooledAttestation addPooledAttestation(final int... validators) {
    return addPooledAttestation(Optional.empty(), validators);
  }

  private PooledAttestation addPooledAttestation(
      final Optional<Integer> committeeIndex, final int... validators) {
    final PooledAttestation attestation = createPooledAttestation(committeeIndex, validators);
    final boolean added = group.add(attestation, Optional.empty());
    assertThat(added).isTrue();
    return attestation;
  }

  private PooledAttestation createPooledAttestation(final int... validators) {
    return createPooledAttestation(Optional.empty(), validators);
  }

  private PooledAttestation createPooledAttestation(
      final Optional<Integer> committeeIndex, final int... validators) {
    final SszBitlist aggregationBits =
        attestationSchema.getAggregationBitsSchema().ofBits(10, validators);
    final boolean isElectra = spec.atSlot(SLOT).getMilestone().isGreaterThanOrEqualTo(ELECTRA);
    final Supplier<SszBitvector> committeeBits;
    final Optional<Attestation> singleAttestation;
    final int resolvedCommitteeIndex = committeeIndex.orElse(0);

    if (validators.length == 1 && isElectra) {
      singleAttestation =
          Optional.of(
              spec.getGenesisSchemaDefinitions()
                  .toVersionElectra()
                  .orElseThrow()
                  .getSingleAttestationSchema()
                  .create(
                      UInt64.valueOf(resolvedCommitteeIndex),
                      UInt64.valueOf(validators[0]),
                      attestationData,
                      dataStructureUtil.randomSignature()));
    } else {
      singleAttestation = Optional.empty();
    }

    if (spec.atSlot(SLOT).getMilestone().isGreaterThanOrEqualTo(ELECTRA)) {
      committeeBits =
          () ->
              attestationSchema
                  .getCommitteeBitsSchema()
                  .orElseThrow()
                  .ofBits(resolvedCommitteeIndex);
    } else {
      committeeBits = () -> null;
    }

    final Attestation attestation =
        attestationSchema.create(
            aggregationBits, attestationData, dataStructureUtil.randomSignature(), committeeBits);

    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, singleAttestation.orElse(attestation), committeeSizes);

    singleAttestation.ifPresent(
        __ -> validatableAttestation.convertToAggregatedFormatFromSingleAttestation(attestation));

    return PooledAttestation.fromValidatableAttestation(
        validatableAttestation, validatorBitToValidatorIndex(validators));
  }

  private List<UInt64> validatorBitToValidatorIndex(final int... validatorBits) {
    return Arrays.stream(validatorBits)
        .sorted() // make sure we sort validators so that we can compare the list without converting
        // it to a set
        .mapToObj(bit -> UInt64.valueOf(bit + 100))
        .toList();
  }

  private Attestation toAttestation(final PooledAttestation pooledAttestation) {
    return attestationSchema.create(
        pooledAttestation.bits().getAggregationBits(),
        attestationData,
        pooledAttestation.aggregatedSignature(),
        pooledAttestation.bits()::getCommitteeBits); // Supplier for committee bits
  }

  private PooledAttestationWithData toPooledAttestationWithData(
      final PooledAttestation pooledAttestation) {
    // Use the attestation created by toAttestation to ensure consistency if PooledAttestation
    // doesn't directly hold a full Attestation object in the exact schema form.
    return toPooledAttestationWithData(toAttestation(pooledAttestation));
  }

  private PooledAttestationWithData toPooledAttestationWithData(final Attestation attestation) {
    return new PooledAttestationWithData(
        attestationData,
        PooledAttestation.fromValidatableAttestation(
            ValidatableAttestation.from(spec, attestation, committeeSizes),
            validatorBitToValidatorIndex(
                attestation.getAggregationBits().getAllSetBits().toIntArray())));
  }
}
