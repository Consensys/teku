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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class ExecutionLayerManagerImplTest {

  private final ExecutionClientHandler executionClientHandler = mock(ExecutionClientHandler.class);

  private final BuilderClient builderClient = Mockito.mock(BuilderClient.class);

  private Spec spec = TestSpecFactory.createMinimalBellatrix();

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  private final EventLogger eventLogger = mock(EventLogger.class);

  private final BuilderCircuitBreaker builderCircuitBreaker = mock(BuilderCircuitBreaker.class);
  private ExecutionLayerManagerImpl executionLayerManager =
      createExecutionLayerChannelImpl(true, false);

  @Test
  public void builderShouldNotBeAvailableWhenBuilderNotEnabled() {
    executionLayerManager = createExecutionLayerChannelImpl(false, false);

    // trigger update of builder status (should not do anything since builder is not enabled)
    executionLayerManager.onSlot(UInt64.ONE);

    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isFalse();
    verifyNoInteractions(builderClient);
    verifyNoInteractions(eventLogger);
  }

  @Test
  public void builderShouldBeAvailableWhenBuilderIsOperatingNormally() {
    final SafeFuture<Response<Void>> builderClientResponse =
        SafeFuture.completedFuture(Response.withNullPayload());

    updateBuilderStatus(builderClientResponse);

    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isTrue();
    verifyNoInteractions(eventLogger);
  }

  @Test
  public void builderShouldNotBeAvailableWhenBuilderIsNotOperatingNormally() {
    final SafeFuture<Response<Void>> builderClientResponse =
        SafeFuture.completedFuture(Response.withErrorMessage("oops"));

    updateBuilderStatus(builderClientResponse);

    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isFalse();
    verify(eventLogger).builderIsNotAvailable("oops");
  }

  @Test
  public void builderShouldNotBeAvailableWhenBuilderStatusCallFails() {
    final SafeFuture<Response<Void>> builderClientResponse =
        SafeFuture.failedFuture(new Throwable("oops"));

    updateBuilderStatus(builderClientResponse);

    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isFalse();
    verify(eventLogger).builderIsNotAvailable("oops");
  }

  @Test
  public void builderAvailabilityIsUpdatedOnSlotEventAndLoggedAdequately() {
    // Initially builder should be available
    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isTrue();

    // Given builder status is ok
    updateBuilderStatus(SafeFuture.completedFuture(Response.withNullPayload()));

    // Then
    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isTrue();
    verifyNoInteractions(eventLogger);

    // Given builder status is not ok
    updateBuilderStatus(SafeFuture.completedFuture(Response.withErrorMessage("oops")));

    // Then
    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isFalse();
    verify(eventLogger).builderIsNotAvailable("oops");

    // Given builder status is back to being ok
    updateBuilderStatus(SafeFuture.completedFuture(Response.withNullPayload()));

    // Then
    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isTrue();
    verify(eventLogger).builderIsAvailable();
  }

  // FIXME: restore removed tests

  private ExecutionLayerManagerImpl createExecutionLayerChannelImpl(
      final boolean builderEnabled, final boolean builderValidatorEnabled) {
    when(builderCircuitBreaker.isEngaged(any())).thenReturn(false);
    return ExecutionLayerManagerImpl.create(
        eventLogger,
        executionClientHandler,
        builderEnabled ? Optional.of(builderClient) : Optional.empty(),
        spec,
        stubMetricsSystem,
        builderValidatorEnabled
            ? new BuilderBidValidatorImpl(eventLogger)
            : BuilderBidValidator.NOOP,
        builderCircuitBreaker,
        BlobsBundleValidator.NOOP);
  }

  private void updateBuilderStatus(final SafeFuture<Response<Void>> builderClientResponse) {
    updateBuilderStatus(builderClientResponse, UInt64.ONE);
  }

  private void updateBuilderStatus(SafeFuture<Response<Void>> builderClientResponse, UInt64 slot) {
    when(builderClient.status()).thenReturn(builderClientResponse);
    // trigger update of the builder status
    executionLayerManager.onSlot(slot);
  }
}
