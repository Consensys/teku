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
    addAttestation(1);
    assertThat(group.isEmpty()).isFalse();
  }

  @TestTemplate
  public void isEmpty_shouldBeEmptyAfterAttestationRemoved() {
    final Attestation attestation = addAttestation(1).toAttestation(attestationSchema);
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, attestation);

    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @TestTemplate
  public void remove_shouldRemoveAttestationEvenWhenInstanceIsDifferent() {
    final Attestation attestation = addAttestation(1).toAttestation(attestationSchema);
    final Attestation copy = attestationSchema.sszDeserialize(attestation.sszSerialize());
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, copy);

    assertThat(group.stream()).isEmpty();
    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @TestTemplate
  public void remove_multipleCallsToRemoveShouldAggregate() {

    // Create attestations that will be removed
    final PooledAttestation attestation1 = createAttestation(1);
    final PooledAttestation attestation2 = createAttestation(2);

    // Add some attestations
    final PooledAttestation attestation3 = addAttestation(3);
    addAttestation(1, 2);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO, attestation1.toAttestation(attestationSchema));
    assertThat(numRemoved).isEqualTo(0);
    numRemoved +=
        group.onAttestationIncludedInBlock(
            UInt64.ZERO, attestation2.toAttestation(attestationSchema));
    assertThat(numRemoved).isEqualTo(1);
    assertThat(group.stream(Optional.of(UInt64.ZERO))).containsExactly(attestation3);
  }

  @TestTemplate
  public void remove_shouldRemoveAttestationsThatAreAggregatedIntoRemovedAttestation() {
    final PooledAttestation attestation1 = addAttestation(1);
    final PooledAttestation attestation2 = addAttestation(2);
    final PooledAttestation attestation3 = addAttestation(3);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(
                attestation1.toAttestation(attestationSchema),
                attestation2.toAttestation(attestationSchema)));

    assertThat(group.stream(Optional.of(UInt64.ZERO))).containsExactly(attestation3);
    assertThat(numRemoved).isEqualTo(2); // the one attestation is still there, and we've removed 2.
  }

  @TestTemplate
  public void add_shouldIgnoreAttestationWhoseBitsHaveAllBeenRemoved() {
    // Create attestations that will be removed
    final PooledAttestation attestation1 = createAttestation(1);
    final PooledAttestation attestation2 = createAttestation(2);

    // Create attestation to be added / ignored
    final PooledAttestation attestationToIgnore = createAttestation(1, 2);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO, attestation1.toAttestation(attestationSchema));
    numRemoved +=
        group.onAttestationIncludedInBlock(
            UInt64.ZERO, attestation2.toAttestation(attestationSchema));
    assertThat(numRemoved).isEqualTo(0);

    assertThat(group.add(attestationToIgnore, Optional.empty())).isFalse();
    assertThat(group.stream()).isEmpty();
  }

  @TestTemplate
  public void add_shouldAggregateAttestationsFromSameCommittee(final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation attestation1 = addAttestation(Optional.of(0), 1);
    final PooledAttestation attestation2 = addAttestation(Optional.of(1), 2);
    final PooledAttestation attestation3 = addAttestation(Optional.of(1), 3);

    assertThat(group.stream(Optional.of(UInt64.ZERO))).containsExactly(attestation1);

    final Attestation expected =
        aggregateAttestations(
            attestation2.toAttestation(attestationSchema),
            attestation3.toAttestation(attestationSchema));

    assertThat(group.stream(Optional.of(UInt64.ONE)))
        .containsExactly(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expected, committeeSizes)));
  }

  @TestTemplate
  public void add_shouldIgnoreDuplicateAttestations() {
    final PooledAttestation attestation = addAttestation(1, 2);
    final PooledAttestation copy =
        PooledAttestation.fromValidatableAttestation(
            ValidatableAttestation.from(
                spec,
                attestationSchema.sszDeserialize(
                    attestation.toAttestation(attestationSchema).sszSerialize()),
                committeeSizes));

    assertThat(group.add(copy, Optional.empty())).isFalse();
    assertThat(group.stream()).containsExactly(attestation);
  }

  @TestTemplate
  public void iterator_shouldAggregateAttestationsWhereValidatorsDoNotOverlap() {
    final PooledAttestation attestation1 = addAttestation(1);
    final PooledAttestation attestation2 = addAttestation(2);

    final Attestation expected =
        aggregateAttestations(
            attestation1.toAttestation(attestationSchema),
            attestation2.toAttestation(attestationSchema));
    assertThat(group.stream(Optional.of(UInt64.ZERO)))
        .containsExactlyInAnyOrder(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expected, committeeSizes)));
  }

  @TestTemplate
  public void iterator_shouldAggregateAttestationsWithMoreValidatorsFirst() {
    final PooledAttestation bigAttestation = addAttestation(1, 3, 5, 7);
    final PooledAttestation mediumAttestation = addAttestation(3, 5, 9);
    final PooledAttestation littleAttestation = addAttestation(2, 4);

    assertThat(group)
        .containsExactly(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec,
                    aggregateAttestations(
                        bigAttestation.toAttestation(attestationSchema),
                        littleAttestation.toAttestation(attestationSchema)),
                    committeeSizes)),
            mediumAttestation);
  }

  @TestTemplate
  public void iterator_electra_shouldAggregateSkipSingleAttestationsInBlockProduction(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation bigAttestation = addAttestation(1, 3, 5, 7);
    final PooledAttestation mediumAttestation = addAttestation(3, 5, 9);
    addAttestation(2);

    assertThat(group).containsExactly(bigAttestation, mediumAttestation);
  }

  @TestTemplate
  public void iterator_shouldNotAggregateAttestationsWhenValidatorsOverlap() {
    final PooledAttestation attestation1 = addAttestation(1, 2, 5);
    final PooledAttestation attestation2 = addAttestation(1, 2, 3);

    assertThat(group).containsExactlyInAnyOrder(attestation1, attestation2);
  }

  @TestTemplate
  public void iterator_shouldOmitAttestationsThatAreAlreadyIncludedInTheAggregate() {
    final PooledAttestation aggregate = addAttestation(1, 2, 3);
    addAttestation(2);

    assertThat(group).containsExactly(aggregate);
  }

  @TestTemplate
  void iterator_shouldOmitAttestationsThatOverlapWithFirstAttestationAndAreRedundantWithCombined() {
    // First aggregate created will have validators 1,2,3,4 which makes the 2,4 attestation
    // redundant, but iteration will have already passed it before it becomes redundant
    final PooledAttestation useful1 = addAttestation(1, 2, 3);
    addAttestation(2, 4);
    final PooledAttestation useful2 = addAttestation(4);

    assertThat(group.stream(Optional.of(UInt64.ZERO)))
        .containsExactly(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec,
                    aggregateAttestations(
                        useful1.toAttestation(attestationSchema),
                        useful2.toAttestation(attestationSchema)),
                    committeeSizes)));
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldRemoveAttestationsMadeRedundant() {
    final PooledAttestation attestation1 = addAttestation(1, 2, 3, 4);
    final PooledAttestation attestation2 = addAttestation(1, 5, 7);
    final PooledAttestation attestation3 = addAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group).containsExactly(attestation1, attestation2, attestation3);

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, createAttestation(1, 2, 3, 4, 5, 6, 7).toAttestation(attestationSchema));

    assertThat(group.size()).isZero();
    assertThat(group).isEmpty();
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldNotRemoveAttestationsWithAdditionalValidators() {
    final PooledAttestation attestation1 = addAttestation(1, 2, 3, 4);
    final PooledAttestation attestation2 = addAttestation(1, 5, 7);
    final PooledAttestation attestation3 = addAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group).containsExactly(attestation1, attestation2, attestation3);

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, createAttestation(1, 2, 3, 4, 5, 6).toAttestation(attestationSchema));

    // Validator 7 is still relevant
    assertThat(group.size()).isEqualTo(1);
    assertThat(group).containsExactly(attestation2);
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldNotAddAttestationsAlreadySeenInBlocks() {
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), createAttestation(1, 2, 3, 4, 5, 6).toAttestation(attestationSchema));

    // Can't add redundant attestation
    assertThat(group.add(createAttestation(1), Optional.empty())).isFalse();
    assertThat(group.add(createAttestation(1, 2, 3, 4, 5, 6), Optional.empty())).isFalse();
    assertThat(group.add(createAttestation(2, 3), Optional.empty())).isFalse();
  }

  @TestTemplate
  void onReorg_shouldAllowReadingAttestationsThatAreNoLongerRedundant() {
    final PooledAttestation attestation = createAttestation(3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), createAttestation(1, 2, 3, 4, 5, 6).toAttestation(attestationSchema));

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
    final PooledAttestation attestation1 = createAttestation(3, 4);
    final PooledAttestation attestation2 = createAttestation(1, 2, 3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), createAttestation(2, 3, 4).toAttestation(attestationSchema));
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(3), createAttestation(1, 2, 3, 4).toAttestation(attestationSchema));

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
    final PooledAttestation attestation1 = addAttestation(1);
    assertThat(group.size()).isEqualTo(1);
    final PooledAttestation attestation2 = addAttestation(2);
    assertThat(group.size()).isEqualTo(2);
    addAttestation(3, 4);
    assertThat(group.size()).isEqualTo(3);
    addAttestation(1, 2);
    assertThat(group.size()).isEqualTo(4);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(
                attestation1.toAttestation(attestationSchema),
                attestation2.toAttestation(attestationSchema)));

    assertThat(numRemoved).isEqualTo(3);
    assertThat(group.size()).isEqualTo(1);
  }

  private PooledAttestation addAttestation(final int... validators) {
    return addAttestation(Optional.empty(), validators);
  }

  private PooledAttestation addAttestation(
      final Optional<Integer> committeeIndex, final int... validators) {
    final PooledAttestation attestation = createAttestation(committeeIndex, validators);
    final boolean added = group.add(attestation, Optional.empty());
    assertThat(added).isTrue();
    return attestation;
  }

  private PooledAttestation createAttestation(final int... validators) {
    return createAttestation(Optional.empty(), validators);
  }

  private PooledAttestation createAttestation(
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
}
