/*
 * Copyright 2021 ConsenSys AG.
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;
import tech.pegasys.teku.spec.executionengine.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class ForkChoiceNotifierTest {

  private final InlineEventThread eventThread = new InlineEventThread();
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private StorageSystem storageSystem;
  private RecentChainData recentChainData;
  private ReadOnlyForkChoiceStrategy forkChoiceStrategy;
  private PayloadAttributesCalculator payloadAttributesCalculator;
  private Optional<Bytes20> defaultFeeRecipient =
      Optional.of(Bytes20.fromHexString("0x2Df386eFF130f991321bfC4F8372Ba838b9AB14B"));

  private final ExecutionEngineChannel executionEngineChannel = mock(ExecutionEngineChannel.class);

  private ForkChoiceNotifierImpl notifier;

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = false;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = true;
  }

  @BeforeEach
  void setUp() {
    setUp(false);
  }

  void setUp(final boolean doNotInitializeWithDefaultFeeRecipient) {
    // initialize post-merge by default
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    recentChainData = storageSystem.recentChainData();
    payloadAttributesCalculator =
        spy(
            new PayloadAttributesCalculator(
                spec,
                eventThread,
                recentChainData,
                doNotInitializeWithDefaultFeeRecipient ? Optional.empty() : defaultFeeRecipient));
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            spec,
            executionEngineChannel,
            recentChainData,
            payloadAttributesCalculator);
    notifier.onSyncingStatusChanged(true); // Start in sync to make testing easier
    storageSystem.chainUpdater().initializeGenesisWithPayload(false);
    storageSystem.chainUpdater().updateBestBlock(storageSystem.chainUpdater().advanceChain());
    forkChoiceStrategy = recentChainData.getForkChoiceStrategy().orElseThrow();

    when(executionEngineChannel.newPayload(any()))
        .thenReturn(SafeFuture.completedFuture(PayloadStatus.VALID));
    when(executionEngineChannel.forkChoiceUpdated(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.empty())));
  }

  void reInitializePreMerge() {
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    recentChainData = storageSystem.recentChainData();
    payloadAttributesCalculator =
        spy(
            new PayloadAttributesCalculator(
                spec, eventThread, recentChainData, defaultFeeRecipient));
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            spec,
            executionEngineChannel,
            recentChainData,
            payloadAttributesCalculator);
    notifier.onSyncingStatusChanged(true);
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
    assertThat(notifier.onForkChoiceUpdated(forkChoiceState)).isCompleted();
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationWithPayloadAttributesForNextProposer() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);

    assertThat(notifier.onForkChoiceUpdated(forkChoiceState)).isCompleted();
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes));
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationWithoutPayloadAttributesWhenNotProposingNext() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    final int notTheNextProposer = spec.getBeaconProposerIndex(headState, blockSlot) + 1;
    notifier.onUpdatePreparableProposers(
        List.of(
            new BeaconPreparableProposer(
                UInt64.valueOf(notTheNextProposer), dataStructureUtil.randomBytes20())));

    assertThat(notifier.onForkChoiceUpdated(forkChoiceState)).isCompleted();
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onForkChoiceUpdated_shouldNotSendNotificationWhenHeadBlockHashIsZero() {
    assertThat(
            notifier.onForkChoiceUpdated(
                new ForkChoiceState(
                    Bytes32.ZERO, UInt64.ZERO, Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO, false)))
        .isCompleted();

    verifyNoInteractions(executionEngineChannel);
  }

  @Test
  @SuppressWarnings("unchecked")
  void onForkChoiceUpdated_shouldNotSendNotificationOfOutOfOrderPayloadAttributes() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1); // slot 2

    // proposer index 1 and 0 will propose slot 2 and 3
    final List<PayloadAttributes> payloadAttributes =
        withProposerForTwoSlots(headState, blockSlot, blockSlot.plus(1));

    // current slot is 1

    // store real payload attributes and return an incomplete future
    AtomicReference<SafeFuture<Optional<PayloadAttributes>>> actualResponseA =
        new AtomicReference<>();
    SafeFuture<Optional<PayloadAttributes>> deferredResponseA = new SafeFuture<>();
    doAnswer(
            invocation -> {
              actualResponseA.set(
                  (SafeFuture<Optional<PayloadAttributes>>) invocation.callRealMethod());
              return deferredResponseA;
            })
        .when(payloadAttributesCalculator)
        .calculatePayloadAttributes(any(), anyBoolean(), any(), anyBoolean());

    assertThat(notifier.onForkChoiceUpdated(forkChoiceState))
        .isCompleted(); // calculate attributes for slot 2

    // it is called once with no attributes. the one with attributes is pending
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());

    // forward to real method call
    doAnswer(InvocationOnMock::callRealMethod)
        .when(payloadAttributesCalculator)
        .calculatePayloadAttributes(any(), anyBoolean(), any(), anyBoolean());

    storageSystem
        .chainUpdater()
        .setCurrentSlot(headState.getSlot().plus(1)); // set current slot to 2

    assertThat(notifier.onForkChoiceUpdated(forkChoiceState))
        .isCompleted(); // calculate attributes for slot 3

    // expect a call with second attributes
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes.get(1)));

    // let the payload attributes for slot 2 return
    actualResponseA.get().propagateTo(deferredResponseA);

    // it should get ignored
    verifyNoMoreInteractions(executionEngineChannel);
  }

  @Test
  void onForkChoiceUpdated_shouldNotSendNotificationOfOrderedPayloadAttributes() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1); // slot 2

    // proposer index 1 and 0 will propose slot 2 and 3
    final List<PayloadAttributes> payloadAttributes =
        withProposerForTwoSlots(headState, blockSlot, blockSlot.plus(1));

    // current slot is 1

    assertThat(notifier.onForkChoiceUpdated(forkChoiceState))
        .isCompleted(); // calculate attributes for slot 2

    // expect attributes for slot 2
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes.get(0)));

    storageSystem
        .chainUpdater()
        .setCurrentSlot(headState.getSlot().plus(1)); // set current slot to 2

    assertThat(notifier.onForkChoiceUpdated(forkChoiceState))
        .isCompleted(); // calculate attributes for slot 3

    // expect attributes for slot 3
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes.get(1)));

    // it should get ignored
    verifyNoMoreInteractions(executionEngineChannel);
  }

  @Test
  void onAttestationsDue_shouldNotSendUpdateIfNotChanged() {
    final BeaconState headState = getHeadState();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    assertThat(notifier.onForkChoiceUpdated(forkChoiceState)).isCompleted();
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());

    notifier.onAttestationsDue(headState.getSlot());
    verifyNoMoreInteractions(executionEngineChannel);

    notifier.onAttestationsDue(headState.getSlot().plus(1));
    verifyNoMoreInteractions(executionEngineChannel);
  }

  @Test
  void onAttestationsDue_shouldSendUpdateEvenWithAMissedBlockIfWeAreDueToProposeNextTwo() {
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot1 = headState.getSlot().plus(1); // slot 2
    final UInt64 blockSlot2 = headState.getSlot().plus(2); // slot 3
    final List<PayloadAttributes> payloadAttributes =
        withProposerForTwoSlots(headState, blockSlot1, blockSlot2);
    // context:
    //  current slot is 1
    //  proposer index 1 proposes on slot 2
    //  proposer index 0 proposes on slot 3

    // slot is 1 and is not empty -> sending forkChoiceUpdated
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    assertThat(notifier.onForkChoiceUpdated(forkChoiceState)).isCompleted();
    // We are proposing block on slot 2
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes.get(0)));

    // onAttestationsDue for slot 1 (attributes for slot2)
    notifier.onAttestationsDue(headState.getSlot());
    verifyNoMoreInteractions(executionEngineChannel);

    // simulating we missed trying to produce a block: we are now in slot 2
    storageSystem
        .chainUpdater()
        .setCurrentSlot(recentChainData.getCurrentSlot().orElseThrow().plus(1));

    // Slot 2 is now assumed empty so prepare to propose in slot 3
    notifier.onAttestationsDue(recentChainData.getCurrentSlot().orElseThrow());
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes.get(1)));

    // Shouldn't resend with added payload attributes
    verifyNoMoreInteractions(executionEngineChannel);
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

    final PayloadAttributes payloadAttributes = withProposerForSlot(blockSlot);

    assertThat(notifier.onForkChoiceUpdated(getCurrentForkChoiceState())).isCompleted();

    verify(executionEngineChannel)
        .forkChoiceUpdated(getCurrentForkChoiceState(), Optional.of(payloadAttributes));
  }

  @Test
  void onForkChoiceUpdated_shouldNotIncludePayloadAttributesWhileSyncing() {
    withProposerForSlot(recentChainData.getHeadSlot().plus(1));
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifier.onSyncingStatusChanged(false);

    assertThat(notifier.onForkChoiceUpdated(forkChoiceState)).isCompleted();

    // We're syncing so don't include payload attributes
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onUpdatePreparableProposers_shouldNotIncludePayloadAttributesWhileSyncing() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    assertThat(notifier.onForkChoiceUpdated(forkChoiceState)).isCompleted();
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());

    notifier.onSyncingStatusChanged(false);
    withProposerForSlot(recentChainData.getHeadSlot().plus(1));

    // Shouldn't resend with added payload attributes
    verifyNoMoreInteractions(executionEngineChannel);
  }

  @Test
  void onUpdatePreparableProposers_shouldSendNewNotificationWhenProposerAdded() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    assertThat(notifier.onForkChoiceUpdated(forkChoiceState)).isCompleted();
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());

    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes));
  }

  @Test
  void getPayloadId_shouldReturnLatestPayloadId() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionEngineChannel.forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes)))
        .thenReturn(responseFuture);

    assertThat(notifier.onForkChoiceUpdated(forkChoiceState)).isCompleted();

    // Initially has no payload ID.
    assertThatSafeFuture(notifier.getPayloadId(blockRoot, blockSlot)).isNotCompleted();

    // But becomes available once we receive the response
    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));
    assertThatSafeFuture(notifier.getPayloadId(blockRoot, blockSlot))
        .isCompletedWithOptionalContaining(payloadId);
  }

  @Test
  void getPayloadId_shouldReturnExceptionallyLatestPayloadIdOnWrongRoot() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    final Bytes32 wrongBlockRoot = dataStructureUtil.randomBytes32();

    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionEngineChannel.forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes)))
        .thenReturn(responseFuture);

    assertThat(notifier.onForkChoiceUpdated(forkChoiceState)).isCompleted();

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
            Bytes32.ZERO, UInt64.ZERO, terminalBlockHash, terminalBlockHash, Bytes32.ZERO, false);

    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);

    notifier.onTerminalBlockReached(terminalBlockHash);

    validateGetPayloadIdOnTheFlyRetrieval(
        blockSlot, blockRoot, forkChoiceState, payloadId, payloadAttributes, false);
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
    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);

    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();

    // send merge onForkChoiceUpdated (with non-finalized block state)
    final ForkChoiceState nonFinalizedForkChoiceState = getCurrentForkChoiceState();
    assertThat(nonFinalizedForkChoiceState.getFinalizedExecutionBlockHash())
        .isEqualTo(Bytes32.ZERO);
    assertThat(notifier.onForkChoiceUpdated(nonFinalizedForkChoiceState)).isCompleted();
    verify(executionEngineChannel).forkChoiceUpdated(nonFinalizedForkChoiceState, Optional.empty());

    final Bytes8 payloadId = dataStructureUtil.randomBytes8();

    validateGetPayloadIdOnTheFlyRetrieval(
        blockSlot, blockRoot, nonFinalizedForkChoiceState, payloadId, payloadAttributes, false);
  }

  @Test
  void getPayloadId_shouldObtainAPayloadIdOnPostMergeBlockFinalized() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();

    // current slot: 1

    // send post-merge onForkChoiceUpdated (with finalized block state)
    ForkChoiceState finalizedForkChoiceState = getCurrentForkChoiceState();
    assertThat(finalizedForkChoiceState.getFinalizedExecutionBlockHash())
        .isNotEqualTo(Bytes32.ZERO);
    assertThat(notifier.onForkChoiceUpdated(finalizedForkChoiceState)).isCompleted();
    verify(executionEngineChannel).forkChoiceUpdated(finalizedForkChoiceState, Optional.empty());

    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);

    validateGetPayloadIdOnTheFlyRetrieval(
        blockSlot, blockRoot, finalizedForkChoiceState, payloadId, payloadAttributes, false);
  }

  @Test
  void getPayloadId_shouldObtainAPayloadIdOnPostMergeBlockFinalizedEvenIfProposerNotPrepared() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();

    // current slot: 1

    // send post-merge onForkChoiceUpdated (with finalized block state)
    ForkChoiceState finalizedForkChoiceState = getCurrentForkChoiceState();
    assertThat(finalizedForkChoiceState.getFinalizedExecutionBlockHash())
        .isNotEqualTo(Bytes32.ZERO);
    assertThat(notifier.onForkChoiceUpdated(finalizedForkChoiceState)).isCompleted();
    verify(executionEngineChannel).forkChoiceUpdated(finalizedForkChoiceState, Optional.empty());

    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final PayloadAttributes payloadAttributes =
        withProposerForSlotButDoNotPrepare(headState, blockSlot, defaultFeeRecipient);

    validateGetPayloadIdOnTheFlyRetrieval(
        blockSlot, blockRoot, finalizedForkChoiceState, payloadId, payloadAttributes, false);
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
    assertThat(notifier.onForkChoiceUpdated(finalizedForkChoiceState)).isCompleted();
    verify(executionEngineChannel).forkChoiceUpdated(finalizedForkChoiceState, Optional.empty());

    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final PayloadAttributes payloadAttributes =
        withProposerForSlotButDoNotPrepare(headState, blockSlot, Optional.empty());

    validateGetPayloadIdOnTheFlyRetrieval(
        blockSlot, blockRoot, finalizedForkChoiceState, payloadId, payloadAttributes, true);
  }

  @Test
  void getPayloadId_shouldReturnExceptionallyBeforeTheFirstForkChoiceState() {
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();

    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);
    when(executionEngineChannel.forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes)))
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
    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);
    when(executionEngineChannel.forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes)))
        .thenReturn(responseFuture);

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    // we are pre-merge, we can continue producing blocks with no execution payload
    assertThatSafeFuture(notifier.getPayloadId(blockRoot, blockSlot))
        .isCompletedWithEmptyOptional();
  }

  private void validateGetPayloadIdOnTheFlyRetrieval(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final ForkChoiceState forkChoiceState,
      final Bytes8 payloadId,
      final PayloadAttributes payloadAttributes,
      final boolean mustFail) {
    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    when(executionEngineChannel.forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes)))
        .thenReturn(responseFuture);

    // Initially has no payload ID.
    SafeFuture<Optional<Bytes8>> futurePayloadId = notifier.getPayloadId(blockRoot, blockSlot);
    assertThatSafeFuture(futurePayloadId).isNotCompleted();

    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));

    if (mustFail) {
      assertThatSafeFuture(futurePayloadId).isCompletedExceptionally();
    } else {
      assertThatSafeFuture(futurePayloadId).isCompletedWithOptionalContaining(payloadId);
    }
  }

  private PayloadAttributes withProposerForSlot(final UInt64 blockSlot) {
    final Bytes32 bestBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final BeaconState state =
        recentChainData
            .retrieveStateAtSlot(new SlotAndBlockRoot(blockSlot, bestBlockRoot))
            .join()
            .orElseThrow();
    return withProposerForSlot(state, blockSlot);
  }

  private PayloadAttributes withProposerForSlotButDoNotPrepare(
      final BeaconState headState,
      final UInt64 blockSlot,
      final Optional<Bytes20> overrideFeeRecipient) {
    return withProposerForSlot(headState, blockSlot, false, overrideFeeRecipient);
  }

  private PayloadAttributes withProposerForSlot(
      final BeaconState headState, final UInt64 blockSlot) {
    return withProposerForSlot(headState, blockSlot, true, Optional.empty());
  }

  private PayloadAttributes withProposerForSlot(
      final BeaconState headState,
      final UInt64 blockSlot,
      final boolean doPrepare,
      final Optional<Bytes20> overrideFeeRecipient) {
    final int block2Proposer = spec.getBeaconProposerIndex(headState, blockSlot);
    final PayloadAttributes payloadAttributes =
        getExpectedPayloadAttributes(headState, blockSlot, overrideFeeRecipient);
    if (doPrepare) {
      notifier.onUpdatePreparableProposers(
          List.of(
              new BeaconPreparableProposer(
                  UInt64.valueOf(block2Proposer), payloadAttributes.getFeeRecipient())));
    }
    return payloadAttributes;
  }

  private List<PayloadAttributes> withProposerForTwoSlots(
      final BeaconState headState, final UInt64 blockSlot1, UInt64 blockSlot2) {
    final int block2Proposer1 = spec.getBeaconProposerIndex(headState, blockSlot1);
    final int block2Proposer2 = spec.getBeaconProposerIndex(headState, blockSlot2);
    final PayloadAttributes payloadAttributes1 =
        getExpectedPayloadAttributes(headState, blockSlot1, Optional.empty());
    final PayloadAttributes payloadAttributes2 =
        getExpectedPayloadAttributes(headState, blockSlot2, Optional.empty());

    if (block2Proposer1 == block2Proposer2) {
      throw new UnsupportedOperationException(
          "unsupported test scenario: with same proposer for different slots");
    }
    notifier.onUpdatePreparableProposers(
        List.of(
            new BeaconPreparableProposer(
                UInt64.valueOf(block2Proposer1), payloadAttributes1.getFeeRecipient()),
            new BeaconPreparableProposer(
                UInt64.valueOf(block2Proposer2), payloadAttributes2.getFeeRecipient())));
    return List.of(payloadAttributes1, payloadAttributes2);
  }

  private PayloadAttributes getExpectedPayloadAttributes(
      final BeaconState headState,
      final UInt64 blockSlot,
      final Optional<Bytes20> overrideFeeRecipient) {
    final Bytes20 feeRecipient = overrideFeeRecipient.orElse(dataStructureUtil.randomBytes20());
    final UInt64 timestamp = spec.computeTimeAtSlot(headState, blockSlot);
    final Bytes32 random = spec.getRandaoMix(headState, UInt64.ZERO);
    return new PayloadAttributes(timestamp, random, feeRecipient);
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
