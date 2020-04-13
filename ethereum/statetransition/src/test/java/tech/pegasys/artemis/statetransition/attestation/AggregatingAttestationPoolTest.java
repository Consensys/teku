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
import static com.google.common.primitives.UnsignedLong.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.statetransition.attestation.AggregatorUtil.aggregateAttestations;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
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

  @AfterEach
  public void tearDown() {
    Constants.setConstants("minimal");
  }

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
    addAttestationFromValidators(randomAttestationDataToIncludeInBlock(SLOT), 1);
    addAttestationFromValidators(randomAttestationDataToIncludeInBlock(SLOT.plus(ONE)), 2);
    addAttestationFromValidators(randomAttestationDataToIncludeInBlock(ZERO), 3);

    assertThat(aggregatingPool.getAttestationsForBlock(SLOT)).isEmpty();
  }

  @Test
  public void getAttestationsForBlock_shouldNotIncludeAttestationsFromBeforePreviousEpoch() {
    addAttestationFromValidators(randomAttestationDataToIncludeInBlock(SLOT.minus(ONE)), 1);

    final UnsignedLong twoEpochs = UnsignedLong.valueOf(2 * Constants.SLOTS_PER_EPOCH);
    assertThat(aggregatingPool.getAttestationsForBlock(SLOT.plus(twoEpochs))).isEmpty();
  }

  @Test
  public void getAttestationsForBlock_shouldAggregateAttestationsWhenPossible() {
    final AttestationData attestationData = randomAttestationDataToIncludeInBlock();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);

    assertThat(aggregatingPool.getAttestationsForBlock(SLOT))
        .containsExactly(aggregateAttestations(attestation1, attestation2));
  }

  @Test
  public void getAttestationsForBlock_shouldIncludeAttestationsWithDifferentData() {
    final AttestationData attestationData = randomAttestationDataToIncludeInBlock();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);
    final Attestation attestation3 =
        addAttestationFromValidators(randomAttestationDataToIncludeInBlock(), 3, 4);

    assertThat(aggregatingPool.getAttestationsForBlock(SLOT))
        .containsExactlyInAnyOrder(aggregateAttestations(attestation1, attestation2), attestation3);
  }

  @Test
  public void getAttestationsForBlock_shouldNotAddMoreAttestationsThanAllowedInBlock() {
    Constants.MAX_ATTESTATIONS = 2;
    final AttestationData attestationData = randomAttestationDataToIncludeInBlock();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 5);
    // Won't be included because of the 2 attestation limit.
    addAttestationFromValidators(attestationData, 2, 6);

    assertThat(aggregatingPool.getAttestationsForBlock(SLOT))
        .containsExactly(attestation1, attestation2);
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

  private AttestationData randomAttestationDataToIncludeInBlock() {
    final UnsignedLong attestationSlot = SLOT.minus(ONE);
    return randomAttestationDataToIncludeInBlock(attestationSlot);
  }

  private AttestationData randomAttestationDataToIncludeInBlock(final UnsignedLong slot) {
    return new AttestationData(
        slot,
        dataStructureUtil.randomUnsignedLong(),
        dataStructureUtil.randomBytes32(),
        new Checkpoint(compute_epoch_at_slot(slot).minus(ONE), dataStructureUtil.randomBytes32()),
        new Checkpoint(compute_epoch_at_slot(slot), dataStructureUtil.randomBytes32()));
  }
}
