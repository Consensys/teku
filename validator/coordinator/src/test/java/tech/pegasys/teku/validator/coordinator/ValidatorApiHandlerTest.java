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

package tech.pegasys.teku.validator.coordinator;

import static com.google.common.primitives.UnsignedLong.ZERO;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.AttestationUtil;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.gossip.AttestationTopicSubscriber;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.sync.SyncState;
import tech.pegasys.teku.sync.SyncStateTracker;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.api.ValidatorDuties;

class ValidatorApiHandlerTest {

  private static final UnsignedLong EPOCH = UnsignedLong.valueOf(13);
  private static final UnsignedLong PREVIOUS_EPOCH_START_SLOT =
      BeaconStateUtil.compute_start_slot_at_epoch(EPOCH.minus(UnsignedLong.ONE));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final SyncStateTracker syncStateTracker = mock(SyncStateTracker.class);
  private final StateTransition stateTransition = mock(StateTransition.class);
  private final BlockFactory blockFactory = mock(BlockFactory.class);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final AttestationManager attestationManager = mock(AttestationManager.class);
  private final AttestationTopicSubscriber attestationTopicSubscriptions =
      mock(AttestationTopicSubscriber.class);
  private final EventBus eventBus = mock(EventBus.class);

  private final ValidatorApiHandler validatorApiHandler =
      new ValidatorApiHandler(
          chainDataClient,
          syncStateTracker,
          stateTransition,
          blockFactory,
          attestationPool,
          attestationManager,
          attestationTopicSubscriptions,
          eventBus);

