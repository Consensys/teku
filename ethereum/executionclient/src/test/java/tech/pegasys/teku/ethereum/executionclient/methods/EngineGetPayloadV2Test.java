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
import static org.mockito.ArgumentMatchers.eq;
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
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV2Response;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineGetPayloadV2Test {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EngineGetPayloadV2 jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EngineGetPayloadV2(executionEngineClient, spec);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_getPayload");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(2);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_getPayloadV2");
  }

  @Test
  public void executionPayloadContextParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void slotParamIsRequired() {
    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 1");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.getPayloadV2(any()))
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).add(UInt64.ZERO).build();

    assertThat(jsonRpcMethod.execute(params))
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(InvalidRemoteResponseException.class)
        .withMessageContaining(
            "Invalid remote response from the execution client: %s", errorResponseFromClient);
  }

  @Test
  public void shouldCallGetPayloadV2AndParseResponseSuccessfullyWhenInBellatrix() {
    final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtilBellatrix = new DataStructureUtil(bellatrixSpec);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtilBellatrix.randomPayloadExecutionContext(false);

    when(executionEngineClient.getPayloadV2(eq(executionPayloadContext.getPayloadId())))
        .thenReturn(dummySuccessfulResponse());

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).add(UInt64.ZERO).build();

    jsonRpcMethod = new EngineGetPayloadV2(executionEngineClient, bellatrixSpec);

    assertThat(jsonRpcMethod.execute(params)).isCompleted();

    verify(executionEngineClient).getPayloadV2(eq(executionPayloadContext.getPayloadId()));
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void shouldCallGetPayloadV2AndParseResponseSuccessfullyWhenInCapella() {
    final Spec capellaSpec = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtilCapella = new DataStructureUtil(capellaSpec);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtilCapella.randomPayloadExecutionContext(false);

    when(executionEngineClient.getPayloadV2(eq(executionPayloadContext.getPayloadId())))
        .thenReturn(dummySuccessfulResponse());

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).add(UInt64.ZERO).build();

    jsonRpcMethod = new EngineGetPayloadV2(executionEngineClient, capellaSpec);

    assertThat(jsonRpcMethod.execute(params)).isCompleted();

    verify(executionEngineClient).getPayloadV2(eq(executionPayloadContext.getPayloadId()));
    verifyNoMoreInteractions(executionEngineClient);
  }

  private SafeFuture<Response<GetPayloadV2Response>> dummySuccessfulResponse() {
    return SafeFuture.completedFuture(
        new Response<>(
            new GetPayloadV2Response(
                ExecutionPayloadV2.fromInternalExecutionPayload(
                    dataStructureUtil.randomExecutionPayload()),
                dataStructureUtil.randomUInt256())));
  }

  private SafeFuture<Response<GetPayloadV2Response>> dummyFailedResponse(
      final String errorMessage) {
    return SafeFuture.completedFuture(Response.withErrorMessage(errorMessage));
  }
}
