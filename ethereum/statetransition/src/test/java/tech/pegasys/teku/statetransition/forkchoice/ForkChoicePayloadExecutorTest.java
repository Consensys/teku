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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class ForkChoicePayloadExecutorTest {

  private final Spec spec = TestSpecFactory.createMinimalMerge();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final SafeFuture<ExecutePayloadResult> executionResult = new SafeFuture<>();
  private final ExecutionEngineChannel executionEngine = mock(ExecutionEngineChannel.class);
  private EventThread forkChoiceExecutor = new InlineEventThread();

  private final StateAndBlockSummary chainHead =
      StateAndBlockSummary.create(dataStructureUtil.randomBeaconState());
  private final SignedBeaconBlock block =
      dataStructureUtil.randomSignedBeaconBlock(0, chainHead.getRoot());

  @BeforeEach
  void setUp() {
    when(recentChainData.getForkChoiceStrategy())
        .thenAnswer(
            invocation -> {
              forkChoiceExecutor.checkOnEventThread();
              return Optional.of(forkChoiceStrategy);
            });
    when(recentChainData.getChainHead()).thenReturn(Optional.of(chainHead));
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(chainHead.getRoot()));

    when(executionEngine.executePayload(any())).thenReturn(executionResult);
  }

  @Test
  void optimisticallyExecute_shouldSendToExecutionEngineAndReturnTrue() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    final boolean result = payloadExecutor.optimisticallyExecute(payload);
    verify(executionEngine).executePayload(payload);
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnBlockImportResultImmediatelyWhenNotSuccessful() {
    forkChoiceExecutor = mock(EventThread.class);
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    payloadExecutor.optimisticallyExecute(dataStructureUtil.randomExecutionPayload());

    final BlockImportResult blockImportResult =
        BlockImportResult.failedStateTransition(new RuntimeException("Bad block"));
    final SafeFuture<BlockImportResult> result = payloadExecutor.combine(blockImportResult);
    assertThat(result).isCompletedWithValue(blockImportResult);
  }

  @Test
  void shouldReturnBlockImportResultImmediatelyWhenNoPayloadExecuted() {
    forkChoiceExecutor = mock(EventThread.class);
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();

    final BlockImportResult blockImportResult = BlockImportResult.successful(block);
    final SafeFuture<BlockImportResult> result = payloadExecutor.combine(blockImportResult);
    assertThat(result).isCompletedWithValue(blockImportResult);
    verifyChainHeadUpdated(blockImportResult);
  }

  @Test
  void shouldCombineWithValidPayloadExecution() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    payloadExecutor.optimisticallyExecute(payload);

    final BlockImportResult blockImportResult = BlockImportResult.successful(block);
    final SafeFuture<BlockImportResult> result = payloadExecutor.combine(blockImportResult);
    assertThat(result).isNotCompleted();
    verifyChainHeadNotUpdated(blockImportResult);

    executionResult.complete(
        new ExecutePayloadResult(ExecutionPayloadStatus.VALID, Optional.empty(), Optional.empty()));

    assertThat(result).isCompletedWithValue(blockImportResult);
    verify(forkChoiceStrategy)
        .onExecutionPayloadResult(block.getRoot(), ExecutionPayloadStatus.VALID);
    verifyChainHeadUpdated(blockImportResult);
  }

  @Test
  void shouldCombineWithInvalidPayloadExecution() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    payloadExecutor.optimisticallyExecute(payload);

    final BlockImportResult blockImportResult = BlockImportResult.successful(block);
    final SafeFuture<BlockImportResult> result = payloadExecutor.combine(blockImportResult);
    assertThat(result).isNotCompleted();
    verifyChainHeadNotUpdated(blockImportResult);

    executionResult.complete(
        new ExecutePayloadResult(
            ExecutionPayloadStatus.INVALID, Optional.empty(), Optional.empty()));

    assertThat(result).isCompleted();
    verify(forkChoiceStrategy)
        .onExecutionPayloadResult(block.getRoot(), ExecutionPayloadStatus.INVALID);
    final BlockImportResult finalImportResult = result.join();
    assertThat(finalImportResult.isSuccessful()).isFalse();
    verifyChainHeadNotUpdated(finalImportResult);
  }

  @Test
  void shouldCombineWithSyncingPayloadExecution() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    payloadExecutor.optimisticallyExecute(payload);

    final BlockImportResult blockImportResult = BlockImportResult.successful(block);
    final SafeFuture<BlockImportResult> result = payloadExecutor.combine(blockImportResult);
    assertThat(result).isNotCompleted();
    verifyChainHeadNotUpdated(blockImportResult);

    executionResult.complete(
        new ExecutePayloadResult(
            ExecutionPayloadStatus.SYNCING, Optional.empty(), Optional.empty()));

    assertThat(result).isCompletedWithValue(blockImportResult);
    verify(forkChoiceStrategy)
        .onExecutionPayloadResult(block.getRoot(), ExecutionPayloadStatus.SYNCING);
    verifyChainHeadNotUpdated(blockImportResult);
  }

  private void verifyChainHeadNotUpdated(final BlockImportResult blockImportResult) {
    verify(recentChainData, never()).updateHead(any(), any());
    assertThat(blockImportResult.isBlockOnCanonicalChain()).isFalse();
  }

  private void verifyChainHeadUpdated(final BlockImportResult blockImportResult) {
    verify(recentChainData).updateHead(block.getRoot(), block.getSlot());
    assertThat(blockImportResult.isBlockOnCanonicalChain()).isTrue();
  }

  private ForkChoicePayloadExecutor createPayloadExecutor() {
    return new ForkChoicePayloadExecutor(
        recentChainData, forkChoiceExecutor, block, executionEngine);
  }
}
