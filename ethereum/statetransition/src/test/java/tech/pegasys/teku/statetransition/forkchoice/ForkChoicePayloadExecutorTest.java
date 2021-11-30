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
import static tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus.SYNCING;

import java.io.IOException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
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
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class ForkChoicePayloadExecutorTest {

  private final Spec spec = TestSpecFactory.createMinimalMerge();
  private final SchemaDefinitionsMerge schemaDefinitionsMerge =
      spec.getGenesisSchemaDefinitions().toVersionMerge().orElseThrow();
  private final ExecutionPayload defaultPayload =
      schemaDefinitionsMerge.getExecutionPayloadSchema().getDefault();
  private final ExecutionPayloadHeader defaultPayloadHeader =
      schemaDefinitionsMerge.getExecutionPayloadHeaderSchema().getDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final SafeFuture<ExecutePayloadResult> executionResult = new SafeFuture<>();
  private final ExecutionEngineChannel executionEngine = mock(ExecutionEngineChannel.class);
  private final ExecutionPayloadHeader payloadHeader =
      dataStructureUtil.randomExecutionPayloadHeader();
  private final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
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
    final boolean result = payloadExecutor.optimisticallyExecute(payloadHeader, payload);
    verify(executionEngine).executePayload(payload);
    assertThat(result).isTrue();
  }

  @Test
  void optimisticallyExecute_shouldTreatErrorFromExecutionEngineAsSyncing() {
    when(executionEngine.executePayload(any()))
        .thenReturn(SafeFuture.failedFuture(new IOException("Boom")));
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean result = payloadExecutor.optimisticallyExecute(payloadHeader, payload);
    verify(executionEngine).executePayload(payload);
    assertThat(result).isTrue();

    final BlockImportResult blockImportResult = BlockImportResult.successful(block);
    final SafeFuture<BlockImportResult> combinedResult = payloadExecutor.combine(blockImportResult);
    assertThat(combinedResult).isCompletedWithValue(blockImportResult);
    verify(forkChoiceStrategy).onExecutionPayloadResult(block.getRoot(), SYNCING);
  }

  @Test
  void optimisticallyExecute_shouldNotExecuteDefaultPayload() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean result = payloadExecutor.optimisticallyExecute(payloadHeader, defaultPayload);
    verify(executionEngine, never()).executePayload(any());
    assertThat(result).isTrue();
  }

  /**
   * The details of how we validate the merge block are all handled by {@link
   * tech.pegasys.teku.spec.logic.versions.merge.helpers.MergeTransitionHelpers} so we don't want to
   * retest all that here. Instead, we check that those extra checks started (the call to {@link
   * ExecutionEngineChannel#getPowBlock(Bytes32)}) and check that we didn't execute the payload as
   * MergeTransitionHelpers will take care of that after the TTD validations are done.
   *
   * <p>Since the future we return from getPowBlock is never completed we never complete the
   * validations which saves us a bunch of mocking.
   */
  @Test
  void optimisticallyExecute_shouldValidateMergeBlockWhenThisIsTheMergeBlock() {
    when(executionEngine.getPowBlock(payload.getParentHash())).thenReturn(new SafeFuture<>());
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean result = payloadExecutor.optimisticallyExecute(defaultPayloadHeader, payload);

    // Should defer execution until it has checked the terminal difficulty so we expect getPoWBlock
    verify(executionEngine).getPowBlock(payload.getParentHash());
    verify(executionEngine, never()).executePayload(payload);
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnBlockImportResultImmediatelyWhenNotSuccessful() {
    forkChoiceExecutor = mock(EventThread.class);
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    payloadExecutor.optimisticallyExecute(payloadHeader, payload);

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
    payloadExecutor.optimisticallyExecute(payloadHeader, payload);

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
    payloadExecutor.optimisticallyExecute(payloadHeader, payload);

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
    payloadExecutor.optimisticallyExecute(payloadHeader, payload);

    final BlockImportResult blockImportResult = BlockImportResult.successful(block);
    final SafeFuture<BlockImportResult> result = payloadExecutor.combine(blockImportResult);
    assertThat(result).isNotCompleted();
    verifyChainHeadNotUpdated(blockImportResult);

    executionResult.complete(new ExecutePayloadResult(SYNCING, Optional.empty(), Optional.empty()));

    assertThat(result).isCompletedWithValue(blockImportResult);
    verify(forkChoiceStrategy).onExecutionPayloadResult(block.getRoot(), SYNCING);
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
        spec, recentChainData, forkChoiceExecutor, block, executionEngine);
  }
}
