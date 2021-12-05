/*
 * Copyright 2020 ConsenSys AG.
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
import static tech.pegasys.teku.statetransition.attestation.AggregatorUtil.aggregateAttestations;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MatchingDataAttestationGroupTest {
  private static final UInt64 SLOT = UInt64.valueOf(1234);
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AttestationData attestationData = dataStructureUtil.randomAttestationData(SLOT);

  private final MatchingDataAttestationGroup group =
      new MatchingDataAttestationGroup(spec, attestationData);

  @Test
  public void isEmpty_shouldBeEmptyInitially() {
    assertThat(group.isEmpty()).isTrue();
  }

  @Test
  public void isEmpty_shouldNotBeEmptyWhenAnAttestationIsAdded() {
    addAttestation(1);
    assertThat(group.isEmpty()).isFalse();
  }

  @Test
  public void isEmpty_shouldBeEmptyAfterAttestationRemoved() {
    final Attestation attestation = addAttestation(1).getAttestation();
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, attestation);

    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @Test
  public void remove_shouldRemoveAttestationEvenWhenInstanceIsDifferent() {
    final Attestation attestation = addAttestation(1).getAttestation();
    final Attestation copy = Attestation.SSZ_SCHEMA.sszDeserialize(attestation.sszSerialize());
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, copy);

    assertThat(group.stream()).isEmpty();
    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @Test
  public void remove_multipleCallsToRemoveShouldAggregate() {
    // Create attestations that will be removed
    final ValidateableAttestation attestation1 = createAttestation(1);
    final ValidateableAttestation attestation2 = createAttestation(2);

    // Add some attestations
    final ValidateableAttestation attestation3 = addAttestation(3);
    addAttestation(1, 2);

    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, attestation1.getAttestation());
    assertThat(numRemoved).isEqualTo(0);
    numRemoved += group.onAttestationIncludedInBlock(UInt64.ZERO, attestation2.getAttestation());
    assertThat(numRemoved).isEqualTo(1);
    assertThat(group.stream()).containsExactly(attestation3);
  }

  @Test
  public void remove_shouldRemoveAttestationsThatAreAggregatedIntoRemovedAttestation() {
    final ValidateableAttestation attestation1 = addAttestation(1);
    final ValidateableAttestation attestation2 = addAttestation(2);
    final ValidateableAttestation attestation3 = addAttestation(3);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(attestation1.getAttestation(), attestation2.getAttestation()));

    assertThat(group.stream()).containsExactly(attestation3);
    assertThat(numRemoved).isEqualTo(2); // the one attestation is still there, and we've removed 2.
  }

  @Test
  public void add_shouldIgnoreAttestationWhoseBitsHaveAllBeenRemoved() {
    // Create attestations that will be removed
    final ValidateableAttestation attestation1 = createAttestation(1);
    final ValidateableAttestation attestation2 = createAttestation(2);

    // Create attestation to be added / ignored
    final ValidateableAttestation attestationToIgnore = createAttestation(1, 2);

    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, attestation1.getAttestation());
    numRemoved += group.onAttestationIncludedInBlock(UInt64.ZERO, attestation2.getAttestation());
    assertThat(numRemoved).isEqualTo(0);

    assertThat(group.add(attestationToIgnore)).isFalse();
    assertThat(group.stream()).isEmpty();
  }

  @Test
  public void add_shouldIgnoreDuplicateAttestations() {
    final ValidateableAttestation attestation = addAttestation(1);
    final ValidateableAttestation copy =
        ValidateableAttestation.from(
            spec,
            Attestation.SSZ_SCHEMA.sszDeserialize(attestation.getAttestation().sszSerialize()));

    assertThat(group.add(copy)).isFalse();
    assertThat(group.stream()).containsExactly(attestation);
  }

  @Test
  public void iterator_shouldAggregateAttestationsWhereValidatorsDoNotOverlap() {
    final ValidateableAttestation attestation1 = addAttestation(1);
    final ValidateableAttestation attestation2 = addAttestation(2);

    final Attestation expected =
        aggregateAttestations(attestation1.getAttestation(), attestation2.getAttestation());
    assertThat(group).containsExactlyInAnyOrder(ValidateableAttestation.from(spec, expected));
  }

  @Test
  public void iterator_shouldAggregateAttestationsWithMoreValidatorsFirst() {
    final ValidateableAttestation bigAttestation = addAttestation(1, 3, 5, 7);
    final ValidateableAttestation mediumAttestation = addAttestation(3, 5, 9);
    final ValidateableAttestation littleAttestation = addAttestation(2);

    assertThat(group)
        .containsExactly(
            ValidateableAttestation.from(
                spec,
                aggregateAttestations(
                    bigAttestation.getAttestation(), littleAttestation.getAttestation())),
            mediumAttestation);
  }

  @Test
  public void iterator_shouldNotAggregateAttestationsWhenValidatorsOverlap() {
    final ValidateableAttestation attestation1 = addAttestation(1, 2, 5);
    final ValidateableAttestation attestation2 = addAttestation(1, 2, 3);

    assertThat(group).containsExactlyInAnyOrder(attestation1, attestation2);
  }

  @Test
  public void iterator_shouldOmitAttestationsThatAreAlreadyIncludedInTheAggregate() {
    final ValidateableAttestation aggregate = addAttestation(1, 2, 3);
    addAttestation(2);

    assertThat(group).containsExactly(aggregate);
  }

  @Test
  void iterator_shouldOmitAttestationsThatOverlapWithFirstAttestationAndAreRedundantWithCombined() {
    // First aggregate created will have validators 1,2,3,4 which makes the 2,4 attestation
    // redundant, but iteration will have already passed it before it becomes redundant
    final ValidateableAttestation useful1 = addAttestation(1, 2, 3);
    addAttestation(2, 4);
    final ValidateableAttestation useful2 = addAttestation(4);

    assertThat(group)
        .containsExactly(
            ValidateableAttestation.from(
                spec, aggregateAttestations(useful1.getAttestation(), useful2.getAttestation())));
  }

  @Test
  void onAttestationIncludedInBlock_shouldRemoveAttestationsMadeRedundant() {
    final ValidateableAttestation attestation1 = addAttestation(1, 2, 3, 4);
    final ValidateableAttestation attestation2 = addAttestation(1, 5, 7);
    final ValidateableAttestation attestation3 = addAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group).containsExactly(attestation1, attestation2, attestation3);

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, createAttestation(1, 2, 3, 4, 5, 6, 7).getAttestation());

    assertThat(group.size()).isZero();
    assertThat(group).isEmpty();
  }

  @Test
  void onAttestationIncludedInBlock_shouldNotRemoveAttestationsWithAdditionalValidators() {
    final ValidateableAttestation attestation1 = addAttestation(1, 2, 3, 4);
    final ValidateableAttestation attestation2 = addAttestation(1, 5, 7);
    final ValidateableAttestation attestation3 = addAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group).containsExactly(attestation1, attestation2, attestation3);

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, createAttestation(1, 2, 3, 4, 5, 6).getAttestation());

    // Validator 7 is still relevant
    assertThat(group.size()).isEqualTo(1);
    assertThat(group).containsExactly(attestation2);
  }

  @Test
  void onAttestationIncludedInBlock_shouldNotAddAttestationsAlreadySeenInBlocks() {
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), createAttestation(1, 2, 3, 4, 5, 6).getAttestation());

    // Can't add redundant attestation
    assertThat(group.add(createAttestation(1))).isFalse();
    assertThat(group.add(createAttestation(1, 2, 3, 4, 5, 6))).isFalse();
    assertThat(group.add(createAttestation(2, 3))).isFalse();
  }

  @Test
  void onReorg_shouldAllowReaddingAttestationsThatAreNoLongerRedundant() {
    final ValidateableAttestation attestation = createAttestation(3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), createAttestation(1, 2, 3, 4, 5, 6).getAttestation());

    // Can't add redundant attestation
    assertThat(group.add(attestation)).isFalse();

    // Reorg removes seen attestation
    group.onReorg(UInt64.ZERO);

    // Can now add attestation
    assertThat(group.add(attestation)).isTrue();
    assertThat(group.size()).isEqualTo(1);
    assertThat(group).containsExactly(attestation);
  }

  @Test
  void onReorg_shouldNotAllowReaddingAttestationsThatAreStillRedundant() {
    final ValidateableAttestation attestation1 = createAttestation(3, 4);
    final ValidateableAttestation attestation2 = createAttestation(1, 2, 3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), createAttestation(2, 3, 4).getAttestation());
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(3), createAttestation(1, 2, 3, 4).getAttestation());

    // Can't add redundant attestation
    assertThat(group.add(attestation1)).isFalse();
    assertThat(group.add(attestation2)).isFalse();

    // Reorg removes only the last seen attestation
    group.onReorg(UInt64.valueOf(2));

    // Still can't add attestation1 because 3 and 4 are included attestation
    assertThat(group.add(attestation1)).isFalse();

    // But can add attestation2 because validator 1 is still relevant
    assertThat(group.add(attestation2)).isTrue();
    assertThat(group.size()).isEqualTo(1);
    assertThat(group).containsExactly(attestation2);
  }

  @Test
  public void size() {
    assertThat(group.size()).isEqualTo(0);
    final ValidateableAttestation attestation1 = addAttestation(1);
    assertThat(group.size()).isEqualTo(1);
    final ValidateableAttestation attestation2 = addAttestation(2);
    assertThat(group.size()).isEqualTo(2);
    addAttestation(3, 4);
    assertThat(group.size()).isEqualTo(3);
    addAttestation(1, 2);
    assertThat(group.size()).isEqualTo(4);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(attestation1.getAttestation(), attestation2.getAttestation()));

    assertThat(numRemoved).isEqualTo(3);
    assertThat(group.size()).isEqualTo(1);
  }

  private ValidateableAttestation addAttestation(final int... validators) {
    final ValidateableAttestation attestation = createAttestation(validators);
    final boolean added = group.add(attestation);
    assertThat(added).isTrue();
    return attestation;
  }

  private ValidateableAttestation createAttestation(final int... validators) {
    final SszBitlist aggregationBits =
        Attestation.SSZ_SCHEMA.getAggregationBitsSchema().ofBits(10, validators);
    return ValidateableAttestation.from(
        spec,
        new Attestation(aggregationBits, attestationData, dataStructureUtil.randomSignature()));
  }
}
