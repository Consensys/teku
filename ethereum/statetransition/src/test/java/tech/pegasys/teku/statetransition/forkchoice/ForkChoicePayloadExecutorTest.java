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
import static tech.pegasys.teku.spec.executionengine.PayloadStatus.VALID;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class ForkChoicePayloadExecutorTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final SchemaDefinitionsBellatrix schemaDefinitionsBellatrix =
      spec.getGenesisSchemaDefinitions().toVersionBellatrix().orElseThrow();
  private final ExecutionPayload defaultPayload =
      schemaDefinitionsBellatrix.getExecutionPayloadSchema().getDefault();
  private final ExecutionPayloadHeader defaultPayloadHeader =
      schemaDefinitionsBellatrix.getExecutionPayloadHeaderSchema().getDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SafeFuture<PayloadStatus> executionResult = new SafeFuture<>();
  private final ExecutionEngineChannel executionEngine = mock(ExecutionEngineChannel.class);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final ExecutionPayloadHeader payloadHeader =
      dataStructureUtil.randomExecutionPayloadHeader();
  private final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();

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
    storageSystem.chainUpdater().initializeGenesis(false);
    when(executionEngine.newPayload(any())).thenReturn(executionResult);
    when(executionEngine.getPowBlock(any())).thenReturn(new SafeFuture<>());
  }

  @Test
  void optimisticallyExecute_shouldSendToExecutionEngineAndReturnTrue() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean result = payloadExecutor.optimisticallyExecute(payloadHeader, payload);
    verify(executionEngine).newPayload(payload);
    assertThat(result).isTrue();
  }

  @Test
  void optimisticallyExecute_shouldNotExecuteDefaultPayload() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean result = payloadExecutor.optimisticallyExecute(payloadHeader, defaultPayload);
    verify(executionEngine, never()).newPayload(any());
    assertThat(result).isTrue();
    assertThat(payloadExecutor.getExecutionResult()).isCompletedWithValue(PayloadStatus.VALID);
  }

  /**
   * The details of how we validate the merge block are all handled by {@link
   * BellatrixTransitionHelpers} so we don't want to retest all that here. Instead, we check that
   * those extra checks started (the call to {@link ExecutionEngineChannel#getPowBlock(Bytes32)}).
   *
   * <p>Since the future we return from getPowBlock is never completed we never complete the
   * validations which saves us a bunch of mocking.
   */
  @Test
  void optimisticallyExecute_shouldValidateMergeBlockWhenThisIsTheMergeBlock() {
    when(executionEngine.newPayload(payload)).thenReturn(SafeFuture.completedFuture(VALID));
    when(executionEngine.getPowBlock(payload.getParentHash())).thenReturn(new SafeFuture<>());
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean result = payloadExecutor.optimisticallyExecute(defaultPayloadHeader, payload);

    // Should execute first and then begin validation of the transition block conditions.
    verify(executionEngine).newPayload(payload);
    verify(executionEngine).getPowBlock(payload.getParentHash());
    assertThat(result).isTrue();
  }

  @Test
  void optimisticallyExecute_shouldReturnFailedExecutionOnMergeBlockWhenELOfflineAtExecution() {
    when(executionEngine.newPayload(payload)).thenReturn(SafeFuture.failedFuture(new Error()));
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean execution = payloadExecutor.optimisticallyExecute(defaultPayloadHeader, payload);

    // Should not attempt to validate transition conditions because execute payload failed
    verify(executionEngine, never()).getPowBlock(payload.getParentHash());
    verify(executionEngine).newPayload(payload);
    assertThat(execution).isTrue();
    assertThat(payloadExecutor.getExecutionResult())
        .isCompletedWithValueMatching(PayloadStatus::hasFailedExecution);
  }

  @Test
  void
      optimisticallyExecute_shouldReturnFailedExecutionOnMergeBlockWhenELGoesOfflineAfterExecution() {
    when(executionEngine.newPayload(payload)).thenReturn(SafeFuture.completedFuture(VALID));
    when(executionEngine.getPowBlock(payload.getParentHash()))
        .thenReturn(SafeFuture.failedFuture(new Error()));
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean execution = payloadExecutor.optimisticallyExecute(defaultPayloadHeader, payload);

    verify(executionEngine).getPowBlock(payload.getParentHash());
    verify(executionEngine).newPayload(payload);
    assertThat(execution).isTrue();
    assertThat(payloadExecutor.getExecutionResult())
        .isCompletedWithValueMatching(PayloadStatus::hasFailedExecution);
  }

  @Test
  void optimisticallyExecute_shouldReturnNotVerifyTransitionIfExecutePayloadIsInvalid() {
    final PayloadStatus expectedResult =
        PayloadStatus.invalid(Optional.empty(), Optional.of("Nope"));
    when(executionEngine.newPayload(payload))
        .thenReturn(SafeFuture.completedFuture(expectedResult));
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean execution = payloadExecutor.optimisticallyExecute(defaultPayloadHeader, payload);

    verify(executionEngine).newPayload(payload);
    verify(executionEngine, never()).getPowBlock(payload.getParentHash());
    assertThat(execution).isTrue();
    assertThat(payloadExecutor.getExecutionResult()).isCompletedWithValue(expectedResult);
  }

  @Test
  void shouldReturnValidImmediatelyWhenNoPayloadExecuted() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();

    final SafeFuture<PayloadStatus> result = payloadExecutor.getExecutionResult();
    assertThat(result).isCompletedWithValue(VALID);
  }

  @Test
  void shouldReturnExecutionResultWhenExecuted() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    payloadExecutor.optimisticallyExecute(payloadHeader, payload);

    final SafeFuture<PayloadStatus> result = payloadExecutor.getExecutionResult();
    assertThat(result).isNotCompleted();

    this.executionResult.complete(VALID);

    assertThat(result).isCompletedWithValue(VALID);
  }

  @Test
  void shouldVerifyNonFinalizedAncestorTransitionBlock() {
    final SignedBlockAndState transitionBlock = generateNonfinalizedTransition();
    final SignedBlockAndState chainHead = storageSystem.chainBuilder().getLatestBlockAndState();
    final SignedBlockAndState blockToVerify = storageSystem.chainBuilder().generateNextBlock();

    final ForkChoicePayloadExecutor payloadExecutor =
        createPayloadExecutor(blockToVerify.getBlock());
    final ExecutionPayload newExecutionPayload = getExecutionPayload(blockToVerify);
    when(executionEngine.newPayload(newExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(VALID));

    assertThat(
            storageSystem
                .recentChainData()
                .getForkChoiceStrategy()
                .orElseThrow()
                .getOptimisticallySyncedTransitionBlockRoot(chainHead.getRoot()))
        .isPresent();

    payloadExecutor.optimisticallyExecute(
        chainHead.getState().toVersionBellatrix().orElseThrow().getLatestExecutionPayloadHeader(),
        newExecutionPayload);

    verify(executionEngine).newPayload(newExecutionPayload);
    verify(executionEngine).getPowBlock(getExecutionPayload(transitionBlock).getParentHash());
    assertThat(payloadExecutor.getExecutionResult()).isNotCompleted();
  }

  @Test
  void shouldVerifyFinalizedAncestorTransitionBlock() {
    final SignedBlockAndState transitionBlock = generateFinalizedTransition();
    final BeaconStateBellatrix chainHeadState =
        storageSystem
            .chainBuilder()
            .getLatestBlockAndState()
            .getState()
            .toVersionBellatrix()
            .orElseThrow();

    final SignedBlockAndState blockToVerify = storageSystem.chainBuilder().generateNextBlock();

    final ForkChoicePayloadExecutor payloadExecutor =
        createPayloadExecutor(blockToVerify.getBlock());
    final ExecutionPayload newExecutionPayload = getExecutionPayload(blockToVerify);
    when(executionEngine.newPayload(newExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(VALID));

    assertThat(storageSystem.recentChainData().getStore().getFinalizedOptimisticTransitionPayload())
        .isPresent();

    payloadExecutor.optimisticallyExecute(
        chainHeadState.getLatestExecutionPayloadHeader(), newExecutionPayload);

    verify(executionEngine).newPayload(newExecutionPayload);
    verify(executionEngine).getPowBlock(getExecutionPayload(transitionBlock).getParentHash());
    assertThat(payloadExecutor.getExecutionResult()).isNotCompleted();
  }

  private SignedBlockAndState generateNonfinalizedTransition() {
    storageSystem
        .chainBuilder()
        .generateBlocksUpToSlot(5)
        .forEach(storageSystem.chainUpdater()::saveOptimisticBlock);
    final Bytes32 terminalBlockHash = dataStructureUtil.randomBytes32();
    final SignedBlockAndState transitionBlock =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(6, BlockOptions.create().setTerminalBlockHash(terminalBlockHash));
    storageSystem.chainUpdater().saveOptimisticBlock(transitionBlock);

    storageSystem
        .chainBuilder()
        .generateBlocksUpToSlot(39)
        .forEach(storageSystem.chainUpdater()::saveOptimisticBlock);
    final SignedBlockAndState chainHead = storageSystem.chainBuilder().generateBlockAtSlot(40);
    storageSystem.chainUpdater().saveOptimisticBlock(chainHead);
    return transitionBlock;
  }

  private SignedBlockAndState generateFinalizedTransition() {
    final SignedBlockAndState transitionBlock = generateNonfinalizedTransition();

    // Finalize between transition block and chain head
    storageSystem.chainUpdater().finalizeEpoch(1);
    return transitionBlock;
  }

  private ExecutionPayload getExecutionPayload(final SignedBlockAndState blockToVerify) {
    return blockToVerify
        .getBlock()
        .getMessage()
        .getBody()
        .getOptionalExecutionPayload()
        .orElseThrow();
  }

  private ForkChoicePayloadExecutor createPayloadExecutor() {
    return createPayloadExecutor(storageSystem.chainBuilder().generateNextBlock().getBlock());
  }

  private ForkChoicePayloadExecutor createPayloadExecutor(final SignedBeaconBlock block) {
    return new ForkChoicePayloadExecutor(
        spec, storageSystem.recentChainData(), block, executionEngine);
  }
}
