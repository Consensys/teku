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

package tech.pegasys.artemis.statetransition.attestation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.statetransition.attestation.AggregatorUtil.aggregateAttestations;
import static tech.pegasys.artemis.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import com.google.common.primitives.UnsignedLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;

class MatchingDataAttestationGroupTest {
  private static final UnsignedLong SLOT = UnsignedLong.valueOf(1234);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final AttestationData attestationData = dataStructureUtil.randomAttestationData(SLOT);

  private final MatchingDataAttestationGroup group =
      new MatchingDataAttestationGroup(attestationData);

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
    final Attestation attestation = addAttestation(1);
    group.remove(attestation);

    assertThat(group.isEmpty()).isTrue();
  }

  @Test
  public void remove_shouldRemoveAttestationEvenWhenInstanceIsDifferent() {
    final Attestation attestation = addAttestation(1);
    final Attestation copy =
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(attestation), Attestation.class);
    group.remove(copy);

    assertThat(group.stream()).isEmpty();
    assertThat(group.isEmpty()).isTrue();
  }

  @Test
  public void iterator_shouldAggregateAttestationsWhereValidatorsDoNotOverlap() {
    final Attestation attestation1 = addAttestation(1);
    final Attestation attestation2 = addAttestation(2);

    final Attestation expected = aggregateAttestations(attestation1, attestation2);
    assertThat(group).containsExactlyInAnyOrder(expected);
  }

  @Test
  public void iterator_shouldAggregateAttestationsWithMoreValidatorsFirst() {
    final Attestation bigAttestation = addAttestation(1, 3, 5, 7);
    final Attestation mediumAttestation = addAttestation(3, 5, 9);
    final Attestation littleAttestation = addAttestation(2);

    assertThat(group)
        .containsExactly(
            aggregateAttestations(bigAttestation, littleAttestation), mediumAttestation);
  }

  @Test
  public void iterator_shouldNotAggregateAttestaionsWhenValidatorsOverlap() {
    final Attestation attestation1 = addAttestation(1, 2, 5);
    final Attestation attestation2 = addAttestation(1, 2, 3);

    assertThat(group).containsExactlyInAnyOrder(attestation1, attestation2);
  }

  @Test
  public void iterator_shouldOmitAttestationsThatAreAlreadyIncludedInTheAggregate() {
    final Attestation aggregate = addAttestation(1, 2, 3);
    addAttestation(2);

    assertThat(group).containsExactly(aggregate);
  }

  private Attestation addAttestation(final int... validators) {
    final Attestation attestation = createAttestation(validators);
    group.add(attestation);
    return attestation;
  }

  private Attestation createAttestation(final int... validators) {
    final Bitlist aggregationBits = new Bitlist(10, MAX_VALIDATORS_PER_COMMITTEE);
    IntStream.of(validators).forEach(aggregationBits::setBit);
    return new Attestation(aggregationBits, attestationData, dataStructureUtil.randomSignature());
  }
}
