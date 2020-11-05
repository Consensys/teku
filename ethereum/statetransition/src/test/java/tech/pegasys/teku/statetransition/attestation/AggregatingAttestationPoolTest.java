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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.statetransition.attestation.AggregatorUtil.aggregateAttestations;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_RETENTION_EPOCHS;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.Optional;
import java.util.stream.IntStream;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import tech.pegasys.teku.core.operationvalidators.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.AttestationDataStateTransitionValidator.AttestationInvalidReason;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.util.config.Constants;

class AggregatingAttestationPoolTest {

  public static final UInt64 SLOT = UInt64.valueOf(1234);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final AttestationDataStateTransitionValidator attestationDataValidator =
      mock(AttestationDataStateTransitionValidator.class);

  private final AggregatingAttestationPool aggregatingPool =
      new AggregatingAttestationPool(attestationDataValidator, new NoOpMetricsSystem());

  private final AttestationForkChecker forkChecker = mock(AttestationForkChecker.class);

  @BeforeEach
  public void setUp() {
    when(forkChecker.areAttestationsFromCorrectFork(any())).thenReturn(true);
  }

  @AfterEach
  public void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  public void createAggregateFor_shouldReturnEmptyWhenNoAttestationsMatchGivenData() {
    final Optional<ValidateableAttestation> result =
        aggregatingPool.createAggregateFor(
            dataStructureUtil.randomAttestationData().hashTreeRoot());
    assertThat(result).isEmpty();
  }

