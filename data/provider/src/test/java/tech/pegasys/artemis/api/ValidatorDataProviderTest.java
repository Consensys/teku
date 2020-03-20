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

package tech.pegasys.artemis.api;

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.api.schema.Attestation;
import tech.pegasys.artemis.api.schema.BLSPubKey;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.api.schema.BeaconBlock;
import tech.pegasys.artemis.api.schema.BeaconState;
import tech.pegasys.artemis.api.schema.ValidatorDuties;
import tech.pegasys.artemis.api.schema.ValidatorDutiesRequest;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.MutableValidator;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class ValidatorDataProviderTest {

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<tech.pegasys.artemis.datastructures.operations.Attestation> args =
      ArgumentCaptor.forClass(tech.pegasys.artemis.datastructures.operations.Attestation.class);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ValidatorCoordinator validatorCoordinator = mock(ValidatorCoordinator.class);
  private CombinedChainDataClient combinedChainDataClient = mock(CombinedChainDataClient.class);
  private ValidatorDataProvider provider =
      new ValidatorDataProvider(validatorCoordinator, combinedChainDataClient);;
  private final tech.pegasys.artemis.datastructures.blocks.BeaconBlock blockInternal =
      dataStructureUtil.randomBeaconBlock(123);
  private final BeaconBlock block = new BeaconBlock(blockInternal);
  private final tech.pegasys.artemis.util.bls.BLSSignature signatureInternal =
      tech.pegasys.artemis.util.bls.BLSSignature.random(1234);
  private final BLSSignature signature = new BLSSignature(signatureInternal);
  private final tech.pegasys.artemis.datastructures.state.BeaconState beaconStateInternal =
      dataStructureUtil.randomBeaconState();
  private final BeaconState beaconState = new BeaconState(beaconStateInternal);
  private Bytes32 blockRoot = dataStructureUtil.randomBytes32();
  private UnsignedLong slot = dataStructureUtil.randomUnsignedLong();
  private final BLSPublicKey pubKey1 = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey pubKey2 = dataStructureUtil.randomPublicKey();

  @Test
  void getUnsignedBeaconBlockAtSlot_throwsWithoutSlotDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(null, null));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowWithoutRandaoDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(ONE, null));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowDataProviderExceptionIfStateTransitionException() {
    shouldThrowDataProviderExceptionAfterGettingException(new StateTransitionException(null));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowDataProviderExceptionIfSlotProcessingException() {
    shouldThrowDataProviderExceptionAfterGettingException(new SlotProcessingException("TEST"));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowDataProviderExceptionIfEpochProcessingException() {
    shouldThrowDataProviderExceptionAfterGettingException(new EpochProcessingException("TEST"));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldCreateAnUnsignedBlock()
      throws SlotProcessingException, EpochProcessingException, StateTransitionException {
    when(validatorCoordinator.createUnsignedBlock(ONE, signatureInternal))
        .thenReturn(Optional.of(blockInternal));

    Optional<BeaconBlock> data = provider.getUnsignedBeaconBlockAtSlot(ONE, signature);
    verify(validatorCoordinator).createUnsignedBlock(ONE, signatureInternal);
    assertThat(data.isPresent()).isTrue();
    assertThat(data.get()).usingRecursiveComparison().isEqualTo(block);
  }

  private void shouldThrowDataProviderExceptionAfterGettingException(Exception ex) {
    tech.pegasys.artemis.util.bls.BLSSignature signatureInternal =
        tech.pegasys.artemis.util.bls.BLSSignature.random(1234);
    BLSSignature signature = new BLSSignature(signatureInternal);
    try {
      when(validatorCoordinator.createUnsignedBlock(ONE, signatureInternal)).thenThrow(ex);
    } catch (Exception ignored) {
    }

    assertThatExceptionOfType(DataProviderException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(ONE, signature));
  }

  @Test
  void getValidatorIndex_shouldReturnNotFoundIfNotFound() {
    BLSPubKey pubKey = new BLSPubKey(dataStructureUtil.randomPublicKey().toBytes());
    Integer validatorIndex = ValidatorDataProvider.getValidatorIndex(List.of(), pubKey);
    assertThat(validatorIndex).isEqualTo(null);
  }

  @Test
  void getValidatorIndex_shouldReturnIndexIfFound() {
    tech.pegasys.artemis.datastructures.state.BeaconState beaconStateInternal =
        dataStructureUtil.randomBeaconState();
    BeaconState state = new BeaconState(beaconStateInternal);
    // all the validators are the same so the first one will match
    int expectedValidatorIndex = 0;
    BLSPubKey pubKey = state.validators.get(expectedValidatorIndex).pubkey;
    int actualValidatorIndex =
        ValidatorDataProvider.getValidatorIndex(
            beaconStateInternal.getValidators().asList(), pubKey);
    assertThat(actualValidatorIndex).isEqualTo(expectedValidatorIndex);
  }

  @Test
  void getCommitteeIndex_shouldReturnNotFoundIfNotFound() {
    ValidatorDataProvider provider =
        new ValidatorDataProvider(validatorCoordinator, combinedChainDataClient);
    Integer committeeIndex = provider.getCommitteeIndex(List.of(), 99);
    assertThat(committeeIndex).isEqualTo(null);
  }

  @Test
  void getCommitteeIndex_shouldReturnIndexIfFound() {
    ValidatorDataProvider provider =
        new ValidatorDataProvider(validatorCoordinator, combinedChainDataClient);
    UnsignedLong committeeIndex = dataStructureUtil.randomUnsignedLong();
    CommitteeAssignment committeeAssignment1 =
        new CommitteeAssignment(List.of(4, 5, 6), committeeIndex, slot);
    CommitteeAssignment committeeAssignment2 =
        new CommitteeAssignment(List.of(3, 2, 1), committeeIndex, slot);
    int validatorCommitteeIndex =
        provider.getCommitteeIndex(List.of(committeeAssignment1, committeeAssignment2), 1);
    assertThat(validatorCommitteeIndex).isEqualTo(1);
  }

  @Test
  void getValidatorDutiesFromState() {
    tech.pegasys.artemis.datastructures.state.BeaconState beaconStateInternal =
        dataStructureUtil.randomBeaconState();
    ValidatorDataProvider provider =
        new ValidatorDataProvider(validatorCoordinator, combinedChainDataClient);
    List<CommitteeAssignment> committees = List.of();
    when(combinedChainDataClient.getCommitteesFromState(any(), any())).thenReturn(committees);
    when(validatorCoordinator.getProposerForSlot(any(), any())).thenReturn(pubKey1);

    List<ValidatorDuties> dutiesList =
        provider.getValidatorDutiesFromState(
            beaconStateInternal,
            List.of(pubKey1, pubKey2).stream()
                .map(k -> new BLSPubKey(k.toBytes()))
                .collect(Collectors.toList()));
    // TODO can do better than this
    assertThat(dutiesList.size()).isEqualTo(2);
    verify(combinedChainDataClient).getCommitteesFromState(any(), any());
    verify(validatorCoordinator, times(8)).getProposerForSlot(any(), any());
  }

  @Test
  void getValidatorsDutiesByRequest_shouldIncludeMissingValidators()
      throws ExecutionException, InterruptedException {
    ValidatorDataProvider provider =
        new ValidatorDataProvider(validatorCoordinator, combinedChainDataClient);
    ValidatorDutiesRequest smallRequest =
        new ValidatorDutiesRequest(
            compute_epoch_at_slot(beaconState.slot), List.of(BLSPubKey.empty()));
    when(validatorCoordinator.getProposerForSlot(any(), any())).thenReturn(pubKey1);
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(combinedChainDataClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(combinedChainDataClient.getStateAtSlot(any(), any()))
        .thenReturn(completedFuture(Optional.of(beaconStateInternal)));
    when(combinedChainDataClient.getCommitteesFromState(any(), eq(beaconState.slot)))
        .thenReturn(List.of());

    SafeFuture<List<ValidatorDuties>> future = provider.getValidatorDutiesByRequest(smallRequest);
    List<ValidatorDuties> validatorDuties = future.get();

    assertThat(validatorDuties.size()).isEqualTo(1);
    ValidatorDuties expected = new ValidatorDuties(BLSPubKey.empty(), null, null, List.of());
    assertThat(validatorDuties.get(0)).isEqualToComparingFieldByField(expected);
  }

  @Test
  void getValidatorDutiesByRequest_shouldIncludeValidatorDuties()
      throws ExecutionException, InterruptedException {
    // add a validator with a different pubkey since by default they are all the same
    tech.pegasys.artemis.datastructures.state.BeaconState alteredInternalState =
        addValidator(beaconStateInternal);

    BeaconState alteredState = new BeaconState(alteredInternalState);
    int addedValidatorIndex = alteredState.validators.size() - 1;
    when(validatorCoordinator.getProposerForSlot(any(), any()))
        .thenReturn(alteredInternalState.getValidators().get(addedValidatorIndex).getPubkey());

    ValidatorDataProvider provider =
        new ValidatorDataProvider(validatorCoordinator, combinedChainDataClient);
    ValidatorDutiesRequest validatorDutiesByRequest =
        new ValidatorDutiesRequest(
            compute_epoch_at_slot(beaconState.slot),
            List.of(
                alteredState.validators.get(0).pubkey,
                alteredState.validators.get(11).pubkey,
                alteredState.validators.get(addedValidatorIndex).pubkey));
    CommitteeAssignment ca1 = new CommitteeAssignment(List.of(3, 2, 1, 0), ZERO, alteredState.slot);
    CommitteeAssignment ca2 =
        new CommitteeAssignment(List.of(11, 22, 33, addedValidatorIndex), ZERO, alteredState.slot);
    List<CommitteeAssignment> committeeAssignments = List.of(ca1, ca2);
    List<UnsignedLong> allEpochSlots = getEpochSlotsFromStartSlot(beaconState.slot);

    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(combinedChainDataClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(combinedChainDataClient.getStateAtSlot(any(), any()))
        .thenReturn(completedFuture(Optional.of(alteredInternalState)));
    when(combinedChainDataClient.getCommitteesFromState(any(), eq(alteredInternalState.getSlot())))
        .thenReturn(committeeAssignments);

    SafeFuture<List<ValidatorDuties>> future =
        provider.getValidatorDutiesByRequest(validatorDutiesByRequest);

    List<ValidatorDuties> validatorDuties = future.get();

    assertThat(validatorDuties.size()).isEqualTo(3);
    assertThat(validatorDuties.get(0))
        .usingRecursiveComparison()
        .isEqualTo(new ValidatorDuties(alteredState.validators.get(0).pubkey, 0, 0, List.of()));
    // even though we used key 11 it will come out as 0 since the default keys are all equal
    assertThat(validatorDuties.get(1))
        .usingRecursiveComparison()
        .isEqualTo(new ValidatorDuties(alteredState.validators.get(11).pubkey, 0, 0, List.of()));
    assertThat(validatorDuties.get(2))
        .usingRecursiveComparison()
        .isEqualTo(
            new ValidatorDuties(
                alteredState.validators.get(addedValidatorIndex).pubkey,
                addedValidatorIndex,
                1,
                allEpochSlots));
  }

  private List<UnsignedLong> getEpochSlotsFromStartSlot(UnsignedLong slot) {
    List<UnsignedLong> slotsFromEpoch = new ArrayList<>();
    for (int i = 0; i < Constants.SLOTS_PER_EPOCH; i++) {
      slotsFromEpoch.add(slot.plus(UnsignedLong.valueOf(i)));
    }
    assertThat(slotsFromEpoch.size()).isEqualTo(Constants.SLOTS_PER_EPOCH);
    return slotsFromEpoch;
  }

  private tech.pegasys.artemis.datastructures.state.BeaconState addValidator(
      final tech.pegasys.artemis.datastructures.state.BeaconState beaconState) {
    MutableBeaconState beaconStateW = beaconState.createWritableCopy();
    // create a validator and add it to the list
    MutableValidator v = dataStructureUtil.randomValidator().createWritableCopy();
    v.setActivation_eligibility_epoch(ZERO);
    v.setActivation_epoch(ONE);
    beaconStateW.getValidators().add(v);
    return beaconStateW.commitChanges();
  }

  void submitAttestation_shouldSubmitAnInternalAttestationStructure() {
    tech.pegasys.artemis.datastructures.operations.Attestation internalAttestation =
        dataStructureUtil.randomAttestation();
    Attestation attestation = new Attestation(internalAttestation);

    provider.submitAttestation(attestation);

    verify(validatorCoordinator).postSignedAttestation(args.capture(), eq(true));
    assertThat(args.getValue()).usingRecursiveComparison().isEqualTo(internalAttestation);
  }
}
