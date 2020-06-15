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

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.attestation.AggregatorUtil.aggregateAttestations;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_RETENTION_EPOCHS;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.operationvalidators.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.AttestationDataStateTransitionValidator.AttestationInvalidReason;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.util.config.Constants;

class AggregatingAttestationPoolTest {

  public static final UnsignedLong SLOT = UnsignedLong.valueOf(1234);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final AttestationDataStateTransitionValidator attestationDataValidator =
      mock(AttestationDataStateTransitionValidator.class);

  private final AggregatingAttestationPool aggregatingPool =
      new AggregatingAttestationPool(attestationDataValidator);

  @AfterEach
  public void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  public void createAggregateFor_shouldReturnEmptyWhenNoAttestationsMatchGivenData() {
    final Optional<ValidateableAttestation> result =
        aggregatingPool.createAggregateFor(dataStructureUtil.randomAttestationData());
    assertThat(result).isEmpty();
  }

  @Test
  public void createAggregateFor_shouldAggregateAttestationsWithMatchingData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 3, 5);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 4, 6);

    final Optional<ValidateableAttestation> result =
        aggregatingPool.createAggregateFor(attestationData);
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
        aggregatingPool.createAggregateFor(attestationData);
    assertThat(result.map(ValidateableAttestation::getAttestation))
        .contains(aggregateAttestations(attestation1, attestation2));
  }

  @Test
  public void getAttestationsForBlock_shouldReturnEmptyListWhenNoAttestationsAvailable() {
    when(attestationDataValidator.validate(any(), any())).thenReturn(Optional.empty());
    assertThat(aggregatingPool.getAttestationsForBlock(dataStructureUtil.randomBeaconState()))
        .isEmpty();
  }

  @Test
  public void getAttestationsForBlock_shouldNotIncludeAttestationsWhereDataDoesNotValidate() {
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 1);
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 2);
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 3);

    when(attestationDataValidator.validate(any(), any()))
        .thenReturn(Optional.of(AttestationInvalidReason.SLOT_NOT_IN_EPOCH));

    assertThat(aggregatingPool.getAttestationsForBlock(dataStructureUtil.randomBeaconState()))
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

    assertThat(aggregatingPool.getAttestationsForBlock(state))
        .containsExactlyInAnyOrder(attestation2, attestation3);
  }

  @Test
  public void getAttestationsForBlock_shouldAggregateAttestationsWhenPossible() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);

    assertThat(aggregatingPool.getAttestationsForBlock(dataStructureUtil.randomBeaconState()))
        .containsExactly(aggregateAttestations(attestation1, attestation2));
  }

  @Test
  public void getAttestationsForBlock_shouldIncludeAttestationsWithDifferentData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);
    final Attestation attestation3 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 3, 4);

    assertThat(aggregatingPool.getAttestationsForBlock(dataStructureUtil.randomBeaconState()))
        .containsExactlyInAnyOrder(aggregateAttestations(attestation1, attestation2), attestation3);
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

    assertThat(aggregatingPool.getAttestationsForBlock(state))
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

    aggregatingPool.onSlot(
        pruneAttestationData
            .getSlot()
            .plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH * ATTESTATION_RETENTION_EPOCHS))
            .plus(ONE));

    assertThat(aggregatingPool.getAttestationsForBlock(dataStructureUtil.randomBeaconState()))
        .containsOnly(preserveAttestation);
  }

  private Attestation addAttestationFromValidators(
      final AttestationData data, final int... validators) {
    final Bitlist bitlist = new Bitlist(20, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    IntStream.of(validators).forEach(bitlist::setBit);
    final Attestation attestation =
        new Attestation(bitlist, data, dataStructureUtil.randomSignature());
    aggregatingPool.add(ValidateableAttestation.fromAttestation(attestation));
    return attestation;
  }
}
