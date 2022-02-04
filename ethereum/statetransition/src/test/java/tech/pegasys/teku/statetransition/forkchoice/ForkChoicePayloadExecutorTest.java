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

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoicePayloadExecutorTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final SchemaDefinitionsBellatrix schemaDefinitionsBellatrix =
      spec.getGenesisSchemaDefinitions().toVersionBellatrix().orElseThrow();
  private final ExecutionPayload defaultPayload =
      schemaDefinitionsBellatrix.getExecutionPayloadSchema().getDefault();
  private final ExecutionPayloadHeader defaultPayloadHeader =
      schemaDefinitionsBellatrix.getExecutionPayloadHeaderSchema().getDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SafeFuture<ExecutePayloadResult> executionResult = new SafeFuture<>();
  private final ExecutionEngineChannel executionEngine = mock(ExecutionEngineChannel.class);
  private final ExecutionPayloadHeader payloadHeader =
      dataStructureUtil.randomExecutionPayloadHeader();
  private final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();

  private final StateAndBlockSummary chainHead =
      StateAndBlockSummary.create(dataStructureUtil.randomBeaconState());
  private final SignedBeaconBlock block =
      dataStructureUtil.randomSignedBeaconBlock(0, chainHead.getRoot());

  @BeforeEach
  void setUp() {
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
  void optimisticallyExecute_shouldNotExecuteDefaultPayload() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean result = payloadExecutor.optimisticallyExecute(payloadHeader, defaultPayload);
    verify(executionEngine, never()).executePayload(any());
    assertThat(result).isTrue();
    assertThat(payloadExecutor.getExecutionResult())
        .isCompletedWithValue(ExecutePayloadResult.VALID);
  }

  /**
   * The details of how we validate the merge block are all handled by {@link
   * BellatrixTransitionHelpers} so we don't want to retest all that here. Instead, we check that
   * those extra checks started (the call to {@link ExecutionEngineChannel#getPowBlock(Bytes32)})
   * and check that we didn't execute the payload as {@link BellatrixTransitionHelpers} will take
   * care of that after the TTD validations are done.
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
  void optimisticallyExecute_shouldReturnFailedExecutionOnMergeBlockWhenELOffline() {
    when(executionEngine.getPowBlock(payload.getParentHash()))
        .thenReturn(SafeFuture.failedFuture(new Error()));
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean execution = payloadExecutor.optimisticallyExecute(defaultPayloadHeader, payload);

    // Should defer execution until it has checked the terminal difficulty so we expect getPoWBlock
    verify(executionEngine).getPowBlock(payload.getParentHash());
    verify(executionEngine, never()).executePayload(payload);
    assertThat(execution).isTrue();
    assertThat(payloadExecutor.getExecutionResult())
        .isCompletedWithValueMatching(ExecutePayloadResult::hasFailedExecution);
    ;
  }

  @Test
  void shouldReturnValidImmediatelyWhenNoPayloadExecuted() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();

    final SafeFuture<ExecutePayloadResult> result = payloadExecutor.getExecutionResult();
    assertThat(result).isCompletedWithValue(ExecutePayloadResult.VALID);
  }

  @Test
  void shouldReturnExecutionResultWhenExecuted() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    payloadExecutor.optimisticallyExecute(payloadHeader, payload);

    final SafeFuture<ExecutePayloadResult> result = payloadExecutor.getExecutionResult();
    assertThat(result).isNotCompleted();

    this.executionResult.complete(ExecutePayloadResult.VALID);

    assertThat(result).isCompletedWithValue(ExecutePayloadResult.VALID);
  }

  private ForkChoicePayloadExecutor createPayloadExecutor() {
    return new ForkChoicePayloadExecutor(spec, block, executionEngine);
  }
}
