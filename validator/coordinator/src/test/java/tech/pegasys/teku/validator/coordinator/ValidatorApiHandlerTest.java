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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.core.results.BlockImportResult.FailureReason.DOES_NOT_DESCEND_FROM_LATEST_FINALIZED;
import static tech.pegasys.teku.datastructures.util.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.getCurrentDutyDependentRoot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.getPreviousDutyDependentRoot;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.AttestationUtil;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.sync.events.SyncState;
import tech.pegasys.teku.sync.events.SyncStateProvider;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker;

class ValidatorApiHandlerTest {

  private static final UInt64 EPOCH = UInt64.valueOf(13);
  private static final UInt64 PREVIOUS_EPOCH = EPOCH.minus(ONE);
  private static final UInt64 EPOCH_START_SLOT = compute_start_slot_at_epoch(EPOCH);
  private static final UInt64 PREVIOUS_EPOCH_START_SLOT =
      compute_start_slot_at_epoch(PREVIOUS_EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final SyncStateProvider syncStateProvider = mock(SyncStateProvider.class);
  private final BlockFactory blockFactory = mock(BlockFactory.class);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final AttestationManager attestationManager = mock(AttestationManager.class);
  private final AttestationTopicSubscriber attestationTopicSubscriptions =
      mock(AttestationTopicSubscriber.class);
  private final ActiveValidatorTracker activeValidatorTracker = mock(ActiveValidatorTracker.class);
  private final BlockImportChannel blockImportChannel = mock(BlockImportChannel.class);
  private final EventBus eventBus = mock(EventBus.class);
  private final DefaultPerformanceTracker performanceTracker =
      mock(DefaultPerformanceTracker.class);
  private final ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);

  private final ValidatorApiHandler validatorApiHandler =
      new ValidatorApiHandler(
          chainDataProvider,
          chainDataClient,
          syncStateProvider,
          blockFactory,
          blockImportChannel,
          attestationPool,
          attestationManager,
          attestationTopicSubscriptions,
          activeValidatorTracker,
          eventBus,
          mock(DutyMetrics.class),
          performanceTracker);

  @BeforeEach
  public void setUp() {
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
  }

