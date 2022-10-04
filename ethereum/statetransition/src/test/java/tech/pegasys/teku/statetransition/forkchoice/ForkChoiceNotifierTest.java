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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;
import static tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdateData.RESEND_AFTER_MILLIS;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class ForkChoiceNotifierTest {

  private final InlineEventThread eventThread = new InlineEventThread();
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);

  private StubMetricsSystem metricsSystem;
  private StorageSystem storageSystem;
  private RecentChainData recentChainData;
  private ReadOnlyForkChoiceStrategy forkChoiceStrategy;
  private ProposersDataManager proposersDataManager;

  private final Optional<Eth1Address> defaultFeeRecipient =
      Optional.of(Eth1Address.fromHexString("0x2Df386eFF130f991321bfC4F8372Ba838b9AB14B"));

  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);

  private ForkChoiceNotifierImpl notifier;
  private ForkChoiceUpdatedResultNotification forkChoiceUpdatedResultNotification;

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.blsVerifyDeposit = false;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.blsVerifyDeposit = true;
  }

  @BeforeEach
  void setUp() {
    setUp(false);
  }

  void setUp(final boolean doNotInitializeWithDefaultFeeRecipient) {
    // initialize post-merge by default
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    recentChainData = storageSystem.recentChainData();
    metricsSystem = new StubMetricsSystem();
    proposersDataManager =
        spy(
            new ProposersDataManager(
                eventThread,
                spec,
                metricsSystem,
                executionLayerChannel,
                recentChainData,
                doNotInitializeWithDefaultFeeRecipient ? Optional.empty() : defaultFeeRecipient));
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            timeProvider,
            spec,
            executionLayerChannel,
            recentChainData,
            proposersDataManager);
    notifier.onSyncingStatusChanged(true); // Start in sync to make testing easier
    // store fcu notification
    notifier.subscribeToForkChoiceUpdatedResult(
        notification -> forkChoiceUpdatedResultNotification = notification);
    storageSystem.chainUpdater().initializeGenesisWithPayload(false);
    storageSystem.chainUpdater().updateBestBlock(storageSystem.chainUpdater().advanceChain());
    forkChoiceStrategy = recentChainData.getForkChoiceStrategy().orElseThrow();

    when(executionLayerChannel.builderRegisterValidators(any(), any()))
        .thenReturn(SafeFuture.COMPLETE);
    when(executionLayerChannel.engineNewPayload(any()))
        .thenReturn(SafeFuture.completedFuture(PayloadStatus.VALID));
    when(executionLayerChannel.engineForkChoiceUpdated(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.empty())));
  }

  void reInitializePreMerge() {
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    recentChainData = storageSystem.recentChainData();
    metricsSystem = new StubMetricsSystem();
    proposersDataManager =
        spy(
            new ProposersDataManager(
                eventThread,
                spec,
                metricsSystem,
                executionLayerChannel,
                recentChainData,
                defaultFeeRecipient));
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            timeProvider,
            spec,
            executionLayerChannel,
            recentChainData,
            proposersDataManager);
    notifier.onSyncingStatusChanged(true);
    // store fcu notification
    notifier.subscribeToForkChoiceUpdatedResult(
        notification -> forkChoiceUpdatedResultNotification = notification);
    storageSystem.chainUpdater().initializeGenesis(false);
    storageSystem.chainUpdater().updateBestBlock(storageSystem.chainUpdater().advanceChain());
    forkChoiceStrategy = recentChainData.getForkChoiceStrategy().orElseThrow();
  }

  private void doMerge(Bytes32 terminalBlockHash) {
    // advance chain with the terminal block
    SignedBlockAndState newBlockWithExecutionPayloadAtopTerminalBlock =
        storageSystem
            .chainUpdater()
            .chainBuilder
            .generateBlockAtSlot(
                recentChainData.getHeadSlot().plus(1),
                ChainBuilder.BlockOptions.create().setTerminalBlockHash(terminalBlockHash));

    storageSystem.chainUpdater().updateBestBlock(newBlockWithExecutionPayloadAtopTerminalBlock);
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationToExecutionEngine() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationWithPayloadBuildingAttributesForNextProposer() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);

    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes));
  }

  @Test
  void
      onForkChoiceUpdated_shouldSendNotificationWithPayloadBuildingAttributesForProposerAtProposingSlot() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes));
  }

  @Test
  void
      onForkChoiceUpdated_shouldSendNotificationWithoutPayloadBuildingAttributesWhenNotProposingNext() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    final int notTheNextProposer = spec.getBeaconProposerIndex(headState, blockSlot) + 1;
    proposersDataManager.updatePreparedProposers(
        List.of(
            new BeaconPreparableProposer(
                UInt64.valueOf(notTheNextProposer), dataStructureUtil.randomEth1Address())),
        recentChainData.getHeadSlot());

    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onForkChoiceUpdated_shouldNotSendNotificationWhenHeadBlockHashIsZero() {

    notifyForkChoiceUpdated(
        new ForkChoiceState(
            Bytes32.ZERO, UInt64.ZERO, Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO, false));

    verifyNoInteractions(executionLayerChannel);
  }

  @Test
  @SuppressWarnings("unchecked")
  void onForkChoiceUpdated_shouldNotSendNotificationOfOutOfOrderPayloadBuildingAttributes() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1); // slot 2

    // proposer index 1 and 0 will propose slot 2 and 3
    final List<PayloadBuildingAttributes> payloadBuildingAttributes =
        withProposerForTwoSlots(headState, blockSlot, blockSlot.plus(1));

    // current slot is 1

    // store real payload attributes and return an incomplete future
    AtomicReference<SafeFuture<Optional<PayloadBuildingAttributes>>> actualResponseA =
        new AtomicReference<>();
    SafeFuture<Optional<PayloadBuildingAttributes>> deferredResponseA = new SafeFuture<>();
    doAnswer(
            invocation -> {
              actualResponseA.set(
                  (SafeFuture<Optional<PayloadBuildingAttributes>>) invocation.callRealMethod());
              return deferredResponseA;
            })
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    notifyForkChoiceUpdated(forkChoiceState); // calculate attributes for slot 2

    // it is called once with no attributes. the one with attributes is pending
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());

    // forward to real method call
    doAnswer(InvocationOnMock::callRealMethod)
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    storageSystem
        .chainUpdater()
        .setCurrentSlot(headState.getSlot().plus(1)); // set current slot to 2

    notifyForkChoiceUpdated(forkChoiceState); // calculate attributes for slot 3

    // expect a call with second attributes
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(1)));

    // let the payload attributes for slot 2 return
    actualResponseA.get().propagateTo(deferredResponseA);

    // it should get ignored
    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationOfOrderedPayloadBuildingAttributes() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1); // slot 2

    // proposer index 1 and 0 will propose slot 2 and 3
    final List<PayloadBuildingAttributes> payloadBuildingAttributes =
        withProposerForTwoSlots(headState, blockSlot, blockSlot.plus(1));

    // current slot is 1

    notifyForkChoiceUpdated(forkChoiceState); // calculate attributes for slot 2

    // expect attributes for slot 2
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(0)));

    storageSystem
        .chainUpdater()
        .setCurrentSlot(headState.getSlot().plus(1)); // set current slot to 2

    notifyForkChoiceUpdated(forkChoiceState); // calculate attributes for slot 3

    // expect attributes for slot 3
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(1)));

    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  @SuppressWarnings("unchecked")
  void onForkChoiceUpdated_shouldNotSendNotificationWithOldPayloadBuildingAttributes() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    BeaconState headState = getHeadState();
    UInt64 blockSlot = headState.getSlot().plus(1); // slot 2

    final List<PayloadBuildingAttributes> payloadBuildingAttributesArr =
        withProposerForTwoSlots(headState, blockSlot, blockSlot.plus(1));

    // proposer index 1 and 0 will propose slot 2 and 3
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);

    // current slot is 1

    notifyForkChoiceUpdated(forkChoiceState); // calculate attributes for slot 2

    // expect attributes for slot 2
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes));

    // advance chain (generate block at slot 2), we get a new forkChoiceState
    storageSystem.chainUpdater().addNewBestBlock();

    headState = getHeadState();
    blockSlot = headState.getSlot().plus(1);

    // new attributes for new state block production at slot 3
    final PayloadBuildingAttributes payloadBuildingAttributesSlot3 =
        withProposerForSlot(
            headState,
            blockSlot,
            false,
            Optional.of(payloadBuildingAttributesArr.get(1).getFeeRecipient()),
            Optional.empty());
    final ForkChoiceState forkChoiceStateSlot3 = getCurrentForkChoiceState();

    // store real payload attributes and return an incomplete future
    AtomicReference<SafeFuture<Optional<PayloadBuildingAttributes>>> actualResponseA =
        new AtomicReference<>();
    SafeFuture<Optional<PayloadBuildingAttributes>> deferredResponseA = new SafeFuture<>();
    doAnswer(
            invocation -> {
              actualResponseA.set(
                  (SafeFuture<Optional<PayloadBuildingAttributes>>) invocation.callRealMethod());
              return deferredResponseA;
            })
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    notifyForkChoiceUpdated(forkChoiceStateSlot3); // calculate attributes for slot 2

    // it is called once with no attributes. the one with attributes is pending
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceStateSlot3, Optional.empty());

    // let the payload attributes for slot 3 return
    actualResponseA.get().propagateTo(deferredResponseA);

    // expect attributes for slot 3
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceStateSlot3, Optional.of(payloadBuildingAttributesSlot3));

    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void onAttestationsDue_shouldNotSendUpdateIfNotChanged() {
    final BeaconState headState = getHeadState();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());

    notifier.onAttestationsDue(headState.getSlot());
    verifyNoMoreInteractions(executionLayerChannel);

    notifier.onAttestationsDue(headState.getSlot().plus(1));
    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void onAttestationsDue_shouldSendUpdateIfNotChangedButResendLimitPassed() {
    final BeaconState headState = getHeadState();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifyForkChoiceUpdated(forkChoiceState);

    timeProvider.advanceTimeByMillis(RESEND_AFTER_MILLIS.longValue() + 1);

    notifier.onAttestationsDue(headState.getSlot());
    verify(executionLayerChannel, times(2))
        .engineForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onAttestationsDue_shouldSendUpdateEvenWithAMissedBlockIfWeAreDueToProposeNextTwo() {
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot1 = headState.getSlot().plus(1); // slot 2
    final UInt64 blockSlot2 = headState.getSlot().plus(2); // slot 3
    final List<PayloadBuildingAttributes> payloadBuildingAttributes =
        withProposerForTwoSlots(headState, blockSlot1, blockSlot2);
    // context:
    //  current slot is 1
    //  proposer index 1 proposes on slot 2
    //  proposer index 0 proposes on slot 3

    // slot is 1 and is not empty -> sending forkChoiceUpdated
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifyForkChoiceUpdated(forkChoiceState);
    // We are proposing block on slot 2
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(0)));

    // onAttestationsDue for slot 1 (attributes for slot2)
    notifier.onAttestationsDue(headState.getSlot());
    verifyNoMoreInteractions(executionLayerChannel);

    // simulating we missed trying to produce a block: we are now in slot 2
    storageSystem
        .chainUpdater()
        .setCurrentSlot(recentChainData.getCurrentSlot().orElseThrow().plus(1));

    // Slot 2 is now assumed empty so prepare to propose in slot 3
    notifier.onAttestationsDue(recentChainData.getCurrentSlot().orElseThrow());
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(1)));

    // Shouldn't resend with added payload attributes
    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void shouldUseStateFromCorrectEpochToCalculateBlockProposer() {
    final int firstSlotOfNextEpoch = spec.getSlotsPerEpoch(UInt64.ZERO);
    final UInt64 blockSlot = UInt64.valueOf(firstSlotOfNextEpoch);
    final UInt64 slotBeforeBlock = blockSlot.minus(1);
    final SignedBlockAndState headBlockAndState =
        storageSystem.chainUpdater().advanceChainUntil(firstSlotOfNextEpoch - 1);
    storageSystem.chainUpdater().updateBestBlock(headBlockAndState);
    final BeaconState headState = headBlockAndState.getState();
    storageSystem.chainUpdater().setTime(spec.computeTimeAtSlot(headState, slotBeforeBlock));
    assertThat(recentChainData.getCurrentSlot()).contains(slotBeforeBlock);

    final PayloadBuildingAttributes payloadBuildingAttributes = withProposerForSlot(blockSlot);

    notifyForkChoiceUpdated(getCurrentForkChoiceState());

    verify(executionLayerChannel)
        .engineForkChoiceUpdated(
            getCurrentForkChoiceState(), Optional.of(payloadBuildingAttributes));
  }

  @Test
  void onForkChoiceUpdated_shouldNotIncludePayloadBuildingAttributesWhileSyncing() {
    withProposerForSlot(recentChainData.getHeadSlot().plus(1));
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifier.onSyncingStatusChanged(false);

    notifyForkChoiceUpdated(forkChoiceState);

    // We're syncing so don't include payload attributes
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onPreparedProposersUpdated_shouldNotIncludePayloadBuildingAttributesWhileSyncing() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());

    notifier.onSyncingStatusChanged(false);
    withProposerForSlot(recentChainData.getHeadSlot().plus(1));

    // Shouldn't resend with added payload attributes
    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void onPreparedProposersUpdated_shouldSendNewNotificationWhenProposerAdded() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());

    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes));
  }

  @Test
  void getPayloadId_shouldReturnLatestPayloadId() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    notifyForkChoiceUpdated(forkChoiceState);

    // Initially has no payload ID.
    assertThatSafeFuture(notifier.getPayloadId(blockRoot, blockSlot)).isNotCompleted();

    // But becomes available once we receive the response
    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes);
    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));
    assertThatSafeFuture(notifier.getPayloadId(blockRoot, blockSlot))
        .isCompletedWithOptionalContaining(executionPayloadContext);
  }

  @Test
  void getPayloadId_shouldReturnLatestPayloadIdWithValidatorRegistration() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(
            headState,
            blockSlot,
            true,
            Optional.empty(),
            Optional.of(createValidatorRegistration(headState, blockSlot)));

    assertThat(payloadBuildingAttributes.getValidatorRegistration()).isNotEmpty();

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    notifyForkChoiceUpdated(forkChoiceState);

    // Initially has no payload ID.
    assertThatSafeFuture(notifier.getPayloadId(blockRoot, blockSlot)).isNotCompleted();

    // But becomes available once we receive the response
    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes);
    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));
    assertThatSafeFuture(notifier.getPayloadId(blockRoot, blockSlot))
        .isCompletedWithOptionalContaining(executionPayloadContext);
  }

  @Test
  void getPayloadId_shouldReturnExceptionallyLatestPayloadIdOnWrongRoot() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    final Bytes32 wrongBlockRoot = dataStructureUtil.randomBytes32();

    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    notifyForkChoiceUpdated(forkChoiceState);

    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));

    assertThatSafeFuture(notifier.getPayloadId(wrongBlockRoot, blockSlot))
        .isCompletedExceptionally();
  }

  @Test
  void getPayloadId_shouldReturnEmptyWithNoForkChoiceAndNoTerminalBlock() {
    reInitializePreMerge();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = recentChainData.getHeadSlot().plus(1);

    assertThatSafeFuture(notifier.getPayloadId(blockRoot, blockSlot))
        .isCompletedWithEmptyOptional();
  }

  @Test
  void getPayloadId_shouldObtainAPayloadIdWhenProposingTheMergeBlock() {
    reInitializePreMerge();
    Bytes32 terminalBlockHash = dataStructureUtil.randomBytes32();
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();

    // we expect head block root and slot to be ZERO since in the test we do not send an
    // onForkChoiceUpdated before calling onTerminalBlock, so it will initialize ZEROED
    final ForkChoiceState forkChoiceState =
        new ForkChoiceState(
            Bytes32.ZERO, UInt64.ZERO, terminalBlockHash, Bytes32.ZERO, Bytes32.ZERO, false);

    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);

    notifier.onTerminalBlockReached(terminalBlockHash);

    validateGetPayloadIdOnTheFlyRetrieval(
        blockSlot, blockRoot, forkChoiceState, payloadId, payloadBuildingAttributes, false);
  }

  @Test
  void getPayloadId_shouldObtainAPayloadIdOnPostMergeBlockNonFinalized() {
    reInitializePreMerge();
    // current slot: 1

    Bytes32 terminalBlockHash = dataStructureUtil.randomBytes32();
    doMerge(terminalBlockHash);

    // current slot: 2
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(3); // proposing slot 5
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);

    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();

    // send merge onForkChoiceUpdated (with non-finalized block state)
    final ForkChoiceState nonFinalizedForkChoiceState = getCurrentForkChoiceState();
    assertThat(nonFinalizedForkChoiceState.getFinalizedExecutionBlockHash())
        .isEqualTo(Bytes32.ZERO);
    notifyForkChoiceUpdated(nonFinalizedForkChoiceState);
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(nonFinalizedForkChoiceState, Optional.empty());

    final Bytes8 payloadId = dataStructureUtil.randomBytes8();

    validateGetPayloadIdOnTheFlyRetrieval(
        blockSlot,
        blockRoot,
        nonFinalizedForkChoiceState,
        payloadId,
        payloadBuildingAttributes,
        false);
  }

  @Test
  void getPayloadId_shouldObtainAPayloadIdOnPostMergeBlockFinalized() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();

    // current slot: 1

    // send post-merge onForkChoiceUpdated (with finalized block state)
    ForkChoiceState finalizedForkChoiceState = getCurrentForkChoiceState();
    assertThat(finalizedForkChoiceState.getFinalizedExecutionBlockHash())
        .isNotEqualTo(Bytes32.ZERO);
    notifyForkChoiceUpdated(finalizedForkChoiceState);
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(finalizedForkChoiceState, Optional.empty());

    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);

    validateGetPayloadIdOnTheFlyRetrieval(
        blockSlot,
        blockRoot,
        finalizedForkChoiceState,
        payloadId,
        payloadBuildingAttributes,
        false);
  }

  @Test
  void getPayloadId_shouldObtainAPayloadIdOnPostMergeBlockFinalizedEvenIfProposerNotPrepared() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();

    // current slot: 1

    // send post-merge onForkChoiceUpdated (with finalized block state)
    ForkChoiceState finalizedForkChoiceState = getCurrentForkChoiceState();
    assertThat(finalizedForkChoiceState.getFinalizedExecutionBlockHash())
        .isNotEqualTo(Bytes32.ZERO);
    notifyForkChoiceUpdated(finalizedForkChoiceState);
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(finalizedForkChoiceState, Optional.empty());

    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlotButDoNotPrepare(headState, blockSlot, defaultFeeRecipient);

    validateGetPayloadIdOnTheFlyRetrieval(
        blockSlot,
        blockRoot,
        finalizedForkChoiceState,
        payloadId,
        payloadBuildingAttributes,
        false);
  }

  @Test
  void getPayloadId_shouldFailIfDefaultFeeRecipientIsNotConfigured() {
    setUp(true);
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();

    // current slot: 1

    // send post-merge onForkChoiceUpdated (with finalized block state)
    ForkChoiceState finalizedForkChoiceState = getCurrentForkChoiceState();
    assertThat(finalizedForkChoiceState.getFinalizedExecutionBlockHash())
        .isNotEqualTo(Bytes32.ZERO);
    notifyForkChoiceUpdated(finalizedForkChoiceState);
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(finalizedForkChoiceState, Optional.empty());

    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlotButDoNotPrepare(headState, blockSlot, Optional.empty());

    validateGetPayloadIdOnTheFlyRetrieval(
        blockSlot, blockRoot, finalizedForkChoiceState, payloadId, payloadBuildingAttributes, true);
  }

  @Test
  void getPayloadId_shouldReturnExceptionallyBeforeTheFirstForkChoiceState() {
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();

    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    // we are post-merge, we must have a payloadId
    assertThatSafeFuture(notifier.getPayloadId(blockRoot, blockSlot)).isCompletedExceptionally();
  }

  @Test
  void getPayloadId_preMergeShouldReturnEmptyBeforeTheFirstForkChoiceState() {
    reInitializePreMerge();

    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();

    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(headState, blockSlot);
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    // we are pre-merge, we can continue producing blocks with no execution payload
    assertThatSafeFuture(notifier.getPayloadId(blockRoot, blockSlot))
        .isCompletedWithEmptyOptional();
  }

  private void notifyForkChoiceUpdated(final ForkChoiceState forkChoiceState) {
    notifyForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  private void notifyForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> proposingSlot) {
    forkChoiceUpdatedResultNotification = null;
    notifier.onForkChoiceUpdated(forkChoiceState, proposingSlot);
    assertThat(forkChoiceUpdatedResultNotification).isNotNull();
    assertThat(forkChoiceUpdatedResultNotification.getForkChoiceUpdatedResult()).isCompleted();
  }

  private void validateGetPayloadIdOnTheFlyRetrieval(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final ForkChoiceState forkChoiceState,
      final Bytes8 payloadId,
      final PayloadBuildingAttributes payloadBuildingAttributes,
      final boolean mustFail) {
    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    // Initially has no payload ID.
    SafeFuture<Optional<ExecutionPayloadContext>> futureExecutionPayloadContext =
        notifier.getPayloadId(blockRoot, blockSlot);
    assertThatSafeFuture(futureExecutionPayloadContext).isNotCompleted();

    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));

    if (mustFail) {
      assertThatSafeFuture(futureExecutionPayloadContext).isCompletedExceptionally();
    } else {
      final ExecutionPayloadContext executionPayloadContext =
          new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes);
      assertThatSafeFuture(futureExecutionPayloadContext)
          .isCompletedWithOptionalContaining(executionPayloadContext);
    }
  }

  private PayloadBuildingAttributes withProposerForSlot(final UInt64 blockSlot) {
    final Bytes32 bestBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final BeaconState state =
        recentChainData
            .retrieveStateAtSlot(new SlotAndBlockRoot(blockSlot, bestBlockRoot))
            .join()
            .orElseThrow();
    return withProposerForSlot(state, blockSlot);
  }

  private PayloadBuildingAttributes withProposerForSlotButDoNotPrepare(
      final BeaconState headState,
      final UInt64 blockSlot,
      final Optional<Eth1Address> overrideFeeRecipient) {
    return withProposerForSlot(headState, blockSlot, false, overrideFeeRecipient, Optional.empty());
  }

  private PayloadBuildingAttributes withProposerForSlot(
      final BeaconState headState, final UInt64 blockSlot) {
    return withProposerForSlot(headState, blockSlot, true, Optional.empty(), Optional.empty());
  }

  private PayloadBuildingAttributes withProposerForSlot(
      final BeaconState headState,
      final UInt64 blockSlot,
      final boolean doPrepare,
      final Optional<Eth1Address> overrideFeeRecipient,
      final Optional<SignedValidatorRegistration> validatorRegistration) {
    final int block2Proposer = spec.getBeaconProposerIndex(headState, blockSlot);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        getExpectedPayloadBuildingAttributes(
            headState, blockSlot, overrideFeeRecipient, validatorRegistration);
    if (doPrepare) {
      proposersDataManager.updatePreparedProposers(
          List.of(
              new BeaconPreparableProposer(
                  UInt64.valueOf(block2Proposer), payloadBuildingAttributes.getFeeRecipient())),
          recentChainData.getHeadSlot());
    }
    validatorRegistration.ifPresent(
        signedValidatorRegistration ->
            SafeFutureAssert.safeJoin(
                proposersDataManager.updateValidatorRegistrations(
                    SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.createFromElements(
                        List.of(signedValidatorRegistration)),
                    recentChainData.getHeadSlot())));
    return payloadBuildingAttributes;
  }

  private List<PayloadBuildingAttributes> withProposerForTwoSlots(
      final BeaconState headState, final UInt64 blockSlot1, UInt64 blockSlot2) {
    final int block2Proposer1 = spec.getBeaconProposerIndex(headState, blockSlot1);
    final int block2Proposer2 = spec.getBeaconProposerIndex(headState, blockSlot2);
    final PayloadBuildingAttributes payloadBuildingAttributes1 =
        getExpectedPayloadBuildingAttributes(
            headState, blockSlot1, Optional.empty(), Optional.empty());
    final PayloadBuildingAttributes payloadBuildingAttributes2 =
        getExpectedPayloadBuildingAttributes(
            headState, blockSlot2, Optional.empty(), Optional.empty());

    if (block2Proposer1 == block2Proposer2) {
      throw new UnsupportedOperationException(
          "unsupported test scenario: with same proposer for different slots");
    }
    proposersDataManager.updatePreparedProposers(
        List.of(
            new BeaconPreparableProposer(
                UInt64.valueOf(block2Proposer1), payloadBuildingAttributes1.getFeeRecipient()),
            new BeaconPreparableProposer(
                UInt64.valueOf(block2Proposer2), payloadBuildingAttributes2.getFeeRecipient())),
        recentChainData.getHeadSlot());
    return List.of(payloadBuildingAttributes1, payloadBuildingAttributes2);
  }

  private PayloadBuildingAttributes getExpectedPayloadBuildingAttributes(
      final BeaconState headState,
      final UInt64 blockSlot,
      final Optional<Eth1Address> overrideFeeRecipient,
      final Optional<SignedValidatorRegistration> validatorRegistration) {
    final Eth1Address feeRecipient =
        overrideFeeRecipient.orElse(dataStructureUtil.randomEth1Address());
    final UInt64 timestamp = spec.computeTimeAtSlot(headState, blockSlot);
    final Bytes32 random = spec.getRandaoMix(headState, UInt64.ZERO);
    return new PayloadBuildingAttributes(timestamp, random, feeRecipient, validatorRegistration);
  }

  private ForkChoiceState getCurrentForkChoiceState() {
    final UInt64 headBlockSlot = recentChainData.getHeadSlot();
    final Bytes32 headBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final Bytes32 headExecutionHash =
        forkChoiceStrategy.executionBlockHash(headBlockRoot).orElseThrow();
    final Bytes32 finalizedRoot = recentChainData.getFinalizedCheckpoint().orElseThrow().getRoot();
    final Bytes32 finalizedExecutionHash =
        forkChoiceStrategy.executionBlockHash(finalizedRoot).orElseThrow();

    return new ForkChoiceState(
        headBlockRoot,
        headBlockSlot,
        headExecutionHash,
        headExecutionHash,
        finalizedExecutionHash,
        false);
  }

  private SignedValidatorRegistration createValidatorRegistration(
      final BeaconState headState, final UInt64 blockSlot) {
    final int block2Proposer = spec.getBeaconProposerIndex(headState, blockSlot);
    return dataStructureUtil.randomSignedValidatorRegistration(
        spec.getValidatorPubKey(headState, UInt64.valueOf(block2Proposer)).orElseThrow());
  }

  private ForkChoiceUpdatedResult createForkChoiceUpdatedResult(
      ExecutionPayloadStatus status, Optional<Bytes8> payloadId) {
    return new ForkChoiceUpdatedResult(
        PayloadStatus.create(status, Optional.empty(), Optional.empty()), payloadId);
  }

  private BeaconState getHeadState() {
    final SafeFuture<BeaconState> stateFuture = recentChainData.getBestState().orElseThrow();
    assertThat(stateFuture).isCompleted();
    return stateFuture.join();
  }
}