  @Test
  public void createAggregateFor_shouldAggregateAttestationsWithMatchingData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 3, 5);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 4, 6);

    final Optional<ValidateableAttestation> result =
        aggregatingPool.createAggregateFor(attestationData.hashTreeRoot());
    assertThat(result.map(ValidateableAttestation::getAttestation))
        .contains(aggregateAttestations(attestation1, attestation2));
  }

  @Test
  public void createAggregateFor_shouldReturnBestAggregateForMatchingDataWhenSomeOverlap() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 3, 5, 7);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 4, 6, 8);
    addAttestationFromValidators(attestationData, 2, 3, 9);

    final Optional<ValidateableAttestation> result =
        aggregatingPool.createAggregateFor(attestationData.hashTreeRoot());
    assertThat(result.map(ValidateableAttestation::getAttestation))
        .contains(aggregateAttestations(attestation1, attestation2));
  }

  @Test
  public void getAttestationsForBlock_shouldReturnEmptyListWhenNoAttestationsAvailable() {
    when(attestationDataValidator.validate(any(), any())).thenReturn(Optional.empty());
    assertThat(
            aggregatingPool.getAttestationsForBlock(
                dataStructureUtil.randomBeaconState(), forkChecker))
        .isEmpty();
  }

  @Test
  public void getAttestationsForBlock_shouldNotIncludeAttestationsWhereDataDoesNotValidate() {
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 1);
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 2);
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 3);

    when(attestationDataValidator.validate(any(), any()))
        .thenReturn(Optional.of(AttestationInvalidReason.SLOT_NOT_IN_EPOCH));

    assertThat(
            aggregatingPool.getAttestationsForBlock(
                dataStructureUtil.randomBeaconState(), forkChecker))
        .isEmpty();
  }

  @Test
  public void getAttestationsForBlock_shouldIncludeAttestationsThatPassValidation() {
    final Attestation attestation1 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 1);
    final Attestation attestation2 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 2);
    final Attestation attestation3 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 3);

    final BeaconState state = dataStructureUtil.randomBeaconState();
    when(attestationDataValidator.validate(state, attestation1.getData()))
        .thenReturn(Optional.of(AttestationInvalidReason.SLOT_NOT_IN_EPOCH));
    when(attestationDataValidator.validate(state, attestation2.getData()))
        .thenReturn(Optional.empty());
    when(attestationDataValidator.validate(state, attestation3.getData()))
        .thenReturn(Optional.empty());

    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker))
        .containsExactlyInAnyOrder(attestation2, attestation3);
  }

  @Test
  public void getAttestationsForBlock_shouldAggregateAttestationsWhenPossible() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);

    assertThat(
            aggregatingPool.getAttestationsForBlock(
                dataStructureUtil.randomBeaconState(), forkChecker))
        .containsExactly(aggregateAttestations(attestation1, attestation2));
  }

  @Test
  public void getAttestationsForBlock_shouldIncludeAttestationsWithDifferentData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);
    final Attestation attestation3 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 3, 4);

    assertThat(
            aggregatingPool.getAttestationsForBlock(
                dataStructureUtil.randomBeaconState(), forkChecker))
        .containsExactlyInAnyOrder(aggregateAttestations(attestation1, attestation2), attestation3);
  }

  @Test
  void getAttestationsForBlock_shouldIncludeMoreRecentAttestationsFirst() {
    final AttestationData attestationData1 =
        dataStructureUtil.randomAttestationData(UInt64.valueOf(5));
    final AttestationData attestationData2 =
        dataStructureUtil.randomAttestationData(UInt64.valueOf(6));
    final AttestationData attestationData3 =
        dataStructureUtil.randomAttestationData(UInt64.valueOf(7));
    final Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData2, 3, 4);
    final Attestation attestation3 = addAttestationFromValidators(attestationData3, 5, 6);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState(UInt64.valueOf(10));

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactly(attestation3, attestation2, attestation1);
  }

  @Test
  public void getAttestationsForBlock_shouldNotAddMoreAttestationsThanAllowedInBlock() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    Constants.MAX_ATTESTATIONS = 2;
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 5);
    // Won't be included because of the 2 attestation limit.
    addAttestationFromValidators(attestationData, 2);

    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker))
        .containsExactly(attestation1, attestation2);
  }

  @Test
  public void onSlot_shouldPruneAttestationsMoreThanTwoEpochsBehindCurrentSlot() {
    final AttestationData pruneAttestationData = dataStructureUtil.randomAttestationData(SLOT);
    final AttestationData preserveAttestationData =
        dataStructureUtil.randomAttestationData(SLOT.plus(ONE));
    addAttestationFromValidators(pruneAttestationData, 1);
    final Attestation preserveAttestation =
        addAttestationFromValidators(preserveAttestationData, 2);

    assertThat(aggregatingPool.getSize()).isEqualTo(2);
    aggregatingPool.onSlot(
        pruneAttestationData
            .getSlot()
            .plus(SLOTS_PER_EPOCH * ATTESTATION_RETENTION_EPOCHS)
            .plus(ONE));

    assertThat(
            aggregatingPool.getAttestationsForBlock(
                dataStructureUtil.randomBeaconState(), forkChecker))
        .containsOnly(preserveAttestation);
    assertThat(aggregatingPool.getSize()).isEqualTo(1);
  }

  @Test
  public void getSize_shouldIncludeAttestationsAdded() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();

    addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    addAttestationFromValidators(attestationData, 2, 5);
    assertThat(aggregatingPool.getSize()).isEqualTo(2);
  }

  @Test
  public void getSize_shouldDecreaseWhenAttestationsRemoved() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    final Attestation attestationToRemove = addAttestationFromValidators(attestationData, 2, 5);
    aggregatingPool.remove(attestationToRemove);
    assertThat(aggregatingPool.getSize()).isEqualTo(1);
  }

  @Test
  public void getSize_shouldNotIncrementWhenAttestationAlreadyExists() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();

    final Attestation attestation = addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    aggregatingPool.add(ValidateableAttestation.from(attestation));
    assertThat(aggregatingPool.getSize()).isEqualTo(1);
  }

  @Test
  public void getSize_shouldDecrementForAllRemovedAttestations() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    addAttestationFromValidators(attestationData, 1, 2, 3);
    addAttestationFromValidators(attestationData, 4, 5);
    assertThat(aggregatingPool.getSize()).isEqualTo(2);
    final Attestation attestationToRemove =
        addAttestationFromValidators(attestationData, 1, 2, 3, 4, 5);
    aggregatingPool.remove(attestationToRemove);
    assertThat(aggregatingPool.getSize()).isEqualTo(0);
  }

  @Test
  public void getSize_shouldAddTheRightData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    addAttestationFromValidators(attestationData, 1, 2, 3, 4, 5);
    addAttestationFromValidators(attestationData, 1, 2, 3);
    addAttestationFromValidators(attestationData, 4, 5);
    addAttestationFromValidators(attestationData, 6);
    addAttestationFromValidators(attestationData, 7, 8);
    assertThat(aggregatingPool.getSize()).isEqualTo(5);
  }

  @Test
  public void getSize_shouldDecrementForAllRemovedAttestationsWhileKeepingOthers() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();

    addAttestationFromValidators(attestationData, 1, 2, 3);
    addAttestationFromValidators(attestationData, 4, 5);
    addAttestationFromValidators(attestationData, 6);
    addAttestationFromValidators(attestationData, 7, 8);

    final Attestation attestationToRemove =
        addAttestationFromValidators(attestationData, 1, 2, 3, 4, 5);
    assertThat(aggregatingPool.getSize()).isEqualTo(5);

    aggregatingPool.remove(attestationToRemove);
    assertThat(aggregatingPool.getSize()).isEqualTo(2);
  }

  @Test
  public void getAttestationsForBlock_shouldNotAddAttestationsFromWrongFork() {
    final AttestationData attestationData1 = dataStructureUtil.randomAttestationData();
    final AttestationData attestationData2 = dataStructureUtil.randomAttestationData();

    addAttestationFromValidators(attestationData1, 1, 2, 3);
    Attestation attestation2 = addAttestationFromValidators(attestationData2, 4, 5);

    when(forkChecker.areAttestationsFromCorrectFork(any())).thenReturn(false);
    when(forkChecker.areAttestationsFromCorrectFork(
            ArgumentMatchers.argThat(arg -> arg.getAttestationData().equals(attestationData2))))
        .thenReturn(true);

    final BeaconState state = dataStructureUtil.randomBeaconState();
    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker))
        .containsExactly(attestation2);
  }

  @Test
  public void getAttestations_shouldReturnAllAttestations() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    Attestation attestation = addAttestationFromValidators(attestationData, 1, 2, 3);
    assertThat(aggregatingPool.getAttestations(Optional.empty(), Optional.empty()))
        .containsExactly(attestation);
  }

  @Test
  public void getAttestations_shouldReturnAttestationsForGivenCommitteeIndexOnly() {
    final AttestationData attestationData1 = dataStructureUtil.randomAttestationData();
    final AttestationData attestationData2 =
        new AttestationData(
            attestationData1.getSlot(),
            attestationData1.getIndex().plus(1),
            attestationData1.getBeacon_block_root(),
            attestationData1.getSource(),
            attestationData1.getTarget());
    Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2, 3);
    addAttestationFromValidators(attestationData2, 4, 5, 6);
    assertThat(
            aggregatingPool.getAttestations(
                Optional.empty(), Optional.of(attestationData1.getIndex())))
        .containsExactly(attestation1);
  }

  @Test
  public void getAttestations_shouldReturnAttestationsForGivenSlotOnly() {
    final AttestationData attestationData1 = dataStructureUtil.randomAttestationData();
    final AttestationData attestationData2 =
        new AttestationData(
            attestationData1.getSlot().plus(1),
            attestationData1.getIndex(),
            attestationData1.getBeacon_block_root(),
            attestationData1.getSource(),
            attestationData1.getTarget());
    Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2, 3);
    addAttestationFromValidators(attestationData2, 4, 5, 6);
    assertThat(
            aggregatingPool.getAttestations(
                Optional.of(attestationData1.getSlot()), Optional.empty()))
        .containsExactly(attestation1);
  }

  private Attestation addAttestationFromValidators(
      final AttestationData data, final int... validators) {
    final Bitlist bitlist = new Bitlist(20, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    IntStream.of(validators).forEach(bitlist::setBit);
    final Attestation attestation =
        new Attestation(bitlist, data, dataStructureUtil.randomSignature());
    ValidateableAttestation validateableAttestation = ValidateableAttestation.from(attestation);
    validateableAttestation.saveCommitteeShufflingSeed(
        dataStructureUtil.randomBeaconState(100, 15));
    aggregatingPool.add(validateableAttestation);
    return attestation;
  }
}
