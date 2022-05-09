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
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.ethereum.executionclient.ExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.GenericBuilderStatus;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.BuilderStatus;
import tech.pegasys.teku.spec.datastructures.execution.BuilderStatus.Status;

class ExecutionLayerChannelImplTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();

  private final ExecutionEngineClient executionEngineClient =
      Mockito.mock(ExecutionEngineClient.class);

  private final ExecutionBuilderClient executionBuilderClient =
      Mockito.mock(ExecutionBuilderClient.class);

  private final ExecutionLayerChannelImpl underTest = createExecutionLayerChannelImpl(true);

  @Test
  public void builderStatusShouldReturnNullWhenBuilderNotEnabled() {
    ExecutionLayerChannelImpl noBuilderEnabled = createExecutionLayerChannelImpl(false);

    SafeFuture<BuilderStatus> builderStatus = noBuilderEnabled.builderStatus();

    assertThat(builderStatus).isCompletedWithValue(null);
  }

  @Test
  public void builderStatusShouldBeOkWhenBuilderIsOperatingNormally() {
    SafeFuture<Response<GenericBuilderStatus>> builderClientResponse =
        SafeFuture.completedFuture(new Response<>(GenericBuilderStatus.OK));

    when(executionBuilderClient.status()).thenReturn(builderClientResponse);

    BuilderStatus builderStatus = SafeFutureAssert.safeJoin(underTest.builderStatus());

    assertThat(builderStatus.getStatus()).isEqualTo(Status.OK);
    assertThat(builderStatus.getErrorMessage()).isNull();
  }

  @Test
  public void builderStatusShouldBeFailedWhenBuilderIsNotOperatingNormally() {
    SafeFuture<Response<GenericBuilderStatus>> builderClientResponse =
        SafeFuture.completedFuture(new Response<>("oops"));

    when(executionBuilderClient.status()).thenReturn(builderClientResponse);

    BuilderStatus builderStatus = SafeFutureAssert.safeJoin(underTest.builderStatus());

    assertThat(builderStatus.getStatus()).isNull();
    assertThat(builderStatus.getErrorMessage()).isEqualTo("oops");
  }

  @Test
  public void builderStatusShouldBeFailedWhenBuilderStatusCallFails() {
    SafeFuture<Response<GenericBuilderStatus>> builderClientResponse =
        SafeFuture.failedFuture(new Throwable("oops"));

    when(executionBuilderClient.status()).thenReturn(builderClientResponse);

    BuilderStatus builderStatus = SafeFutureAssert.safeJoin(underTest.builderStatus());

    assertThat(builderStatus.getStatus()).isNull();
    assertThat(builderStatus.getErrorMessage()).isEqualTo("java.lang.Throwable: oops");
  }

  private ExecutionLayerChannelImpl createExecutionLayerChannelImpl(boolean builderEnabled) {
    return new ExecutionLayerChannelImpl(
        executionEngineClient,
        builderEnabled ? Optional.of(executionBuilderClient) : Optional.empty(),
        spec);
  }
}