  @BeforeEach
  public void setUp() {
    when(syncStateTracker.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
  }

  @Test
  public void getDuties_shouldFailWhenNodeIsSyncing() {
    when(syncStateTracker.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    final SafeFuture<Optional<List<ValidatorDuties>>> duties =
        validatorApiHandler.getDuties(EPOCH, List.of(dataStructureUtil.randomPublicKey()));
    assertThat(duties).isCompletedExceptionally();
    assertThatThrownBy(duties::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void getDuties_shouldReturnEmptyWhenStateIsUnavailable() {
    when(chainDataClient.getLatestStateAtSlot(PREVIOUS_EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<List<ValidatorDuties>>> duties =
        validatorApiHandler.getDuties(EPOCH, List.of(dataStructureUtil.randomPublicKey()));
    assertThat(duties).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getDuties_shouldReturnEmptyWhenNoPublicKeysSpecified() {
    when(chainDataClient.getLatestStateAtSlot(PREVIOUS_EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.of(createStateWithActiveValidators())));

    final SafeFuture<Optional<List<ValidatorDuties>>> result =
        validatorApiHandler.getDuties(EPOCH, emptyList());
    final Optional<List<ValidatorDuties>> duties = assertCompletedSuccessfully(result);
    assertThat(duties.get()).isEmpty();
  }

  @Test
  public void getDuties_shouldReturnDutiesForUnknownValidator() {
    when(chainDataClient.getLatestStateAtSlot(PREVIOUS_EPOCH_START_SLOT))
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
    when(chainDataClient.getLatestStateAtSlot(PREVIOUS_EPOCH_START_SLOT))
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
  public void getDuties_shouldNotIncludeBlockProductionDutyForGenesisSlot() {
    final BeaconState state = createStateWithActiveValidators(ZERO);
    when(chainDataClient.getLatestStateAtSlot(ZERO))
        .thenReturn(completedFuture(Optional.of(state)));

    final List<BLSPublicKey> allValidatorKeys =
        state.getValidators().stream().map(Validator::getPubkey).collect(Collectors.toList());
    final SafeFuture<Optional<List<ValidatorDuties>>> result =
        validatorApiHandler.getDuties(ZERO, allValidatorKeys);
    final List<ValidatorDuties> duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(
            duties.stream()
                .flatMap(duty -> duty.getDuties().stream())
                .flatMap(duty -> duty.getBlockProposalSlots().stream()))
        .doesNotContain(ZERO);
  }

  @Test
  public void getDuties_shouldReturnDutiesForMixOfKnownAndUnknownValidators() {
    final BeaconState state = createStateWithActiveValidators();
    when(chainDataClient.getLatestStateAtSlot(PREVIOUS_EPOCH_START_SLOT))
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
    when(chainDataClient.getLatestStateAtSlot(any())).thenReturn(new SafeFuture<>());
    validatorApiHandler
        .getDuties(ZERO, List.of(dataStructureUtil.randomPublicKey()))
        .reportExceptions();

    verify(chainDataClient).getLatestStateAtSlot(ZERO);
  }

  @Test
  public void createUnsignedBlock_shouldFailWhenNodeIsSyncing() {
    when(syncStateTracker.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(
            UnsignedLong.ONE, dataStructureUtil.randomSignature(), Optional.empty());

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createUnsignedBlock_shouldCreateBlock() throws Exception {
    final UnsignedLong newSlot = UnsignedLong.valueOf(25);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final BeaconState previousState = dataStructureUtil.randomBeaconState();
    final BeaconBlockAndState previousBlockAndState =
        dataStructureUtil.randomBlockAndState(previousState.getSlot(), previousState);
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BeaconBlock createdBlock = dataStructureUtil.randomBeaconBlock(newSlot.longValue());

    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(chainDataClient.getBestSlot()).thenReturn(UnsignedLong.valueOf(24));
    when(chainDataClient.getBlockAndStateInEffectAtSlot(newSlot.minus(UnsignedLong.ONE)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(previousBlockAndState)));
    when(blockFactory.createUnsignedBlock(
            previousState,
            previousBlockAndState.getBlock(),
            newSlot,
            randaoReveal,
            Optional.empty()))
        .thenReturn(createdBlock);

    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(newSlot, randaoReveal, Optional.empty());

    assertThat(result).isCompletedWithValue(Optional.of(createdBlock));
  }

  @Test
  public void createUnsignedAttestation_shouldFailWhenNodeIsSyncing() {
    when(syncStateTracker.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    final SafeFuture<Optional<Attestation>> result =
        validatorApiHandler.createUnsignedAttestation(UnsignedLong.ONE, 1);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createUnsignedAttestation_shouldCreateAttestation() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final BeaconState state = createStateWithActiveValidators();
    final UnsignedLong slot = state.getSlot().plus(UnsignedLong.valueOf(5));
    final BeaconBlockAndState blockAndState =
        dataStructureUtil.randomBlockAndState(state.getSlot(), state);

    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(chainDataClient.getBestSlot()).thenReturn(slot);
    when(chainDataClient.getBlockAndStateInEffectAtSlot(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState)));

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
                slot, state, blockAndState.getBlock(), UnsignedLong.valueOf(committeeIndex)));
    assertThat(attestation.getData().getSlot()).isEqualTo(slot);
    assertThat(attestation.getAggregate_signature().toBytes())
        .isEqualTo(BLSSignature.empty().toBytes());
  }

  @Test
  public void createAggregate_shouldFailWhenNodeIsSyncing() {
    when(syncStateTracker.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    final SafeFuture<Optional<Attestation>> result =
        validatorApiHandler.createAggregate(dataStructureUtil.randomAttestationData());

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createAggregate_shouldReturnAggregateFromAttestationPool() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Optional<Attestation> aggregate = Optional.of(dataStructureUtil.randomAttestation());
    when(attestationPool.createAggregateFor(attestationData))
        .thenReturn(aggregate.map(ValidateableAttestation::fromAttestation));

    assertThat(validatorApiHandler.createAggregate(attestationData))
        .isCompletedWithValue(aggregate);
  }

  @Test
  public void getFork_shouldReturnEmptyWhenHeadStateNotAvailable() {
    when(chainDataClient.getHeadStateFromStore()).thenReturn(Optional.empty());

    assertThat(validatorApiHandler.getForkInfo()).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getFork_shouldReturnForkFromHeadState() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    when(chainDataClient.getHeadStateFromStore()).thenReturn(Optional.of(state));

    assertThat(validatorApiHandler.getForkInfo())
        .isCompletedWithValue(Optional.of(state.getForkInfo()));
  }

  @Test
  public void subscribeToBeaconCommittee_shouldSubscribeViaAttestationTopicSubscriptions() {
    final int committeeIndex = 10;
    final UnsignedLong aggregationSlot = UnsignedLong.valueOf(13);
    validatorApiHandler.subscribeToBeaconCommitteeForAggregation(committeeIndex, aggregationSlot);

    verify(attestationTopicSubscriptions)
        .subscribeToCommitteeForAggregation(committeeIndex, aggregationSlot);
  }

  @Test
  public void sendSignedAttestation_shouldAddAttestationToAggregatorAndEventBus() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationManager.onAttestation(any())).thenReturn(SUCCESSFUL);
    validatorApiHandler.sendSignedAttestation(attestation);

    verify(attestationManager).onAttestation(ValidateableAttestation.fromAttestation(attestation));
  }

  @Test
  public void sendSignedBlock_shouldPostProposedBlockEvent() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    validatorApiHandler.sendSignedBlock(block);

    verify(eventBus).post(new ProposedBlockEvent(block));
  }

  @Test
  public void sendAggregateAndProof_shouldPostAggregateAndProof() {
    final SignedAggregateAndProof aggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();
    when(attestationManager.onAttestation(any())).thenReturn(SUCCESSFUL);
    validatorApiHandler.sendAggregateAndProof(aggregateAndProof);

    verify(attestationManager)
        .onAttestation(ValidateableAttestation.fromSignedAggregate(aggregateAndProof));
  }

  private Optional<List<ValidatorDuties>> assertCompletedSuccessfully(
      final SafeFuture<Optional<List<ValidatorDuties>>> result) {
    assertThat(result).isCompleted();
    return result.join();
  }

  private BeaconState createStateWithActiveValidators() {
    return createStateWithActiveValidators(PREVIOUS_EPOCH_START_SLOT);
  }

  private BeaconState createStateWithActiveValidators(final UnsignedLong slot) {
    return dataStructureUtil
        .randomBeaconState(32)
        .updated(
            state -> {
              state.setSlot(slot);
              final SSZMutableList<Validator> validators = state.getValidators();
              for (int i = 0; i < validators.size(); i++) {
                validators.update(
                    i,
                    validator ->
                        validator
                            .withActivation_eligibility_epoch(ZERO)
                            .withActivation_epoch(ZERO)
                            .withExit_epoch(Constants.FAR_FUTURE_EPOCH)
                            .withWithdrawable_epoch(Constants.FAR_FUTURE_EPOCH));
              }
            });
  }
}
