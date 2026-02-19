/*
 * Copyright Consensys Software Inc., 2026
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
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
  private final ReceivedExecutionPayloadEventsChannel
      receivedExecutionPayloadEventsChannelPublisher =
          mock(ReceivedExecutionPayloadEventsChannel.class);

  private final DefaultExecutionPayloadManager executionPayloadManager =
      new DefaultExecutionPayloadManager(
          asyncRunner,
          executionPayloadGossipValidator,
          forkChoice,
          executionLayer,
          receivedExecutionPayloadEventsChannelPublisher);

  private final SignedExecutionPayloadEnvelope signedExecutionPayload =
      dataStructureUtil.randomSignedExecutionPayloadEnvelope(42);

  private final ExecutionPayloadImportResult successfulImportResult =
      ExecutionPayloadImportResult.successful(signedExecutionPayload);

  @Test
  public void shouldValidateAndImport() {
    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    when(forkChoice.onExecutionPayload(signedExecutionPayload, executionLayer))
        .thenReturn(SafeFuture.completedFuture(successfulImportResult));

    final SafeFuture<InternalValidationResult> resultFuture =
        executionPayloadManager.validateAndImportExecutionPayload(signedExecutionPayload);

    try (final LogCaptor logCaptor = LogCaptor.forClass(DefaultExecutionPayloadManager.class)) {
      asyncRunner.executeDueActions();
      logCaptor.assertDebugLog("Successfully imported execution payload");
    }

    assertThat(resultFuture).isCompletedWithValue(InternalValidationResult.ACCEPT);

    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(signedExecutionPayload);

    // verify the `beacon_block_root` is cached
    assertThat(
            executionPayloadManager.isExecutionPayloadRecentlySeen(
                signedExecutionPayload.getBeaconBlockRoot()))
        .isTrue();
  }

  @Test
  public void shouldNotImportIfValidationFails() {
    final InternalValidationResult rejectedResult = InternalValidationResult.reject("oopsy");
    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(rejectedResult));

    final SafeFuture<InternalValidationResult> resultFuture =
        executionPayloadManager.validateAndImportExecutionPayload(signedExecutionPayload);

    asyncRunner.executeDueActions();

    verifyNoInteractions(forkChoice);
    assertThat(resultFuture).isCompletedWithValue(rejectedResult);

    // `beacon_block_root` should not be cached when gossip validation fails
    assertThat(
            executionPayloadManager.isExecutionPayloadRecentlySeen(
                signedExecutionPayload.getBeaconBlockRoot()))
        .isFalse();
  }

  @Test
  public void shouldHandleInternalErrorsWhileImporting() {
    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final IllegalStateException exception = new IllegalStateException("oopsy");

    when(forkChoice.onExecutionPayload(signedExecutionPayload, executionLayer))
        .thenThrow(exception);

    final SafeFuture<InternalValidationResult> resultFuture =
        executionPayloadManager.validateAndImportExecutionPayload(signedExecutionPayload);

    try (final LogCaptor logCaptor = LogCaptor.forClass(DefaultExecutionPayloadManager.class)) {
      asyncRunner.executeDueActions();
      logCaptor.assertErrorLog("Internal error while importing execution payload");
    }

    assertThat(resultFuture).isCompletedWithValue(InternalValidationResult.ACCEPT);

    // verify the `beacon_block_root` is cached
    assertThat(
            executionPayloadManager.isExecutionPayloadRecentlySeen(
                signedExecutionPayload.getBeaconBlockRoot()))
        .isTrue();
  }
}
