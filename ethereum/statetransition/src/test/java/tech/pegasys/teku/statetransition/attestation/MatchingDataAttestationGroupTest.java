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
import static tech.pegasys.teku.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;

class MatchingDataAttestationGroupTest {
  private static final UInt64 SLOT = UInt64.valueOf(1234);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final AttestationData attestationData = dataStructureUtil.randomAttestationData(SLOT);

  private final MatchingDataAttestationGroup group =
      new MatchingDataAttestationGroup(attestationData, Bytes32.ZERO);

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
    int numRemoved = group.remove(attestation);

    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @Test
  public void remove_shouldRemoveAttestationEvenWhenInstanceIsDifferent() {
    final Attestation attestation = addAttestation(1).getAttestation();
    final Attestation copy =
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(attestation), Attestation.class);
    int numRemoved = group.remove(copy);

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

    int numRemoved = group.remove(attestation1.getAttestation());
    assertThat(numRemoved).isEqualTo(0);
    numRemoved += group.remove(attestation2.getAttestation());
    assertThat(numRemoved).isEqualTo(1);
    assertThat(group.stream()).containsExactly(attestation3);
  }

  @Test
  public void remove_shouldRemoveAttestationsThatAreAggregatedIntoRemovedAttestation() {
    final ValidateableAttestation attestation1 = addAttestation(1);
    final ValidateableAttestation attestation2 = addAttestation(2);
    final ValidateableAttestation attestation3 = addAttestation(3);

    int numRemoved =
        group.remove(
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

    int numRemoved = group.remove(attestation1.getAttestation());
    numRemoved += group.remove(attestation2.getAttestation());
    assertThat(numRemoved).isEqualTo(0);

    assertThat(group.add(attestationToIgnore)).isFalse();
    assertThat(group.stream()).isEmpty();
  }

  @Test
  public void add_shouldIgnoreDuplicateAttestations() {
    final ValidateableAttestation attestation = addAttestation(1);
    final ValidateableAttestation copy =
        ValidateableAttestation.from(
            SimpleOffsetSerializer.deserialize(
                SimpleOffsetSerializer.serialize(attestation.getAttestation()), Attestation.class));

    assertThat(group.add(copy)).isFalse();
    assertThat(group.stream()).containsExactly(attestation);
  }

  @Test
  public void iterator_shouldAggregateAttestationsWhereValidatorsDoNotOverlap() {
    final ValidateableAttestation attestation1 = addAttestation(1);
    final ValidateableAttestation attestation2 = addAttestation(2);

    final Attestation expected =
        aggregateAttestations(attestation1.getAttestation(), attestation2.getAttestation());
    assertThat(group).containsExactlyInAnyOrder(ValidateableAttestation.from(expected));
  }

  @Test
  public void iterator_shouldAggregateAttestationsWithMoreValidatorsFirst() {
    final ValidateableAttestation bigAttestation = addAttestation(1, 3, 5, 7);
    final ValidateableAttestation mediumAttestation = addAttestation(3, 5, 9);
    final ValidateableAttestation littleAttestation = addAttestation(2);

    assertThat(group)
        .containsExactly(
            ValidateableAttestation.from(
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
        group.remove(
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
    final Bitlist aggregationBits = new Bitlist(10, MAX_VALIDATORS_PER_COMMITTEE);
    IntStream.of(validators).forEach(aggregationBits::setBit);
    return ValidateableAttestation.from(
        new Attestation(aggregationBits, attestationData, dataStructureUtil.randomSignature()));
  }
}
