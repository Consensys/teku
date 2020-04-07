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

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.statetransition.attestation.AggregatorUtil.aggregateAttestations;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.ssz.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.config.Constants;

class AggregatingAttestationPoolTest {

  public static final UnsignedLong SLOT = UnsignedLong.valueOf(1234);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final AggregatingAttestationPool aggregatingPool = new AggregatingAttestationPool();

  @Test
  public void createAggregateFor_shouldReturnEmptyWhenNoAttestationsMatchGivenData() {
    final Optional<Attestation> result =
        aggregatingPool.createAggregateFor(dataStructureUtil.randomAttestationData());
    assertThat(result).isEmpty();
  }

  @Test
  public void createAggregateFor_shouldAggregateAttestationsWithMatchingData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 3, 5);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 4, 6);

    final Optional<Attestation> result = aggregatingPool.createAggregateFor(attestationData);
    assertThat(result).contains(aggregateAttestations(attestation1, attestation2));
  }

  @Test
  public void createAggregateFor_shouldReturnBestAggregateForMatchingDataWhenSomeOverlap() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 3, 5, 7);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 4, 6, 8);
    addAttestationFromValidators(attestationData, 2, 3, 9);

    final Optional<Attestation> result = aggregatingPool.createAggregateFor(attestationData);
    assertThat(result).contains(aggregateAttestations(attestation1, attestation2));
  }

  @Test
  public void getAttestationsForBlock_shouldReturnEmptyListWhenNoAttestationsAvailable() {
    assertThat(aggregatingPool.getAttestationsForBlock(SLOT)).isEmpty();
  }

  @Test
  public void getAttestationsForBlock_shouldNotIncludeAttestationsFromSameOrLaterSlotThanBlock() {
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(SLOT), 1);
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(SLOT.plus(ONE)), 2);

    assertThat(aggregatingPool.getAttestationsForBlock(SLOT)).isEmpty();
  }

  @Test
  public void getAttestationsForBlock_shouldAggregateAttestationsWhenPossible() {
    final AttestationData attestationData = randomValidationDataToIncludeInBlock();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);

    assertThat(aggregatingPool.getAttestationsForBlock(SLOT))
        .containsExactly(aggregateAttestations(attestation1, attestation2));
  }

  @Test
  public void getAttestationsForBlock_shouldIncludeAttestationsWithDifferentData() {
    final AttestationData attestationData = randomValidationDataToIncludeInBlock();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);
    final Attestation attestation3 =
        addAttestationFromValidators(randomValidationDataToIncludeInBlock(), 3, 4);

    assertThat(aggregatingPool.getAttestationsForBlock(SLOT))
        .containsExactlyInAnyOrder(aggregateAttestations(attestation1, attestation2), attestation3);
  }

  private Attestation addAttestationFromValidators(
      final AttestationData data, final int... validators) {
    final Bitlist bitlist = new Bitlist(20, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    IntStream.of(validators).forEach(bitlist::setBit);
    final Attestation attestation =
        new Attestation(bitlist, data, dataStructureUtil.randomSignature());
    aggregatingPool.add(attestation);
    return attestation;
  }

  private AttestationData randomValidationDataToIncludeInBlock() {
    final UnsignedLong attestationSlot = SLOT.minus(ONE);
    return new AttestationData(
        attestationSlot,
        dataStructureUtil.randomUnsignedLong(),
        dataStructureUtil.randomBytes32(),
        new Checkpoint(
            compute_epoch_at_slot(attestationSlot).minus(ONE), dataStructureUtil.randomBytes32()),
        new Checkpoint(compute_epoch_at_slot(attestationSlot), dataStructureUtil.randomBytes32()));
  }
}
