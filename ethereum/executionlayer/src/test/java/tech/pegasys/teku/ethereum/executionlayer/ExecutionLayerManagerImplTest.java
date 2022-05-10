/*
 * Copyright 2022 ConsenSys AG.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.ethereum.executionclient.ExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.GenericBuilderStatus;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class ExecutionLayerManagerImplTest {

  private final ExecutionEngineClient executionEngineClient =
      Mockito.mock(ExecutionEngineClient.class);

  private final ExecutionBuilderClient executionBuilderClient =
      Mockito.mock(ExecutionBuilderClient.class);

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();

  private final EventLogger eventLogger = mock(EventLogger.class);

  private final ExecutionLayerManagerImpl underTest = createExecutionLayerChannelImpl(true);

  @Test
  public void builderShouldNotBeAvailableWhenBuilderNotEnabled() {
    ExecutionLayerManagerImpl noBuilderEnabled = createExecutionLayerChannelImpl(false);

    // trigger update of builder status (should not do anything since builder is not enabled)
    noBuilderEnabled.onSlot(UInt64.ONE);

    assertThat(noBuilderEnabled.isBuilderAvailable()).isFalse();
    verifyNoInteractions(executionBuilderClient);
    verifyNoInteractions(eventLogger);
  }

  @Test
  public void builderShouldBeAvailableWhenBuilderIsOperatingNormally() {
    SafeFuture<Response<GenericBuilderStatus>> builderClientResponse =
        SafeFuture.completedFuture(new Response<>(GenericBuilderStatus.OK));

    updateBuilderStatus(builderClientResponse);

    assertThat(underTest.isBuilderAvailable()).isTrue();
    verifyNoInteractions(eventLogger);
  }

  @Test
  public void builderShouldNotBeAvailableWhenBuilderIsNotOperatingNormally() {
    SafeFuture<Response<GenericBuilderStatus>> builderClientResponse =
        SafeFuture.completedFuture(new Response<>("oops"));

    updateBuilderStatus(builderClientResponse);

    assertThat(underTest.isBuilderAvailable()).isFalse();
    verify(eventLogger).executionBuilderIsOffline("oops");
  }

  @Test
  public void builderShouldNotBeAvailableWhenBuilderStatusCallFails() {
    SafeFuture<Response<GenericBuilderStatus>> builderClientResponse =
        SafeFuture.failedFuture(new Throwable("oops"));

    updateBuilderStatus(builderClientResponse);

    assertThat(underTest.isBuilderAvailable()).isFalse();
    verify(eventLogger).executionBuilderIsOffline("oops");
  }

  @Test
  public void builderAvailabilityIsUpdatedOnSlotEventAndLoggedAdequately() {
    // Initially builder should be available
    assertThat(underTest.isBuilderAvailable()).isTrue();

    // Given builder status is ok
    updateBuilderStatus(SafeFuture.completedFuture(new Response<>(GenericBuilderStatus.OK)));

    // Then
    assertThat(underTest.isBuilderAvailable()).isTrue();
    verifyNoInteractions(eventLogger);

    // Given builder status is not ok
    updateBuilderStatus(SafeFuture.completedFuture(new Response<>("oops")));

    // Then
    assertThat(underTest.isBuilderAvailable()).isFalse();
    verify(eventLogger).executionBuilderIsOffline("oops");

    // Given builder status is back to being ok
    updateBuilderStatus(SafeFuture.completedFuture(new Response<>(GenericBuilderStatus.OK)));

    // Then
    assertThat(underTest.isBuilderAvailable()).isTrue();
    verify(eventLogger).executionBuilderIsBackOnline();
  }

  private ExecutionLayerManagerImpl createExecutionLayerChannelImpl(boolean builderEnabled) {
    return new ExecutionLayerManagerImpl(
        executionEngineClient,
        builderEnabled ? Optional.of(executionBuilderClient) : Optional.empty(),
        spec,
        eventLogger);
  }

  private void updateBuilderStatus(
      SafeFuture<Response<GenericBuilderStatus>> builderClientResponse) {
    when(executionBuilderClient.status()).thenReturn(builderClientResponse);
    // trigger update of the builder status
    underTest.onSlot(UInt64.ONE);
  }
}
