/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.ethereum.executionclient.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.InvalidRemoteResponseException;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineExchangeTransitionConfigurationV1Test {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EngineExchangeTransitionConfigurationV1 jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EngineExchangeTransitionConfigurationV1(executionEngineClient);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_exchangeTransitionConfiguration");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(1);
    assertThat(jsonRpcMethod.getVersionedName())
        .isEqualTo("engine_exchangeTransitionConfigurationV1");
  }

  @Test
  public void transitionConfigurationParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final TransitionConfiguration consensusTransitionConfiguration = dummyTransitionConfiguration();
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.exchangeTransitionConfiguration(
            TransitionConfigurationV1.fromInternalTransitionConfiguration(
                consensusTransitionConfiguration)))
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(consensusTransitionConfiguration).build();

    assertThat(jsonRpcMethod.execute(params))
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(InvalidRemoteResponseException.class)
        .withMessageContaining(
            "Invalid remote response from the execution client: %s", errorResponseFromClient);
  }

  @Test
  public void shouldCallExchangeTransitionConfigurationV1() {
    final TransitionConfiguration consensusTransitionConfiguration = dummyTransitionConfiguration();
    final TransitionConfiguration executionTransitionConfiguration = dummyTransitionConfiguration();

    when(executionEngineClient.exchangeTransitionConfiguration(any()))
        .thenReturn(dummySuccessfulResponse(executionTransitionConfiguration));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(consensusTransitionConfiguration).build();

    assertThat(jsonRpcMethod.execute(params))
        .isCompletedWithValue(executionTransitionConfiguration);

    verify(executionEngineClient)
        .exchangeTransitionConfiguration(
            TransitionConfigurationV1.fromInternalTransitionConfiguration(
                consensusTransitionConfiguration));
    verifyNoMoreInteractions(executionEngineClient);
  }

  private TransitionConfiguration dummyTransitionConfiguration() {
    return new TransitionConfiguration(
        dataStructureUtil.randomUInt256(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64());
  }

  private SafeFuture<Response<TransitionConfigurationV1>> dummySuccessfulResponse(
      TransitionConfiguration transitionConfiguration) {
    return SafeFuture.completedFuture(
        new Response<>(
            TransitionConfigurationV1.fromInternalTransitionConfiguration(
                transitionConfiguration)));
  }

  private SafeFuture<Response<TransitionConfigurationV1>> dummyFailedResponse(
      final String errorMessage) {
    return SafeFuture.completedFuture(Response.withErrorMessage(errorMessage));
  }
}