  @Test
  public void isSyncActive_syncIsActiveAndHeadIsBehind() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH.minus(2));
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  @Test
  public void isSyncActive_syncIsActiveAndHeadALittleBehind() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH.minus(1));
    assertThat(validatorApiHandler.isSyncActive()).isFalse();
  }

  @Test
  public void isSyncActive_syncIsActiveAndHeadIsCaughtUp() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH);
    assertThat(validatorApiHandler.isSyncActive()).isFalse();
  }

  @Test
  public void isSyncActive_inSyncAndHeadIsRecent() {
    setupSyncingState(SyncState.IN_SYNC, EPOCH, EPOCH);
    assertThat(validatorApiHandler.isSyncActive()).isFalse();
  }

  @Test
  public void isSyncActive_inSyncAndHeadIsOld() {
    setupSyncingState(SyncState.IN_SYNC, EPOCH, EPOCH.minus(5));
    assertThat(validatorApiHandler.isSyncActive()).isFalse();
  }

  @Test
  public void isSyncActive_startingUpAndHeadIsBehind() {
    setupSyncingState(SyncState.START_UP, EPOCH, EPOCH.minus(2));
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  @Test
  public void isSyncActive_startingUpAndHeadALittleBehind() {
    setupSyncingState(SyncState.START_UP, EPOCH, EPOCH.minus(1));
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  @Test
  public void isSyncActive_startingUpAndHeadIsCaughtUp() {
    setupSyncingState(SyncState.START_UP, EPOCH, EPOCH);
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  private void nodeIsSyncing() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH.minus(2));
  }

  private void setupSyncingState(
      final SyncState syncState, final UInt64 currentEpoch, final UInt64 headEpoch) {
    when(syncStateProvider.getCurrentSyncState()).thenReturn(syncState);
    when(chainDataClient.getCurrentEpoch()).thenReturn(currentEpoch);
    when(chainDataClient.getHeadEpoch()).thenReturn(headEpoch);
  }

  @Test
  public void getAttestationDuties_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<AttesterDuties>> duties =
        validatorApiHandler.getAttestationDuties(EPOCH, List.of(1));
    assertThat(duties).isCompletedExceptionally();
    assertThatThrownBy(duties::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void getAttestationDuties_shouldReturnNoDutiesWhenNoIndexesSpecified() {
    final BeaconState state = createStateWithActiveValidators();
    when(chainDataClient.getStateAtSlotExact(PREVIOUS_EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(ONE));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, emptyList());
    final AttesterDuties duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(duties.getDuties()).isEmpty();
    assertThat(duties.getDependentRoot()).isEqualTo(getCurrentDutyDependentRoot(state));
  }

  @Test
  public void getAttestationDuties_shouldUsePreviousDutyDependentRootWhenStateFromSameEpoch() {
    final BeaconState state = createStateWithActiveValidators(EPOCH_START_SLOT);
    when(chainDataClient.getStateAtSlotExact(any()))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(ONE));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, emptyList());
    final AttesterDuties duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(duties.getDependentRoot()).isEqualTo(getPreviousDutyDependentRoot(state));
  }

  @Test
  public void getAttestationDuties_shouldFailForEpochTooFarAhead() {
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(3));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, List.of(1));
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getAttestationDuties_shouldReturnDutiesAndSkipMissingValidators() {
    final BeaconState state = createStateWithActiveValidators();
    final BLSPublicKey validator1Key =
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(1).getPubkey());
    when(chainDataClient.getStateAtSlotExact(PREVIOUS_EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(ONE));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, List.of(1, 32));
    final Optional<AttesterDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().getDuties())
        .containsExactly(new AttesterDuty(validator1Key, 1, 4, 0, 1, 1, UInt64.valueOf(108)));
  }

  @Test
  public void getAttestationDuties_shouldAllowOneEpochTolerance() {
    final BeaconState state = createStateWithActiveValidators();
    final BLSPublicKey validator1Key =
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(1).getPubkey());
    when(chainDataClient.getStateAtSlotExact(PREVIOUS_EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(2));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, List.of(1, 32));
    final Optional<AttesterDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().getDuties())
        .containsExactly(new AttesterDuty(validator1Key, 1, 4, 0, 1, 1, UInt64.valueOf(108)));
  }

  @Test
  public void getProposerDuties_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<ProposerDuties>> duties =
        validatorApiHandler.getProposerDuties(EPOCH);
    assertThat(duties).isCompletedExceptionally();
    assertThatThrownBy(duties::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void getProposerDuties_shouldFailForEpochTooFarAhead() {
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(2));

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getProposerDuties_shouldReturnDutiesForCurrentEpoch() {
    final BeaconState state = createStateWithActiveValidators(EPOCH_START_SLOT);
    when(chainDataClient.getStateAtSlotExact(EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH);

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH);
    final ProposerDuties duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(duties.getDuties().size()).isEqualTo(Constants.SLOTS_PER_EPOCH);
    assertThat(duties.getDependentRoot()).isEqualTo(getCurrentDutyDependentRoot(state));
  }

  @Test
  public void getProposerDuties_shouldAllowOneEpochTolerance() {
    final BeaconState state = createStateWithActiveValidators(EPOCH_START_SLOT);
    when(chainDataClient.getStateAtSlotExact(EPOCH_START_SLOT))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(1));

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH);
    final Optional<ProposerDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().getDuties().size()).isEqualTo(Constants.SLOTS_PER_EPOCH);
  }

  @Test
  public void createUnsignedBlock_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(
            ONE, dataStructureUtil.randomSignature(), Optional.empty());

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createUnsignedBlock_shouldCreateBlock() throws Exception {
    final UInt64 newSlot = UInt64.valueOf(25);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final BeaconState previousState = dataStructureUtil.randomBeaconState(newSlot.minus(1));
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(newSlot);
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BeaconBlock createdBlock = dataStructureUtil.randomBeaconBlock(newSlot.longValue());

    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(chainDataClient.getHeadSlot()).thenReturn(UInt64.valueOf(24));
    when(chainDataClient.getStateAtSlotExact(newSlot.minus(ONE)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(previousState)));
    when(chainDataClient.getStateAtSlotExact(newSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockSlotState)));
    when(blockFactory.createUnsignedBlock(
            previousState, Optional.of(blockSlotState), newSlot, randaoReveal, Optional.empty()))
        .thenReturn(createdBlock);

    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(newSlot, randaoReveal, Optional.empty());

    verify(blockFactory)
        .createUnsignedBlock(
            previousState, Optional.of(blockSlotState), newSlot, randaoReveal, Optional.empty());
    assertThat(result).isCompletedWithValue(Optional.of(createdBlock));
  }

  @Test
  public void createUnsignedAttestation_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<Attestation>> result =
        validatorApiHandler.createUnsignedAttestation(ONE, 1);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createUnsignedAttestation_shouldCreateAttestation() {
    final UInt64 slot = compute_start_slot_at_epoch(EPOCH).plus(ONE);

    final BeaconState state = createStateWithActiveValidators(EPOCH_START_SLOT);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(state.getSlot(), state);
    final SignedBlockAndState blockAndState = new SignedBlockAndState(block, state);

    final SafeFuture<Optional<SignedBlockAndState>> blockAndStateResult =
        completedFuture(Optional.of(blockAndState));
    when(chainDataClient.getSignedBlockAndStateInEffectAtSlot(slot))
        .thenReturn(blockAndStateResult);

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
                slot, state, block.getMessage(), UInt64.valueOf(committeeIndex)));
    assertThat(attestation.getData().getSlot()).isEqualTo(slot);
    assertThat(attestation.getAggregate_signature().toSSZBytes())
        .isEqualTo(BLSSignature.empty().toSSZBytes());
  }

  @Test
  public void createUnsignedAttestation_shouldUseCorrectSourceWhenEpochTransitionRequired() {
    final UInt64 slot = compute_start_slot_at_epoch(EPOCH);
    // Slot is from before the current epoch, so we need to ensure we process the epoch transition
    final UInt64 blockSlot = slot.minus(1);

    final BeaconState wrongState = createStateWithActiveValidators(blockSlot);
    final BeaconState rightState = createStateWithActiveValidators(slot);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(wrongState.getSlot(), wrongState);
    final SignedBlockAndState blockAndState = new SignedBlockAndState(block, wrongState);

    final SafeFuture<Optional<SignedBlockAndState>> blockAndStateResult =
        completedFuture(Optional.of(blockAndState));
    when(chainDataClient.getSignedBlockAndStateInEffectAtSlot(slot))
        .thenReturn(blockAndStateResult);

    when(chainDataClient.getCheckpointState(EPOCH, blockAndState))
        .thenReturn(
            SafeFuture.completedFuture(
                CheckpointState.create(new Checkpoint(EPOCH, block.getRoot()), block, rightState)));

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
                slot, rightState, block.getMessage(), UInt64.valueOf(committeeIndex)));
    assertThat(attestation.getData().getSlot()).isEqualTo(slot);
    assertThat(attestation.getAggregate_signature().toSSZBytes())
        .isEqualTo(BLSSignature.empty().toSSZBytes());
  }

  @Test
  public void createAggregate_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<Attestation>> result =
        validatorApiHandler.createAggregate(
            ONE, dataStructureUtil.randomAttestationData().hashTreeRoot());

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createAggregate_shouldReturnAggregateFromAttestationPool() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Optional<Attestation> aggregate = Optional.of(dataStructureUtil.randomAttestation());
    when(attestationPool.createAggregateFor(eq(attestationData.hashTreeRoot())))
        .thenReturn(aggregate.map(ValidateableAttestation::from));

    assertThat(
            validatorApiHandler.createAggregate(
                aggregate.get().getData().getSlot(), attestationData.hashTreeRoot()))
        .isCompletedWithValue(aggregate);
  }

  @Test
  public void getFork_shouldReturnEmptyWhenHeadStateNotAvailable() {
    when(chainDataClient.getBestState()).thenReturn(Optional.empty());

    assertThat(validatorApiHandler.getFork()).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getFork_shouldReturnForkFromHeadState() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    when(chainDataClient.getBestState()).thenReturn(Optional.of(state));

    assertThat(validatorApiHandler.getFork()).isCompletedWithValue(Optional.of(state.getFork()));
  }

  @Test
  public void subscribeToBeaconCommittee_shouldSubscribeViaAttestationTopicSubscriptions() {
    final int committeeIndex = 10;
    final UInt64 aggregationSlot = UInt64.valueOf(13);
    final UInt64 committeesAtSlot = UInt64.valueOf(10);
    final int validatorIndex = 1;
    validatorApiHandler.subscribeToBeaconCommittee(
        List.of(
            new CommitteeSubscriptionRequest(
                validatorIndex, committeeIndex, committeesAtSlot, aggregationSlot, true)));

    verify(attestationTopicSubscriptions)
        .subscribeToCommitteeForAggregation(committeeIndex, committeesAtSlot, aggregationSlot);
    verify(activeValidatorTracker).onCommitteeSubscriptionRequest(validatorIndex, aggregationSlot);
  }

  @Test
  public void subscribeToBeaconCommittee_shouldUpdateActiveValidatorTrackerWhenNotAggregating() {
    final int committeeIndex = 10;
    final UInt64 aggregationSlot = UInt64.valueOf(13);
    final UInt64 committeesAtSlot = UInt64.valueOf(10);
    final int validatorIndex = 1;
    validatorApiHandler.subscribeToBeaconCommittee(
        List.of(
            new CommitteeSubscriptionRequest(
                validatorIndex, committeeIndex, committeesAtSlot, aggregationSlot, false)));

    verifyNoInteractions(attestationTopicSubscriptions);
    verify(activeValidatorTracker).onCommitteeSubscriptionRequest(validatorIndex, aggregationSlot);
  }

  @Test
  public void sendSignedAttestation_shouldAddAttestationToAggregatorAndEventBus() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationManager.onAttestation(any(ValidateableAttestation.class)))
        .thenReturn(completedFuture(SUCCESSFUL));
    validatorApiHandler.sendSignedAttestation(attestation);

    verify(attestationManager).onAttestation(ValidateableAttestation.from(attestation));
  }

  @Test
  public void sendSignedBlock_shouldConvertSuccessfulResult() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    when(blockImportChannel.importBlock(block))
        .thenReturn(SafeFuture.completedFuture(BlockImportResult.successful(block)));
    final SafeFuture<SendSignedBlockResult> result = validatorApiHandler.sendSignedBlock(block);

    verify(eventBus).post(new ProposedBlockEvent(block));
    verify(blockImportChannel).importBlock(block);
    assertThat(result).isCompletedWithValue(SendSignedBlockResult.success(block.getRoot()));
  }

  @Test
  public void sendSignedBlock_shouldConvertFailedResult() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    when(blockImportChannel.importBlock(block))
        .thenReturn(SafeFuture.completedFuture(BlockImportResult.FAILED_INVALID_ANCESTRY));
    final SafeFuture<SendSignedBlockResult> result = validatorApiHandler.sendSignedBlock(block);

    verify(eventBus).post(new ProposedBlockEvent(block));
    verify(blockImportChannel).importBlock(block);
    assertThat(result)
        .isCompletedWithValue(
            SendSignedBlockResult.notImported(DOES_NOT_DESCEND_FROM_LATEST_FINALIZED.name()));
  }

  @Test
  public void sendSignedBlock_shouldConvertKnownBlockResult() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    when(blockImportChannel.importBlock(block))
        .thenReturn(SafeFuture.completedFuture(BlockImportResult.knownBlock(block)));
    final SafeFuture<SendSignedBlockResult> result = validatorApiHandler.sendSignedBlock(block);

    verify(eventBus).post(new ProposedBlockEvent(block));
    verify(blockImportChannel).importBlock(block);
    assertThat(result).isCompletedWithValue(SendSignedBlockResult.success(block.getRoot()));
  }

  @Test
  public void sendAggregateAndProof_shouldPostAggregateAndProof() {
    final SignedAggregateAndProof aggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();
    when(attestationManager.onAttestation(any(ValidateableAttestation.class)))
        .thenReturn(completedFuture(SUCCESSFUL));
    validatorApiHandler.sendAggregateAndProof(aggregateAndProof);

    verify(attestationManager)
        .onAttestation(ValidateableAttestation.aggregateFromValidator(aggregateAndProof));
  }

  @Test
  void getValidatorIndices_shouldThrowExceptionWhenBestStateNotAvailable() {
    when(chainDataClient.getBestState()).thenReturn(Optional.empty());

    // The validator client needs to be able to differentiate between the state not yet being loaded
    // and the requested validators not existing so it doesn't skip scheduling duties.
    assertThatSafeFuture(
            validatorApiHandler.getValidatorIndices(List.of(dataStructureUtil.randomPublicKey())))
        .isCompletedExceptionallyWith(IllegalStateException.class);
  }

  @Test
  void getValidatorIndices_shouldReturnMapWithKnownValidatorsWhenBestStateAvailable() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final BLSPublicKey validator0 =
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(0).getPubkey());
    final BLSPublicKey unknownValidator = dataStructureUtil.randomPublicKey();
    when(chainDataClient.getBestState()).thenReturn(Optional.of(state));

    assertThatSafeFuture(
            validatorApiHandler.getValidatorIndices(List.of(validator0, unknownValidator)))
        .isCompletedWithValue(Map.of(validator0, 0));
  }

  private <T> Optional<T> assertCompletedSuccessfully(final SafeFuture<Optional<T>> result) {
    assertThat(result).isCompleted();
    return result.join();
  }

  private BeaconState createStateWithActiveValidators() {
    return createStateWithActiveValidators(PREVIOUS_EPOCH_START_SLOT);
  }

  private BeaconState createStateWithActiveValidators(final UInt64 slot) {
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
