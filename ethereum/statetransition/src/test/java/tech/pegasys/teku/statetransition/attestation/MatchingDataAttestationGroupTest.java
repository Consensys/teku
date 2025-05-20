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
import java.util.Optional;
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
class MatchingDataAttestationGroupTest {
  private static final UInt64 SLOT = UInt64.valueOf(1234);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private AttestationSchema<Attestation> attestationSchema;

  private AttestationData attestationData;

  private MatchingDataAttestationGroup group;
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
    group = new MatchingDataAttestationGroup(spec, attestationData, Optional.of(committeeSizes));
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
  public void remove_shouldRemoveAttestationEvenWhenInstanceIsDifferent() {
    final Attestation attestation = toAttestation(addPooledAttestation(1));
    final Attestation copy = attestationSchema.sszDeserialize(attestation.sszSerialize());
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, copy);

    assertThat(group.stream()).isEmpty();
    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @TestTemplate
  public void remove_multipleCallsToRemoveShouldAggregate() {

    // Create attestations that will be removed
    final PooledAttestation attestation1 = createPooledAttestation(1);
    final PooledAttestation attestation2 = createPooledAttestation(2);

    // Add some attestations
    final PooledAttestation attestation3 = addPooledAttestation(3);
    addPooledAttestation(1, 2);

    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation1));
    assertThat(numRemoved).isEqualTo(0);
    numRemoved += group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation2));
    assertThat(numRemoved).isEqualTo(1);
    assertThat(group.stream(Optional.of(UInt64.ZERO)))
        .containsExactly(toPooledAttestationWithData(attestation3));
  }

  @TestTemplate
  public void remove_shouldRemoveAttestationsThatAreAggregatedIntoRemovedAttestation() {
    final PooledAttestation attestation1 = addPooledAttestation(1);
    final PooledAttestation attestation2 = addPooledAttestation(2);
    final PooledAttestation attestation3 = addPooledAttestation(3);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(
                committeeSizes, toAttestation(attestation1), toAttestation(attestation2)));

    assertThat(group.stream(Optional.of(UInt64.ZERO)))
        .containsExactly(toPooledAttestationWithData(attestation3));
    assertThat(numRemoved).isEqualTo(2); // the one attestation is still there, and we've removed 2.
  }

  @TestTemplate
  public void add_shouldIgnoreAttestationWhoseBitsHaveAllBeenRemoved() {
    // Create attestations that will be removed
    final PooledAttestation attestation1 = createPooledAttestation(1);
    final PooledAttestation attestation2 = createPooledAttestation(2);

    // Create attestation to be added / ignored
    final PooledAttestation attestationToIgnore = createPooledAttestation(1, 2);

    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation1));
    numRemoved += group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation2));
    assertThat(numRemoved).isEqualTo(0);

    assertThat(group.add(attestationToIgnore, Optional.empty())).isFalse();
    assertThat(group.stream()).isEmpty();
  }

  @TestTemplate
  public void add_shouldAggregateAttestationsFromSameCommittee(final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation attestation1 = addPooledAttestation(Optional.of(0), 1);
    final PooledAttestation attestation2 = addPooledAttestation(Optional.of(1), 2);
    final PooledAttestation attestation3 = addPooledAttestation(Optional.of(1), 3);

    assertThat(group.stream(Optional.of(UInt64.ZERO)))
        .containsExactly(toPooledAttestationWithData(attestation1));

    final Attestation expected =
        aggregateAttestations(
            committeeSizes, toAttestation(attestation2), toAttestation(attestation3));

    assertThat(group.stream(Optional.of(UInt64.ONE)))
        .containsExactly(
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
                committeeSizes));

    assertThat(group.add(copy, Optional.empty())).isFalse();
    assertThat(group.stream()).containsExactly(toPooledAttestationWithData(attestation));
  }

  @TestTemplate
  public void iterator_shouldAggregateAttestationsWhereValidatorsDoNotOverlap() {
    final PooledAttestation attestation1 = addPooledAttestation(1);
    final PooledAttestation attestation2 = addPooledAttestation(2);

    final Attestation expected =
        aggregateAttestations(
            committeeSizes, toAttestation(attestation1), toAttestation(attestation2));

    assertThat(group.stream(Optional.of(UInt64.ZERO)))
        .containsExactlyInAnyOrder(
            toPooledAttestationWithData(
                PooledAttestation.fromValidatableAttestation(
                    ValidatableAttestation.from(spec, expected, committeeSizes))));
  }

  @TestTemplate
  public void iterator_shouldAggregateAttestationsWithMoreValidatorsFirst() {
    final PooledAttestation bigAttestation = addPooledAttestation(1, 3, 5, 7);
    final PooledAttestation mediumAttestation = addPooledAttestation(3, 5, 9);
    final PooledAttestation littleAttestation = addPooledAttestation(2, 4);

    assertThat(group)
        .containsExactly(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec,
                    aggregateAttestations(
                        committeeSizes,
                        toAttestation(bigAttestation),
                        toAttestation(littleAttestation)),
                    committeeSizes)),
            mediumAttestation);
  }

  @TestTemplate
  public void iterator_electra_shouldAggregateSkipSingleAttestationsInBlockProduction(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation bigAttestation = addPooledAttestation(1, 3, 5, 7);
    final PooledAttestation mediumAttestation = addPooledAttestation(3, 5, 9);
    addPooledAttestation(2);

    assertThat(group).containsExactly(bigAttestation, mediumAttestation);
  }

  @TestTemplate
  public void iterator_shouldNotAggregateAttestationsWhenValidatorsOverlap() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 5);
    final PooledAttestation attestation2 = addPooledAttestation(1, 2, 3);

    assertThat(group).containsExactlyInAnyOrder(attestation1, attestation2);
  }

  @TestTemplate
  public void iterator_shouldOmitAttestationsThatAreAlreadyIncludedInTheAggregate() {
    final PooledAttestation aggregate = addPooledAttestation(1, 2, 3);
    addPooledAttestation(2);

    assertThat(group).containsExactly(aggregate);
  }

  @TestTemplate
  void iterator_shouldOmitAttestationsThatOverlapWithFirstAttestationAndAreRedundantWithCombined() {
    // First aggregate created will have validators 1,2,3,4 which makes the 2,4 attestation
    // redundant, but iteration will have already passed it before it becomes redundant
    final PooledAttestation useful1 = addPooledAttestation(1, 2, 3);
    addPooledAttestation(2, 4);
    final PooledAttestation useful2 = addPooledAttestation(4);

    final PooledAttestationWithData expected =
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec,
                    aggregateAttestations(
                        committeeSizes, toAttestation(useful1), toAttestation(useful2)),
                    committeeSizes)));

    assertThat(group.stream(Optional.of(UInt64.ZERO))).containsExactly(expected);
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldRemoveAttestationsMadeRedundant() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 3, 4);
    final PooledAttestation attestation2 = addPooledAttestation(1, 5, 7);
    final PooledAttestation attestation3 = addPooledAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group).containsExactly(attestation1, attestation2, attestation3);

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6, 7)));

    assertThat(group.size()).isZero();
    assertThat(group).isEmpty();
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldNotRemoveAttestationsWithAdditionalValidators() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 3, 4);
    final PooledAttestation attestation2 = addPooledAttestation(1, 5, 7);
    final PooledAttestation attestation3 = addPooledAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group).containsExactly(attestation1, attestation2, attestation3);

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    // Validator 7 is still relevant
    assertThat(group.size()).isEqualTo(1);
    assertThat(group).containsExactly(attestation2);
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldNotAddAttestationsAlreadySeenInBlocks() {
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    // Can't add redundant attestation
    assertThat(group.add(createPooledAttestation(1), Optional.empty())).isFalse();
    assertThat(group.add(createPooledAttestation(1, 2, 3, 4, 5, 6), Optional.empty())).isFalse();
    assertThat(group.add(createPooledAttestation(2, 3), Optional.empty())).isFalse();
  }

  @TestTemplate
  void onReorg_shouldAllowReadingAttestationsThatAreNoLongerRedundant() {
    final PooledAttestation attestation = createPooledAttestation(3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    // Can't add redundant attestation
    assertThat(group.add(attestation, Optional.empty())).isFalse();

    // Reorg removes seen attestation
    group.onReorg(UInt64.ZERO);

    // Can now add attestation
    assertThat(group.add(attestation, Optional.empty())).isTrue();
    assertThat(group.size()).isEqualTo(1);
    assertThat(group).containsExactly(attestation);
  }

  @TestTemplate
  void onReorg_shouldNotAllowReadingAttestationsThatAreStillRedundant() {
    final PooledAttestation attestation1 = createPooledAttestation(3, 4);
    final PooledAttestation attestation2 = createPooledAttestation(1, 2, 3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(2, 3, 4)));
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(3), toAttestation(createPooledAttestation(1, 2, 3, 4)));

    // Can't add redundant attestation
    assertThat(group.add(attestation1, Optional.empty())).isFalse();
    assertThat(group.add(attestation2, Optional.empty())).isFalse();

    // Reorg removes only the last seen attestation
    group.onReorg(UInt64.valueOf(2));

    // Still can't add attestation1 because 3 and 4 are included attestation
    assertThat(group.add(attestation1, Optional.empty())).isFalse();

    // But can add attestation2 because validator 1 is still relevant
    assertThat(group.add(attestation2, Optional.empty())).isTrue();
    assertThat(group.size()).isEqualTo(1);
    assertThat(group).containsExactly(attestation2);
  }

  @TestTemplate
  public void size() {
    assertThat(group.size()).isEqualTo(0);
    final PooledAttestationWithData attestation1 =
        toPooledAttestationWithData(addPooledAttestation(1));
    assertThat(group.size()).isEqualTo(1);
    final PooledAttestationWithData attestation2 =
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
                committeeSizes,
                attestation1.toAttestation(attestationSchema),
                attestation2.toAttestation(attestationSchema)));

    assertThat(numRemoved).isEqualTo(3);
    assertThat(group.size()).isEqualTo(1);
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

    return PooledAttestation.fromValidatableAttestation(validatableAttestation);
  }

  private Attestation toAttestation(final PooledAttestation pooledAttestation) {
    return attestationSchema.create(
        pooledAttestation.bits().getAggregationBits(),
        attestationData,
        pooledAttestation.aggregatedSignature(),
        pooledAttestation.bits()::getCommitteeBits);
  }

  private PooledAttestationWithData toPooledAttestationWithData(
      final PooledAttestation pooledAttestation) {
    return toPooledAttestationWithData(toAttestation(pooledAttestation));
  }

  private PooledAttestationWithData toPooledAttestationWithData(final Attestation attestation) {
    return new PooledAttestationWithData(
        attestationData,
        PooledAttestation.fromValidatableAttestation(
            ValidatableAttestation.from(spec, attestation, committeeSizes)));
  }
}
