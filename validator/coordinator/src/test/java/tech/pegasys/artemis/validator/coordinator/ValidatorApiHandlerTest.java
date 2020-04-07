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

package tech.pegasys.artemis.validator.coordinator;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.artemis.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.artemis.storage.client.CombinedChainDataClient;
import tech.pegasys.artemis.ssz.SSZTypes.Bitlist;
import tech.pegasys.artemis.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.api.ValidatorDuties;

class ValidatorApiHandlerTest {

  private static final UnsignedLong EPOCH = UnsignedLong.valueOf(13);
  private static final UnsignedLong PREVIOUS_EPOCH_START_SLOT =
      BeaconStateUtil.compute_start_slot_at_epoch(EPOCH.minus(UnsignedLong.ONE));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final BlockFactory blockFactory = mock(BlockFactory.class);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final AttestationAggregator attestationAggregator = mock(AttestationAggregator.class);
  private final EventBus eventBus = mock(EventBus.class);

  private final ValidatorApiHandler validatorApiHandler =
      new ValidatorApiHandler(
          chainDataClient, blockFactory, attestationPool, attestationAggregator, eventBus);

  @Test
  public void getDuties_shouldReturnEmptyWhenStateIsUnavailable() {
    when(chainDataClient.getStateAtSlot(PREVIOUS_EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<List<ValidatorDuties>>> duties =
        validatorApiHandler.getDuties(EPOCH, List.of(dataStructureUtil.randomPublicKey()));
    assertThat(duties).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getDuties_shouldReturnDutiesForUnknownValidator() {
    when(chainDataClient.getStateAtSlot(PREVIOUS_EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.of(createStateWithActiveValidators())));

    final BLSPublicKey unknownPublicKey = dataStructureUtil.randomPublicKey();
    final SafeFuture<Optional<List<ValidatorDuties>>> result =
        validatorApiHandler.getDuties(EPOCH, List.of(unknownPublicKey));
    final Optional<List<ValidatorDuties>> duties = assertCompletedSuccessfully(result);
    assertThat(duties.get()).containsExactly(ValidatorDuties.noDuties(unknownPublicKey));
  }

  @Test
  public void getDuties_shouldReturnDutiesForKnownValidator() {
    final BeaconState state = createStateWithActiveValidators();
    when(chainDataClient.getStateAtSlot(PREVIOUS_EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.of(state)));

    final int validatorIndex = 3;
    final BLSPublicKey publicKey = state.getValidators().get(validatorIndex).getPubkey();
    final SafeFuture<Optional<List<ValidatorDuties>>> result =
        validatorApiHandler.getDuties(EPOCH, List.of(publicKey));
    final Optional<List<ValidatorDuties>> duties = assertCompletedSuccessfully(result);
    assertThat(duties.get())
        .containsExactly(
            ValidatorDuties.withDuties(
                publicKey, validatorIndex, 0, 2, 1, emptyList(), UnsignedLong.valueOf(110)));
  }

  @Test
  public void getDuties_shouldReturnDutiesForMixOfKnownAndUnknownValidators() {
    final BeaconState state = createStateWithActiveValidators();
    when(chainDataClient.getStateAtSlot(PREVIOUS_EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.of(state)));

    final BLSPublicKey unknownPublicKey = dataStructureUtil.randomPublicKey();
    final BLSPublicKey validator3Key = state.getValidators().get(3).getPubkey();
    final BLSPublicKey validator31Key = state.getValidators().get(31).getPubkey();
    final SafeFuture<Optional<List<ValidatorDuties>>> result =
        validatorApiHandler.getDuties(
            EPOCH, List.of(validator3Key, unknownPublicKey, validator31Key));
    final Optional<List<ValidatorDuties>> duties = assertCompletedSuccessfully(result);
    final ValidatorDuties validator3Duties =
        ValidatorDuties.withDuties(
            validator3Key, 3, 0, 2, 1, emptyList(), UnsignedLong.valueOf(110));
    final ValidatorDuties unknownValidatorDuties = ValidatorDuties.noDuties(unknownPublicKey);
    final ValidatorDuties validator31Duties =
        ValidatorDuties.withDuties(
            validator31Key,
            31,
            0,
            0,
            1,
            List.of(UnsignedLong.valueOf(107), UnsignedLong.valueOf(111)),
            UnsignedLong.valueOf(104));
    assertThat(duties.get())
        .containsExactly(validator3Duties, unknownValidatorDuties, validator31Duties);
  }

  @Test
  public void getDuties_shouldUseGenesisStateForFirstEpoch() {
    when(chainDataClient.getStateAtSlot(any())).thenReturn(new SafeFuture<>());
    validatorApiHandler
        .getDuties(UnsignedLong.ZERO, List.of(dataStructureUtil.randomPublicKey()))
        .reportExceptions();

    verify(chainDataClient).getStateAtSlot(UnsignedLong.ZERO);
  }

  @Test
  public void createUnsignedBlock_shouldReturnEmptyWhenBestBlockNotSet() {
    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.empty());

    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(
            UnsignedLong.ONE, dataStructureUtil.randomSignature());

    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void createUnsignedBlock_shouldCreateBlock() throws Exception {
    final UnsignedLong newSlot = UnsignedLong.valueOf(25);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final BeaconState previousState = dataStructureUtil.randomBeaconState();
    final BeaconBlock previousBlock =
        dataStructureUtil.randomBeaconBlock(previousState.getSlot().longValue());
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BeaconBlock createdBlock = dataStructureUtil.randomBeaconBlock(newSlot.longValue());

    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(chainDataClient.getBestSlot()).thenReturn(UnsignedLong.valueOf(24));
    when(chainDataClient.getBlockAndStateInEffectAtSlot(newSlot.minus(UnsignedLong.ONE), blockRoot))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new BeaconBlockAndState(previousBlock, previousState))));
    when(blockFactory.createUnsignedBlock(previousState, previousBlock, newSlot, randaoReveal))
        .thenReturn(createdBlock);

    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(newSlot, randaoReveal);

    assertThat(result).isCompletedWithValue(Optional.of(createdBlock));
  }

