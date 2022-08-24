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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.executionlayer.PayloadStatus.VALID;

import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
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
  private final SafeFuture<PayloadStatus> executionResult = new SafeFuture<>();
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final MergeTransitionBlockValidator transitionValidator =
      mock(MergeTransitionBlockValidator.class);
  private final ExecutionPayloadHeader payloadHeader =
      dataStructureUtil.randomExecutionPayloadHeader();
  private final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
  private final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(0);

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.depositSignatureVerifier = BLSSignatureVerifier.NO_OP;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.depositSignatureVerifier =
        AbstractBlockProcessor.DEFAULT_DEPOSIT_SIGNATURE_VERIFIER;
  }

  @BeforeEach
  void setUp() {
    when(executionLayer.engineNewPayload(any())).thenReturn(executionResult);
  }

  @Test
  void optimisticallyExecute_shouldSendToExecutionEngineAndReturnTrue() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean result = payloadExecutor.optimisticallyExecute(payloadHeader, payload);
    verify(executionLayer).engineNewPayload(payload);
    assertThat(result).isTrue();
  }

  @Test
  void optimisticallyExecute_shouldNotExecuteDefaultPayload() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean result = payloadExecutor.optimisticallyExecute(payloadHeader, defaultPayload);
    verify(executionLayer, never()).engineNewPayload(any());
    assertThat(result).isTrue();
    assertThat(payloadExecutor.getExecutionResult())
        .isCompletedWithValue(new PayloadValidationResult(PayloadStatus.VALID));
  }

  @Test
  void optimisticallyExecute_shouldValidateMergeBlockWhenThisIsTheMergeBlock() {
    when(executionLayer.engineNewPayload(payload)).thenReturn(SafeFuture.completedFuture(VALID));
    when(executionLayer.eth1GetPowBlock(payload.getParentHash())).thenReturn(new SafeFuture<>());
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean result = payloadExecutor.optimisticallyExecute(defaultPayloadHeader, payload);

    // Should execute first and then begin validation of the transition block conditions.
    verify(executionLayer).engineNewPayload(payload);
    verify(transitionValidator).verifyTransitionBlock(defaultPayloadHeader, block);
    assertThat(result).isTrue();
  }

  @Test
  void optimisticallyExecute_shouldReturnFailedExecutionOnMergeBlockWhenELOfflineAtExecution() {
    when(executionLayer.engineNewPayload(payload)).thenReturn(SafeFuture.failedFuture(new Error()));
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean execution = payloadExecutor.optimisticallyExecute(defaultPayloadHeader, payload);

    // Should not attempt to validate transition conditions because execute payload failed
    verify(transitionValidator, never()).verifyTransitionBlock(defaultPayloadHeader, block);
    verify(executionLayer).engineNewPayload(payload);
    assertThat(execution).isTrue();
    assertThat(payloadExecutor.getExecutionResult())
        .isCompletedWithValueMatching(result -> result.getStatus().hasFailedExecution());
  }

  @Test
  void
      optimisticallyExecute_shouldReturnFailedExecutionOnMergeBlockWhenELGoesOfflineAfterExecution() {
    when(executionLayer.engineNewPayload(payload)).thenReturn(SafeFuture.completedFuture(VALID));
    when(transitionValidator.verifyTransitionBlock(defaultPayloadHeader, block))
        .thenReturn(SafeFuture.failedFuture(new Error()));
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean execution = payloadExecutor.optimisticallyExecute(defaultPayloadHeader, payload);

    verify(transitionValidator).verifyTransitionBlock(defaultPayloadHeader, block);
    verify(executionLayer).engineNewPayload(payload);
    assertThat(execution).isTrue();
    assertThat(payloadExecutor.getExecutionResult())
        .isCompletedWithValueMatching(result -> result.getStatus().hasFailedExecution());
  }

  @Test
  void optimisticallyExecute_shouldNotVerifyTransitionIfExecutePayloadIsInvalid() {
    final PayloadStatus expectedResult =
        PayloadStatus.invalid(Optional.empty(), Optional.of("Nope"));
    when(executionLayer.engineNewPayload(payload))
        .thenReturn(SafeFuture.completedFuture(expectedResult));
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    final boolean execution = payloadExecutor.optimisticallyExecute(defaultPayloadHeader, payload);

    verify(executionLayer).engineNewPayload(payload);
    verify(transitionValidator, never()).verifyTransitionBlock(defaultPayloadHeader, block);
    assertThat(execution).isTrue();
    assertThat(payloadExecutor.getExecutionResult())
        .isCompletedWithValue(new PayloadValidationResult(expectedResult));
  }

  @Test
  void shouldReturnValidImmediatelyWhenNoPayloadExecuted() {
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();

    final SafeFuture<PayloadValidationResult> result = payloadExecutor.getExecutionResult();
    assertThat(result).isCompletedWithValue(PayloadValidationResult.VALID);
  }

  @Test
  void shouldReturnExecutionResultWhenExecuted() {
    when(transitionValidator.verifyTransitionBlock(payloadHeader, block))
        .thenReturn(SafeFuture.completedFuture(PayloadValidationResult.VALID));
    final ForkChoicePayloadExecutor payloadExecutor = createPayloadExecutor();
    payloadExecutor.optimisticallyExecute(payloadHeader, payload);

    final SafeFuture<PayloadValidationResult> result = payloadExecutor.getExecutionResult();
    assertThat(result).isNotCompleted();

    this.executionResult.complete(VALID);

    assertThat(result).isCompletedWithValue(PayloadValidationResult.VALID);
  }

  private ForkChoicePayloadExecutor createPayloadExecutor() {
    return new ForkChoicePayloadExecutor(block, executionLayer, transitionValidator);
  }
}
