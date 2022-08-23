/*
 * Copyright ConsenSys Software Inc., 2022
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
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason.DOES_NOT_DESCEND_FROM_LATEST_FINALIZED;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.migrated.StateValidatorData;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker;

class ValidatorApiHandlerTest {

  private static final UInt64 EPOCH = UInt64.valueOf(13);
  private static final UInt64 PREVIOUS_EPOCH = EPOCH.minus(ONE);
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(EPOCH);
  private final UInt64 previousEpochStartSlot = spec.computeStartSlotAtEpoch(PREVIOUS_EPOCH);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final SyncStateProvider syncStateProvider = mock(SyncStateProvider.class);
  private final BlockFactory blockFactory = mock(BlockFactory.class);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final AttestationManager attestationManager = mock(AttestationManager.class);
  private final AttestationTopicSubscriber attestationTopicSubscriptions =
      mock(AttestationTopicSubscriber.class);
  private final ActiveValidatorTracker activeValidatorTracker = mock(ActiveValidatorTracker.class);
  private final BlockImportChannel blockImportChannel = mock(BlockImportChannel.class);
  private final BlockGossipChannel blockGossipChannel = mock(BlockGossipChannel.class);
  private final DefaultPerformanceTracker performanceTracker =
      mock(DefaultPerformanceTracker.class);
  private final ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  private final DutyMetrics dutyMetrics = mock(DutyMetrics.class);
  private final ForkChoiceTrigger forkChoiceTrigger = mock(ForkChoiceTrigger.class);
  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
  private final SyncCommitteeMessagePool syncCommitteeMessagePool =
      mock(SyncCommitteeMessagePool.class);
  private final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  private final SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager =
      mock(SyncCommitteeSubscriptionManager.class);

  private final ValidatorApiHandler validatorApiHandler =
      new ValidatorApiHandler(
          chainDataProvider,
          chainDataClient,
          syncStateProvider,
          blockFactory,
          blockImportChannel,
          blockGossipChannel,
          attestationPool,
          attestationManager,
          attestationTopicSubscriptions,
          activeValidatorTracker,
          dutyMetrics,
          performanceTracker,
          spec,
          forkChoiceTrigger,
          proposersDataManager,
          syncCommitteeMessagePool,
          syncCommitteeContributionPool,
          syncCommitteeSubscriptionManager);

  @BeforeEach
  public void setUp() {
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(forkChoiceTrigger.prepareForBlockProduction(any())).thenReturn(SafeFuture.COMPLETE);
    when(chainDataClient.isOptimisticBlock(any())).thenReturn(false);
    doAnswer(invocation -> SafeFuture.completedFuture(invocation.getArgument(0)))
        .when(blockFactory)
        .unblindSignedBeaconBlockIfBlinded(any());
    when(proposersDataManager.updateValidatorRegistrations(any(), any()))
        .thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void isSyncActive_syncIsActiveAndHeadIsBehind() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH.minus(2));
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  @Test
  public void isSyncActive_syncIsActiveAndHeadALittleBehind() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH.minus(1));
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  @Test
  public void isSyncActive_syncIsActiveAndHeadIsCaughtUp() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH);
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
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
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of(1));
    assertThat(duties).isCompletedExceptionally();
    assertThatThrownBy(duties::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void getAttestationDuties_shouldReturnNoDutiesWhenNoIndicesSpecified() {
    final BeaconState state = createStateWithActiveValidators();
    when(chainDataClient.getStateAtSlotExact(previousEpochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(ONE));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of());
    final AttesterDuties duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(duties.getDuties()).isEmpty();
    assertThat(duties.getDependentRoot()).isEqualTo(spec.getCurrentDutyDependentRoot(state));
  }

  @Test
  public void getAttestationDuties_shouldUsePreviousDutyDependentRootWhenStateFromSameEpoch() {
    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    when(chainDataClient.getStateAtSlotExact(any()))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(ONE));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of());
    final AttesterDuties duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(duties.getDependentRoot()).isEqualTo(spec.getPreviousDutyDependentRoot(state));
  }

  @Test
  public void getAttestationDuties_shouldFailForEpochTooFarAhead() {
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(3));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of(1));
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getAttestationDuties_shouldReturnDutiesAndSkipMissingValidators() {
    final BeaconState state = createStateWithActiveValidators();
    final BLSPublicKey validator1Key =
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(1).getPubkeyBytes());
    when(chainDataClient.getStateAtSlotExact(previousEpochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(ONE));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of(1, 32));
    final Optional<AttesterDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().getDuties())
        .containsExactly(new AttesterDuty(validator1Key, 1, 4, 0, 1, 1, UInt64.valueOf(108)));
  }

  @Test
  public void getAttestationDuties_shouldAllowOneEpochTolerance() {
    final BeaconState state = createStateWithActiveValidators();
    final BLSPublicKey validator1Key =
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(1).getPubkeyBytes());
    when(chainDataClient.getStateAtSlotExact(previousEpochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(2));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of(1, 32));
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
    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    when(chainDataClient.getStateAtSlotExact(epochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH);

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH);
    final ProposerDuties duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(duties.getDuties().size()).isEqualTo(spec.slotsPerEpoch(EPOCH));
    assertThat(duties.getDependentRoot()).isEqualTo(spec.getCurrentDutyDependentRoot(state));
  }

  @Test
  public void getProposerDuties_shouldAllowOneEpochTolerance() {
    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    when(chainDataClient.getStateAtSlotExact(epochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(1));

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH);
    final Optional<ProposerDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().getDuties().size()).isEqualTo(spec.slotsPerEpoch(EPOCH));
  }

  @Test
  void getProposerDuties_shouldReturnDutiesInOrder() {
    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    when(chainDataClient.getStateAtSlotExact(epochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(1));

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH);
    final Optional<ProposerDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().getDuties())
        .isSortedAccordingTo(Comparator.comparing(ProposerDuty::getSlot));
  }

  @Test
  void getSyncCommitteeDuties_shouldFailForEpochTooFarAhead() {
    final BeaconState state = dataStructureUtil.stateBuilderAltair().slot(epochStartSlot).build();
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH);
    when(chainDataClient.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));
    final int epochsPerSyncCommitteePeriod =
        SpecConfigAltair.required(spec.getSpecConfig(EPOCH)).getEpochsPerSyncCommitteePeriod();
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(epochStartSlot);
    final UInt64 firstSlotAfterNextSyncCommitteePeriod =
        syncCommitteeUtil
            .computeFirstEpochOfCurrentSyncCommitteePeriod(EPOCH)
            .plus(epochsPerSyncCommitteePeriod * 2L);
    assertThatSafeFuture(
            validatorApiHandler.getSyncCommitteeDuties(
                firstSlotAfterNextSyncCommitteePeriod, IntList.of(1)))
        .isCompletedExceptionallyWith(IllegalArgumentException.class)
        .hasMessageContaining("not within the current or next sync committee periods");
  }

  @Test
  void getSyncCommitteeDuties_shouldNotUseEpochPriorToFork() {
    final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(EPOCH);
    final ValidatorApiHandler validatorApiHandler =
        new ValidatorApiHandler(
            chainDataProvider,
            chainDataClient,
            syncStateProvider,
            blockFactory,
            blockImportChannel,
            blockGossipChannel,
            attestationPool,
            attestationManager,
            attestationTopicSubscriptions,
            activeValidatorTracker,
            dutyMetrics,
            performanceTracker,
            spec,
            forkChoiceTrigger,
            proposersDataManager,
            syncCommitteeMessagePool,
            syncCommitteeContributionPool,
            syncCommitteeSubscriptionManager);
    // Best state is still in Phase0
    final BeaconState state =
        dataStructureUtil.stateBuilderPhase0().slot(previousEpochStartSlot.minus(1)).build();
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH);
    when(chainDataClient.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));
    when(chainDataClient.getStateAtSlotExact(any())).thenReturn(new SafeFuture<>());

    final SafeFuture<Optional<SyncCommitteeDuties>> result =
        validatorApiHandler.getSyncCommitteeDuties(EPOCH, IntList.of(1));
    assertThat(result).isNotDone();

    // The start of the sync committee period is prior to the fork block so we should use the
    // fork block to ensure we actually have sync committees available.
    verify(chainDataClient).getStateAtSlotExact(epochStartSlot);
  }

  @Test
  public void createUnsignedBlock_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(
            ONE, dataStructureUtil.randomSignature(), Optional.empty(), false);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createUnsignedBlock_shouldFailWhenParentBlockIsOptimistic() {
    final UInt64 newSlot = UInt64.valueOf(25);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(newSlot);
    when(chainDataClient.getStateAtSlotExact(newSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockSlotState)));
    final Bytes32 parentRoot = spec.getBlockRootAtSlot(blockSlotState, newSlot.minus(1));
    when(chainDataClient.isOptimisticBlock(parentRoot)).thenReturn(true);

    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(
            newSlot, dataStructureUtil.randomSignature(), Optional.empty(), false);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
    verifyNoInteractions(blockFactory);
  }

  @Test
  public void createUnsignedBlock_shouldCreateBlock() throws Exception {
    final UInt64 newSlot = UInt64.valueOf(25);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(newSlot);
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BeaconBlock createdBlock = dataStructureUtil.randomBeaconBlock(newSlot.longValue());

    when(chainDataClient.getStateAtSlotExact(newSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockSlotState)));
    when(blockFactory.createUnsignedBlock(
            blockSlotState, newSlot, randaoReveal, Optional.empty(), false))
        .thenReturn(SafeFuture.completedFuture(createdBlock));

    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(newSlot, randaoReveal, Optional.empty(), false);

    verify(blockFactory)
        .createUnsignedBlock(blockSlotState, newSlot, randaoReveal, Optional.empty(), false);
    assertThat(result).isCompletedWithValue(Optional.of(createdBlock));
  }

  @Test
  public void createAttestationData_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<AttestationData>> result =
        validatorApiHandler.createAttestationData(ONE, 1);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createAttestationData_shouldFailWhenHeadIsOptimistic() {
    final UInt64 slot = spec.computeStartSlotAtEpoch(EPOCH).plus(ONE);
    when(chainDataClient.getCurrentSlot()).thenReturn(slot);

    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(state.getSlot(), state);
    final SignedBlockAndState blockAndState = new SignedBlockAndState(block, state);

    final SafeFuture<Optional<SignedBlockAndState>> blockAndStateResult =
        completedFuture(Optional.of(blockAndState));
    when(chainDataClient.getSignedBlockAndStateInEffectAtSlot(slot))
        .thenReturn(blockAndStateResult);
    when(forkChoiceTrigger.prepareForAttestationProduction(slot)).thenReturn(SafeFuture.COMPLETE);

    when(chainDataClient.isOptimisticBlock(blockAndState.getRoot())).thenReturn(true);

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        validatorApiHandler.createAttestationData(slot, committeeIndex);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createAttestationData_shouldCreateAttestation() {
    final UInt64 slot = spec.computeStartSlotAtEpoch(EPOCH).plus(ONE);
    when(chainDataClient.getCurrentSlot()).thenReturn(slot);

    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(state.getSlot(), state);
    final SignedBlockAndState blockAndState = new SignedBlockAndState(block, state);

    final SafeFuture<Optional<SignedBlockAndState>> blockAndStateResult =
        completedFuture(Optional.of(blockAndState));
    when(chainDataClient.getSignedBlockAndStateInEffectAtSlot(slot))
        .thenReturn(blockAndStateResult);
    when(forkChoiceTrigger.prepareForAttestationProduction(slot)).thenReturn(SafeFuture.COMPLETE);

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        validatorApiHandler.createAttestationData(slot, committeeIndex);

    assertThat(result).isCompleted();
    final Optional<AttestationData> maybeAttestation = result.join();
    assertThat(maybeAttestation).isPresent();
    final AttestationData attestationData = maybeAttestation.orElseThrow();
    assertThat(attestationData)
        .isEqualTo(
            spec.getGenericAttestationData(
                slot, state, block.getMessage(), UInt64.valueOf(committeeIndex)));
    assertThat(attestationData.getSlot()).isEqualTo(slot);
    final InOrder inOrder = inOrder(forkChoiceTrigger, chainDataClient);

    // Ensure we prepare for attestation production prior to getting the block to attest to
    inOrder.verify(forkChoiceTrigger).prepareForAttestationProduction(slot);
    inOrder.verify(chainDataClient).getSignedBlockAndStateInEffectAtSlot(slot);
  }

  @Test
  public void createAttestationData_shouldRejectRequestWhenSlotIsInTheFuture() {
    final UInt64 slot = spec.computeStartSlotAtEpoch(EPOCH).plus(ONE);
    when(chainDataClient.getCurrentSlot()).thenReturn(slot.minus(1));

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        validatorApiHandler.createAttestationData(slot, committeeIndex);

    assertThatSafeFuture(result).isCompletedExceptionallyWith(IllegalArgumentException.class);
  }

  @Test
  public void createAttestationData_shouldUseCorrectSourceWhenEpochTransitionRequired() {
    final UInt64 slot = spec.computeStartSlotAtEpoch(EPOCH);
    when(chainDataClient.getCurrentSlot()).thenReturn(slot);
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
                CheckpointState.create(
                    spec, new Checkpoint(EPOCH, block.getRoot()), block, rightState)));
    when(forkChoiceTrigger.prepareForAttestationProduction(slot)).thenReturn(SafeFuture.COMPLETE);

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        validatorApiHandler.createAttestationData(slot, committeeIndex);

    assertThat(result).isCompleted();
    final Optional<AttestationData> maybeAttestation = result.join();
    assertThat(maybeAttestation).isPresent();
    final AttestationData attestationData = maybeAttestation.orElseThrow();
    assertThat(attestationData)
        .isEqualTo(
            spec.getGenericAttestationData(
                slot, rightState, block.getMessage(), UInt64.valueOf(committeeIndex)));
    assertThat(attestationData.getSlot()).isEqualTo(slot);
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
        .thenReturn(aggregate.map(attestation -> ValidateableAttestation.from(spec, attestation)));

    assertThat(
            validatorApiHandler.createAggregate(
                aggregate.get().getData().getSlot(), attestationData.hashTreeRoot()))
        .isCompletedWithValue(aggregate);
  }

  @Test
  public void subscribeToBeaconCommittee_shouldSubscribeViaAttestationTopicSubscriptions() {
    final int committeeIndex = 10;
    final UInt64 aggregationSlot = UInt64.valueOf(13);
    final UInt64 committeesAtSlot = UInt64.valueOf(10);
    final int validatorIndex = 1;

    final SafeFuture<Void> result =
        validatorApiHandler.subscribeToBeaconCommittee(
            List.of(
                new CommitteeSubscriptionRequest(
                    validatorIndex, committeeIndex, committeesAtSlot, aggregationSlot, true)));

    assertThat(result).isCompleted();
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

    final SafeFuture<Void> result =
        validatorApiHandler.subscribeToBeaconCommittee(
            List.of(
                new CommitteeSubscriptionRequest(
                    validatorIndex, committeeIndex, committeesAtSlot, aggregationSlot, false)));

    assertThat(result).isCompleted();
    verifyNoInteractions(attestationTopicSubscriptions);
    verify(activeValidatorTracker).onCommitteeSubscriptionRequest(validatorIndex, aggregationSlot);
  }

  @Test
  void subscribeToSyncCommitteeSubnets_shouldConvertCommitteeIndexToSubnetId() {
    final SyncCommitteeSubnetSubscription subscription1 =
        new SyncCommitteeSubnetSubscription(1, IntSet.of(1, 2, 15, 30), UInt64.valueOf(44));
    final SyncCommitteeSubnetSubscription subscription2 =
        new SyncCommitteeSubnetSubscription(1, IntSet.of(5, 10), UInt64.valueOf(35));

    final SafeFuture<Void> result =
        validatorApiHandler.subscribeToSyncCommitteeSubnets(List.of(subscription1, subscription2));

    assertThat(result).isCompleted();

    final UInt64 unsubscribeSlotSubscription1 = spec.computeStartSlotAtEpoch(UInt64.valueOf(44));
    final UInt64 unsubscribeSlotSubscription2 = spec.computeStartSlotAtEpoch(UInt64.valueOf(35));

    verify(syncCommitteeSubscriptionManager).subscribe(0, unsubscribeSlotSubscription1);
    verify(syncCommitteeSubscriptionManager).subscribe(1, unsubscribeSlotSubscription1);
    verify(syncCommitteeSubscriptionManager).subscribe(3, unsubscribeSlotSubscription1);

    verify(syncCommitteeSubscriptionManager).subscribe(0, unsubscribeSlotSubscription2);
    verify(syncCommitteeSubscriptionManager).subscribe(1, unsubscribeSlotSubscription2);
    verifyNoMoreInteractions(syncCommitteeSubscriptionManager);
  }

  @Test
  public void sendSignedAttestations_shouldAddAttestationToAttestationManager() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationManager.addAttestation(any(ValidateableAttestation.class)))
        .thenReturn(completedFuture(InternalValidationResult.ACCEPT));
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(attestation));
    assertThat(result).isCompletedWithValue(emptyList());

    verify(attestationManager).addAttestation(ValidateableAttestation.from(spec, attestation));
  }

  @Test
  void sendSignedAttestations_shouldAddToDutyMetricsAndPerformanceTrackerWhenNotInvalid() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationManager.addAttestation(any(ValidateableAttestation.class)))
        .thenReturn(completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(attestation));
    assertThat(result).isCompletedWithValue(emptyList());

    verify(dutyMetrics).onAttestationPublished(attestation.getData().getSlot());
    verify(performanceTracker).saveProducedAttestation(attestation);
  }

  @Test
  void sendSignedAttestations_shouldNotAddToDutyMetricsAndPerformanceTrackerWhenInvalid() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationManager.addAttestation(any(ValidateableAttestation.class)))
        .thenReturn(completedFuture(InternalValidationResult.reject("Bad juju")));

    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(attestation));
    assertThat(result).isCompletedWithValue(List.of(new SubmitDataError(ZERO, "Bad juju")));

    verify(dutyMetrics, never()).onAttestationPublished(attestation.getData().getSlot());
    verify(performanceTracker, never()).saveProducedAttestation(attestation);
  }

  @Test
  void sendSignedAttestations_shouldProcessMixOfValidAndInvalidAttestations() {
    final Attestation invalidAttestation = dataStructureUtil.randomAttestation();
    final Attestation validAttestation = dataStructureUtil.randomAttestation();
    when(attestationManager.addAttestation(validatableAttestationOf(invalidAttestation)))
        .thenReturn(completedFuture(InternalValidationResult.reject("Bad juju")));
    when(attestationManager.addAttestation(validatableAttestationOf(validAttestation)))
        .thenReturn(completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(invalidAttestation, validAttestation));
    assertThat(result).isCompletedWithValue(List.of(new SubmitDataError(ZERO, "Bad juju")));

    verify(dutyMetrics, never()).onAttestationPublished(invalidAttestation.getData().getSlot());
    verify(dutyMetrics).onAttestationPublished(validAttestation.getData().getSlot());
    verify(performanceTracker, never()).saveProducedAttestation(invalidAttestation);
    verify(performanceTracker).saveProducedAttestation(validAttestation);
  }

  private ValidateableAttestation validatableAttestationOf(final Attestation validAttestation) {
    return argThat(
        argument -> argument != null && argument.getAttestation().equals(validAttestation));
  }

  @Test
  public void sendSignedBlock_shouldConvertSuccessfulResult() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    when(blockImportChannel.importBlock(block))
        .thenReturn(SafeFuture.completedFuture(BlockImportResult.successful(block)));
    final SafeFuture<SendSignedBlockResult> result = validatorApiHandler.sendSignedBlock(block);

    verify(blockGossipChannel).publishBlock(block);
    verify(blockImportChannel).importBlock(block);
    assertThat(result).isCompletedWithValue(SendSignedBlockResult.success(block.getRoot()));
  }

  @Test
  public void sendSignedBlock_shouldConvertFailedResult() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    when(blockImportChannel.importBlock(block))
        .thenReturn(SafeFuture.completedFuture(BlockImportResult.FAILED_INVALID_ANCESTRY));
    final SafeFuture<SendSignedBlockResult> result = validatorApiHandler.sendSignedBlock(block);

    verify(blockGossipChannel).publishBlock(block);
    verify(blockImportChannel).importBlock(block);
    assertThat(result)
        .isCompletedWithValue(
            SendSignedBlockResult.notImported(DOES_NOT_DESCEND_FROM_LATEST_FINALIZED.name()));
  }

  @Test
  public void sendSignedBlock_shouldConvertKnownBlockResult() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    when(blockImportChannel.importBlock(block))
        .thenReturn(SafeFuture.completedFuture(BlockImportResult.knownBlock(block, false)));
    final SafeFuture<SendSignedBlockResult> result = validatorApiHandler.sendSignedBlock(block);

    verify(blockGossipChannel).publishBlock(block);
    verify(blockImportChannel).importBlock(block);
    assertThat(result).isCompletedWithValue(SendSignedBlockResult.success(block.getRoot()));
  }

  @Test
  public void sendAggregateAndProofs_shouldPostAggregateAndProof() {
    final SignedAggregateAndProof aggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();
    when(attestationManager.addAggregate(any(ValidateableAttestation.class)))
        .thenReturn(completedFuture(InternalValidationResult.ACCEPT));
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendAggregateAndProofs(List.of(aggregateAndProof));
    assertThat(result).isCompletedWithValue(emptyList());

    verify(attestationManager)
        .addAggregate(ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof));
  }

  @Test
  void sendAggregateAndProofs_shouldProcessMixOfValidAndInvalidAggregates() {
    final SignedAggregateAndProof invalidAggregate =
        dataStructureUtil.randomSignedAggregateAndProof();
    final SignedAggregateAndProof validAggregate =
        dataStructureUtil.randomSignedAggregateAndProof();
    when(attestationManager.addAggregate(
            ValidateableAttestation.aggregateFromValidator(spec, invalidAggregate)))
        .thenReturn(completedFuture(InternalValidationResult.reject("Bad juju")));
    when(attestationManager.addAggregate(
            ValidateableAttestation.aggregateFromValidator(spec, validAggregate)))
        .thenReturn(completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendAggregateAndProofs(List.of(invalidAggregate, validAggregate));
    assertThat(result).isCompletedWithValue(List.of(new SubmitDataError(ZERO, "Bad juju")));

    // Should send both to the attestation manager.
    verify(attestationManager)
        .addAggregate(
            argThat(
                validatableAttestation ->
                    validatableAttestation.getSignedAggregateAndProof().equals(validAggregate)));
    verify(attestationManager)
        .addAggregate(
            argThat(
                validatableAttestation ->
                    validatableAttestation.getSignedAggregateAndProof().equals(invalidAggregate)));
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
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(0).getPubkeyBytes());
    final BLSPublicKey unknownValidator = dataStructureUtil.randomPublicKey();
    when(chainDataClient.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));

    assertThatSafeFuture(
            validatorApiHandler.getValidatorIndices(List.of(validator0, unknownValidator)))
        .isCompletedWithValue(Map.of(validator0, 0));
  }

  @Test
  void sendSyncCommitteeMessages_shouldAllowEmptyRequest() {
    final List<SyncCommitteeMessage> messages = List.of();
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSyncCommitteeMessages(messages);
    assertThat(result).isCompleted();
  }

  @Test
  void sendSyncCommitteeMessages_shouldAddMessagesToPool() {
    final SyncCommitteeMessage message = dataStructureUtil.randomSyncCommitteeMessage();
    final List<SyncCommitteeMessage> messages = List.of(message);
    when(syncCommitteeMessagePool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSyncCommitteeMessages(messages);
    assertThat(result).isCompletedWithValue(emptyList());
    verify(performanceTracker).saveProducedSyncCommitteeMessage(message);
  }

  @Test
  void sendSyncCommitteeMessages_shouldRaiseErrors() {
    final SyncCommitteeMessage message = dataStructureUtil.randomSyncCommitteeMessage();
    final List<SyncCommitteeMessage> messages = List.of(message);
    when(syncCommitteeMessagePool.addLocal(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                InternalValidationResult.create(ValidationResultCode.REJECT, "Rejected")));
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSyncCommitteeMessages(messages);
    assertThat(result).isCompletedWithValue(List.of(new SubmitDataError(UInt64.ZERO, "Rejected")));
    verify(performanceTracker, never()).saveProducedSyncCommitteeMessage(message);
  }

  @Test
  void sendSignedContributionAndProofs_shouldAllowEmptyRequest() {
    final SafeFuture<Void> result =
        validatorApiHandler.sendSignedContributionAndProofs(emptyList());
    assertThat(result).isCompleted();
  }

  @Test
  void sendSignedContributionAndProofs_shouldAddContributionsToPool() {
    final SignedContributionAndProof contribution =
        dataStructureUtil.randomSignedContributionAndProof(5);
    when(syncCommitteeContributionPool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<Void> result =
        validatorApiHandler.sendSignedContributionAndProofs(List.of(contribution));
    assertThat(result).isCompleted();
  }

  @Test
  void sendSignedContributionAndProofs_shouldReportErrors() {
    final SignedContributionAndProof contribution1 =
        dataStructureUtil.randomSignedContributionAndProof(5);
    final SignedContributionAndProof contribution2 =
        dataStructureUtil.randomSignedContributionAndProof(5);
    when(syncCommitteeContributionPool.addLocal(contribution1))
        .thenReturn(
            SafeFuture.completedFuture(
                InternalValidationResult.create(ValidationResultCode.REJECT, "Bad")));
    when(syncCommitteeContributionPool.addLocal(contribution2))
        .thenReturn(
            SafeFuture.completedFuture(
                InternalValidationResult.create(ValidationResultCode.REJECT, "Worse")));

    final SafeFuture<Void> result =
        validatorApiHandler.sendSignedContributionAndProofs(List.of(contribution1, contribution2));
    assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(IllegalArgumentException.class)
        .hasMessageContainingAll("Bad", "Worse");
  }

  @Test
  void registerValidators_shouldUpdateRegistrations() {
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(4);

    setupValidatorsState(validatorRegistrations);

    when(chainDataClient.getCurrentSlot()).thenReturn(ONE);

    final SafeFuture<Void> result = validatorApiHandler.registerValidators(validatorRegistrations);

    assertThat(result).isCompleted();

    verify(proposersDataManager).updateValidatorRegistrations(validatorRegistrations, ONE);
  }

  @Test
  void registerValidators_shouldIgnoreExitedValidators() {
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(4);

    final BLSPublicKey exitedUnslashedValidatorKey =
        validatorRegistrations.get(2).getMessage().getPublicKey();
    final BLSPublicKey exitedSlashedValidatorKey =
        validatorRegistrations.get(3).getMessage().getPublicKey();

    setupValidatorsState(
        validatorRegistrations,
        Map.of(
            exitedUnslashedValidatorKey,
            ValidatorStatus.exited_unslashed,
            exitedSlashedValidatorKey,
            ValidatorStatus.exited_slashed));

    when(chainDataClient.getCurrentSlot()).thenReturn(ONE);

    final SafeFuture<Void> result = validatorApiHandler.registerValidators(validatorRegistrations);

    assertThat(result).isCompleted();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<SszList<SignedValidatorRegistration>> argumentCaptor =
        ArgumentCaptor.forClass(SszList.class);

    verify(proposersDataManager).updateValidatorRegistrations(argumentCaptor.capture(), eq(ONE));

    final SszList<SignedValidatorRegistration> capturedRegistrations = argumentCaptor.getValue();

    assertThat(capturedRegistrations)
        .hasSize(2)
        .map(signedRegistration -> signedRegistration.getMessage().getPublicKey())
        .doesNotContain(exitedUnslashedValidatorKey, exitedSlashedValidatorKey);
  }

  @Test
  void registerValidators_shouldReportErrorIfCannotRetrieveValidatorStatuses() {
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(4);

    when(chainDataProvider.getStateValidators(eq("head"), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<Void> result = validatorApiHandler.registerValidators(validatorRegistrations);

    SafeFutureAssert.assertThatSafeFuture(result)
        .isCompletedExceptionallyWithMessage(
            "Couldn't retrieve validator statuses during registering. Most likely the BN is still syncing.");
  }

  private <T> Optional<T> assertCompletedSuccessfully(final SafeFuture<Optional<T>> result) {
    assertThat(result).isCompleted();
    return result.join();
  }

  private BeaconState createStateWithActiveValidators() {
    return createStateWithActiveValidators(previousEpochStartSlot);
  }

  private BeaconState createStateWithActiveValidators(final UInt64 slot) {
    return dataStructureUtil
        .randomBeaconState(32)
        .updated(
            state -> {
              state.setSlot(slot);
              final SszMutableList<Validator> validators = state.getValidators();
              for (int i = 0; i < validators.size(); i++) {
                validators.update(
                    i,
                    validator ->
                        validator
                            .withActivationEligibilityEpoch(ZERO)
                            .withActivationEpoch(ZERO)
                            .withExitEpoch(SpecConfig.FAR_FUTURE_EPOCH)
                            .withWithdrawableEpoch(SpecConfig.FAR_FUTURE_EPOCH));
              }
            });
  }

  private void setupValidatorsState(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    setupValidatorsState(validatorRegistrations, Collections.emptyMap());
  }

  private void setupValidatorsState(
      final SszList<SignedValidatorRegistration> validatorRegistrations,
      final Map<BLSPublicKey, ValidatorStatus> statusOverrides) {
    final List<StateValidatorData> data =
        IntStream.range(0, 4)
            .mapToObj(
                index -> {
                  final SignedValidatorRegistration validatorRegistration =
                      validatorRegistrations.get(index);
                  final BLSPublicKey publicKey = validatorRegistration.getMessage().getPublicKey();
                  return new StateValidatorData(
                      UInt64.valueOf(index),
                      dataStructureUtil.randomUInt64(),
                      statusOverrides.getOrDefault(publicKey, ValidatorStatus.active_ongoing),
                      dataStructureUtil.randomValidator(publicKey));
                })
            .collect(Collectors.toList());

    final ObjectAndMetaData<List<StateValidatorData>> stateValidators =
        new ObjectAndMetaData<>(data, SpecMilestone.BELLATRIX, false, true);

    final List<String> validators =
        validatorRegistrations.stream()
            .map(SignedValidatorRegistration::getMessage)
            .map(ValidatorRegistration::getPublicKey)
            .map(BLSPublicKey::toString)
            .collect(toList());

    when(chainDataProvider.getStateValidators("head", validators, new HashSet<>()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(stateValidators)));
  }
}