  @Test
  public void createUnsignedAttestation_shouldReturnEmptyWhenBestBlockNotSet() {
    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.empty());

    final SafeFuture<Optional<Attestation>> result =
        validatorApiHandler.createUnsignedAttestation(UnsignedLong.ONE, 3);

    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void createUnsignedAttestation_shouldCreateAttestation() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final BeaconState state = createStateWithActiveValidators();
    final UnsignedLong slot = state.getSlot().plus(UnsignedLong.valueOf(5));
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(state.getSlot().longValue());

    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(chainDataClient.getBestSlot()).thenReturn(slot);
    when(chainDataClient.getBlockAndStateInEffectAtSlot(slot, blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(new BeaconBlockAndState(block, state))));

    final int committeeIndex = 0;
    final SafeFuture<Optional<Attestation>> result =
        validatorApiHandler.createUnsignedAttestation(slot, committeeIndex);

    assertThat(result).isCompleted();
    final Optional<Attestation> maybeAttestation = result.join();
    assertThat(maybeAttestation).isPresent();
    final Attestation attestation = maybeAttestation.orElseThrow();
    assertThat(attestation.getAggregation_bits())
        .isEqualTo(new Bitlist(4, Constants.MAX_VALIDATORS_PER_COMMITTEE));
    assertThat(attestation.getData())
        .isEqualTo(
            AttestationUtil.getGenericAttestationData(
                slot, state, block, UnsignedLong.valueOf(committeeIndex)));
    assertThat(attestation.getData().getSlot()).isEqualTo(slot);
    assertThat(attestation.getAggregate_signature().toBytes())
        .isEqualTo(BLSSignature.empty().toBytes());
  }

  @Test
  public void createAggregate_shouldReturnAggregateFromAttestationPool() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Optional<Attestation> aggregate = Optional.of(dataStructureUtil.randomAttestation());
    when(attestationPool.createAggregateFor(attestationData)).thenReturn(aggregate);

    assertThat(validatorApiHandler.createAggregate(attestationData))
        .isCompletedWithValue(aggregate);
  }

  @Test
  public void getFork_shouldReturnEmptyWhenHeadStateNotAvailable() {
    when(chainDataClient.getHeadStateFromStore()).thenReturn(Optional.empty());

    assertThat(validatorApiHandler.getFork()).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getFork_shouldReturnForkFromHeadState() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    when(chainDataClient.getHeadStateFromStore()).thenReturn(Optional.of(state));

    assertThat(validatorApiHandler.getFork()).isCompletedWithValue(Optional.of(state.getFork()));
  }

  @Test
  public void sendSignedAttestation_shouldAddAttestationToAggregatorAndEventBus() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    validatorApiHandler.sendSignedAttestation(attestation);

    verify(attestationPool).add(attestation);
    verify(attestationAggregator).addOwnValidatorAttestation(attestation);
    verify(eventBus).post(attestation);
  }

  @Test
  public void sendSignedBlock_shouldPostProposedBlockEvent() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    validatorApiHandler.sendSignedBlock(block);

    verify(eventBus).post(new ProposedBlockEvent(block));
  }

  @Test
  public void sendAggregateAndProof_shouldPostAggregateAndProof() {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    validatorApiHandler.sendAggregateAndProof(aggregateAndProof);

    verify(eventBus).post(aggregateAndProof);
  }

  private Optional<List<ValidatorDuties>> assertCompletedSuccessfully(
      final SafeFuture<Optional<List<ValidatorDuties>>> result) {
    assertThat(result).isCompleted();
    return result.join();
  }

  private BeaconState createStateWithActiveValidators() {
    return dataStructureUtil
        .randomBeaconState(32)
        .updated(
            state -> {
              state.setSlot(PREVIOUS_EPOCH_START_SLOT);
              final SSZMutableList<Validator> validators = state.getValidators();
              for (int i = 0; i < validators.size(); i++) {
                validators.update(
                    i,
                    validator ->
                        validator
                            .withActivation_eligibility_epoch(UnsignedLong.ZERO)
                            .withActivation_epoch(UnsignedLong.ZERO)
                            .withExit_epoch(Constants.FAR_FUTURE_EPOCH)
                            .withWithdrawable_epoch(Constants.FAR_FUTURE_EPOCH));
              }
            });
  }
}
