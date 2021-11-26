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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.operations.versions.merge.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedStatus;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.type.Bytes20;
import tech.pegasys.teku.ssz.type.Bytes8;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class ForkChoiceNotifierTest {

  private final InlineEventThread eventThread = new InlineEventThread();
  private final Spec spec = TestSpecFactory.createMinimalMerge();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private StorageSystem storageSystem;
  private RecentChainData recentChainData;
  private ForkChoiceStrategy forkChoiceStrategy;

  private final ExecutionEngineChannel executionEngineChannel = mock(ExecutionEngineChannel.class);

  private ForkChoiceNotifier notifier;

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
    // initialize post-merge by default
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    recentChainData = storageSystem.recentChainData();
    notifier = new ForkChoiceNotifier(eventThread, spec, executionEngineChannel, recentChainData);
    storageSystem.chainUpdater().initializeGenesisWithPayload(false);
    storageSystem.chainUpdater().updateBestBlock(storageSystem.chainUpdater().advanceChain());
    forkChoiceStrategy = recentChainData.getForkChoiceStrategy().orElseThrow();

    when(executionEngineChannel.executePayload(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new ExecutePayloadResult(
                    ExecutionPayloadStatus.VALID, Optional.empty(), Optional.empty())));
    when(executionEngineChannel.forkChoiceUpdated(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new ForkChoiceUpdatedResult(ForkChoiceUpdatedStatus.SUCCESS, Optional.empty())));
  }

  void reInitializePreMerge() {
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    recentChainData = storageSystem.recentChainData();
    notifier = new ForkChoiceNotifier(eventThread, spec, executionEngineChannel, recentChainData);
    storageSystem.chainUpdater().initializeGenesis(false);
    storageSystem.chainUpdater().updateBestBlock(storageSystem.chainUpdater().advanceChain());
    forkChoiceStrategy = recentChainData.getForkChoiceStrategy().orElseThrow();
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationToExecutionEngine() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifier.onForkChoiceUpdated(forkChoiceState);
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationWithPayloadAttributesForNextProposer() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = recentChainData.getBestState().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);

    notifier.onForkChoiceUpdated(forkChoiceState);
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes));
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationWithoutPayloadAttributesWhenNotProposingNext() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = recentChainData.getBestState().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    final int notTheNextProposer = spec.getBeaconProposerIndex(headState, blockSlot) + 1;
    notifier.onUpdatePreparableProposers(
        List.of(
            new BeaconPreparableProposer(
                UInt64.valueOf(notTheNextProposer), dataStructureUtil.randomBytes20())));

    notifier.onForkChoiceUpdated(forkChoiceState);
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onForkChoiceUpdated_shouldNotSendNotificationWhenHeadBlockHashIsZero() {
    notifier.onForkChoiceUpdated(new ForkChoiceState(Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO));

    verifyNoInteractions(executionEngineChannel);
  }

  @Test
  void onAttestationsDue_shouldNotSendUpdateIfNotChanged() {
    final BeaconState headState = recentChainData.getBestState().orElseThrow();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifier.onForkChoiceUpdated(forkChoiceState);
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());

    notifier.onAttestationsDue(headState.getSlot());
    verifyNoMoreInteractions(executionEngineChannel);

    notifier.onAttestationsDue(headState.getSlot().plus(1));
    verifyNoMoreInteractions(executionEngineChannel);
  }

  @Test
  void onAttestationsDue_shouldSendUpdateEvenWithAMissedBlockIfWeAreDueToProposeNextTwo() {
    final BeaconState headState = recentChainData.getBestState().orElseThrow();
    final UInt64 blockSlot1 = headState.getSlot().plus(2);
    final UInt64 blockSlot2 = headState.getSlot().plus(3);
    final List<PayloadAttributes> payloadAttributes =
        withProposerForTwoSlots(headState, blockSlot1, blockSlot2);

    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifier.onForkChoiceUpdated(forkChoiceState);
    // Not proposing block +1 so no payload attributes
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());

    notifier.onAttestationsDue(headState.getSlot());
    verifyNoMoreInteractions(executionEngineChannel);

    // Slot +1 is now assumed empty so prepare to propose in slot +2
    notifier.onAttestationsDue(headState.getSlot().plus(1));
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes.get(0)));

    // Slot +2 is now assumed empty so prepare to propose in slot +3
    notifier.onAttestationsDue(headState.getSlot().plus(2));
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes.get(1)));
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

    notifier.onForkChoiceUpdated(getCurrentForkChoiceState());

    verify(executionEngineChannel)
        .forkChoiceUpdated(getCurrentForkChoiceState(), Optional.of(payloadAttributes));
  }

  @Test
  void onUpdatePreparableProposers_shouldSendNewNotificationWhenProposerAdded() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = recentChainData.getBestState().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    notifier.onForkChoiceUpdated(forkChoiceState);
    verify(executionEngineChannel).forkChoiceUpdated(forkChoiceState, Optional.empty());

    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes));
  }

  @Test
  void getPayloadId_shouldReturnLatestPayloadId() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = recentChainData.getBestState().orElseThrow();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionEngineChannel.forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes)))
        .thenReturn(responseFuture);

    notifier.onForkChoiceUpdated(forkChoiceState);

    // Initially has no payload ID.
    assertThatSafeFuture(notifier.getPayloadId(blockRoot)).isNotCompleted();

    // But becomes available once we receive the response
    responseFuture.complete(
        new ForkChoiceUpdatedResult(ForkChoiceUpdatedStatus.SUCCESS, Optional.of(payloadId)));
    assertThatSafeFuture(notifier.getPayloadId(blockRoot))
        .isCompletedWithOptionalContaining(payloadId);
  }

  @Test
  void getPayloadId_shouldReturnExceptionallyLatestPayloadIdOnWrongRoot() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = recentChainData.getBestState().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    final Bytes32 wrongBlockRoot = dataStructureUtil.randomBytes32();

    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionEngineChannel.forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes)))
        .thenReturn(responseFuture);

    notifier.onForkChoiceUpdated(forkChoiceState);

    responseFuture.complete(
        new ForkChoiceUpdatedResult(ForkChoiceUpdatedStatus.SUCCESS, Optional.of(payloadId)));

    assertThatSafeFuture(notifier.getPayloadId(wrongBlockRoot)).isCompletedExceptionally();
  }

  @Test
  void getPayloadId_shouldReturnEmptyWithNoForkChoiceAndNoTerminalBlock() {
    reInitializePreMerge();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();

    assertThatSafeFuture(notifier.getPayloadId(blockRoot)).isCompletedWithEmptyOptional();
  }

  @Test
  void getPayloadId_shouldObtainAPayloadIdOnMergeBlock() {
    reInitializePreMerge();
    Bytes32 terminalBlockHash = dataStructureUtil.randomBytes32();
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();

    final ForkChoiceState forkChoiceState =
        new ForkChoiceState(terminalBlockHash, terminalBlockHash, Bytes32.ZERO);

    final BeaconState headState = recentChainData.getBestState().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final PayloadAttributes payloadAttributes = withProposerForSlot(headState, blockSlot);

    notifier.onTerminalBlockReached(terminalBlockHash);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionEngineChannel.forkChoiceUpdated(forkChoiceState, Optional.of(payloadAttributes)))
        .thenReturn(responseFuture);

    // Initially has no payload ID.
    assertThatSafeFuture(notifier.getPayloadId(blockRoot)).isNotCompleted();

    responseFuture.complete(
        new ForkChoiceUpdatedResult(ForkChoiceUpdatedStatus.SUCCESS, Optional.of(payloadId)));

    assertThatSafeFuture(notifier.getPayloadId(blockRoot))
        .isCompletedWithOptionalContaining(payloadId);
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

  private PayloadAttributes withProposerForSlot(
      final BeaconState headState, final UInt64 blockSlot) {
    final int block2Proposer = spec.getBeaconProposerIndex(headState, blockSlot);
    final PayloadAttributes payloadAttributes = getExpectedPayloadAttributes(headState, blockSlot);
    notifier.onUpdatePreparableProposers(
        List.of(
            new BeaconPreparableProposer(
                UInt64.valueOf(block2Proposer), payloadAttributes.getFeeRecipient())));
    return payloadAttributes;
  }

  private List<PayloadAttributes> withProposerForTwoSlots(
      final BeaconState headState, final UInt64 blockSlot1, UInt64 blockSlot2) {
    final int block2Proposer1 = spec.getBeaconProposerIndex(headState, blockSlot1);
    final int block2Proposer2 = spec.getBeaconProposerIndex(headState, blockSlot2);
    final PayloadAttributes payloadAttributes1 =
        getExpectedPayloadAttributes(headState, blockSlot1);
    final PayloadAttributes payloadAttributes2 =
        getExpectedPayloadAttributes(headState, blockSlot2);

    if (block2Proposer1 != block2Proposer2) {
      notifier.onUpdatePreparableProposers(
          List.of(
              new BeaconPreparableProposer(
                  UInt64.valueOf(block2Proposer1), payloadAttributes1.getFeeRecipient()),
              new BeaconPreparableProposer(
                  UInt64.valueOf(block2Proposer2), payloadAttributes2.getFeeRecipient())));
      return List.of(payloadAttributes1, payloadAttributes2);
    } else {
      notifier.onUpdatePreparableProposers(
          List.of(
              new BeaconPreparableProposer(
                  UInt64.valueOf(block2Proposer1), payloadAttributes1.getFeeRecipient())));
      return List.of(payloadAttributes1, payloadAttributes1);
    }
  }

  private PayloadAttributes getExpectedPayloadAttributes(
      final BeaconState headState, final UInt64 blockSlot) {
    final Bytes20 feeRecipient = dataStructureUtil.randomBytes20();
    final UInt64 timestamp = spec.computeTimeAtSlot(headState, blockSlot);
    final Bytes32 random = spec.getRandaoMix(headState, UInt64.ZERO);
    return new PayloadAttributes(timestamp, random, feeRecipient);
  }

  private ForkChoiceState getCurrentForkChoiceState() {
    final Bytes32 headBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final Bytes32 headExecutionHash =
        forkChoiceStrategy.executionBlockHash(headBlockRoot).orElseThrow();
    final Bytes32 finalizedRoot = recentChainData.getFinalizedCheckpoint().orElseThrow().getRoot();
    final Bytes32 finalizedExecutionHash =
        forkChoiceStrategy.executionBlockHash(finalizedRoot).orElseThrow();

    return new ForkChoiceState(headExecutionHash, headExecutionHash, finalizedExecutionHash);
  }
}
