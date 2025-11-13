/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult.FailureReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

class DefaultExecutionPayloadManagerTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final ExecutionPayloadGossipValidator executionPayloadGossipValidator =
      mock(ExecutionPayloadGossipValidator.class);
  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);

  private final DefaultExecutionPayloadManager executionPayloadManager =
      new DefaultExecutionPayloadManager(
          asyncRunner, executionPayloadGossipValidator, forkChoice, executionLayer);

  private final SignedExecutionPayloadEnvelope signedExecutionPayload =
      dataStructureUtil.randomSignedExecutionPayloadEnvelope(42);

  private final ExecutionPayloadImportResult successfulImportResult =
      ExecutionPayloadImportResult.successful(signedExecutionPayload);

  @Test
  public void shouldImport() {
    when(forkChoice.onExecutionPayload(signedExecutionPayload, executionLayer))
        .thenReturn(SafeFuture.completedFuture(successfulImportResult));

    final SafeFuture<ExecutionPayloadImportResult> resultFuture =
        executionPayloadManager.importExecutionPayload(signedExecutionPayload);

    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(successfulImportResult);

    // verify the `beacon_block_root` is cached
    assertThat(
            executionPayloadManager.isExecutionPayloadRecentlySeen(
                signedExecutionPayload.getMessage().getBeaconBlockRoot()))
        .isTrue();
  }

  @Test
  public void shouldHandleInternalErrors() {
    final IllegalStateException exception = new IllegalStateException("oopsy");

    when(forkChoice.onExecutionPayload(signedExecutionPayload, executionLayer))
        .thenThrow(exception);

    final SafeFuture<ExecutionPayloadImportResult> resultFuture =
        executionPayloadManager.importExecutionPayload(signedExecutionPayload);

    asyncRunner.executeDueActions();

    final ExecutionPayloadImportResult result = safeJoin(resultFuture);

    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getFailureReason()).isEqualTo(FailureReason.INTERNAL_ERROR);
    assertThat(result.getFailureCause())
        .hasValueSatisfying(cause -> assertThat(cause).hasCause(exception));
  }

  @Test
  public void onAcceptedGossipExecutionPayload_shouldImport() {
    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    when(forkChoice.onExecutionPayload(signedExecutionPayload, executionLayer))
        .thenReturn(SafeFuture.completedFuture(successfulImportResult));

    final SafeFuture<InternalValidationResult> resultFuture =
        executionPayloadManager.validateAndImportExecutionPayload(
            signedExecutionPayload, Optional.empty());

    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(InternalValidationResult.ACCEPT);

    verify(forkChoice).onExecutionPayload(signedExecutionPayload, executionLayer);

    // verify the `beacon_block_root` is cached
    assertThat(
            executionPayloadManager.isExecutionPayloadRecentlySeen(
                signedExecutionPayload.getMessage().getBeaconBlockRoot()))
        .isTrue();
  }
}
