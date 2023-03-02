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

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineNewPayloadV3Test {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EngineNewPayloadV3 jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EngineNewPayloadV3(executionEngineClient);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_newPayload");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(3);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_newPayloadV3");
  }

  @Test
  public void executionPayloadParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.newPayloadV3(any()))
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayload).build();

    assertThat(jsonRpcMethod.execute(params))
        .succeedsWithin(1, TimeUnit.SECONDS)
        .matches(PayloadStatus::hasFailedExecution);
  }

  @Test
  public void shouldCallNewPayloadV3WithExecutionPayloadV1WhenInBellatrix() {
    final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtilBellatrix = new DataStructureUtil(bellatrixSpec);

    final ExecutionPayload executionPayload = dataStructureUtilBellatrix.randomExecutionPayload();
    final ExecutionPayloadV1 executionPayloadV1 =
        ExecutionPayloadV1.fromInternalExecutionPayload(executionPayload);
    assertThat(executionPayloadV1).isExactlyInstanceOf(ExecutionPayloadV1.class);

    jsonRpcMethod = new EngineNewPayloadV3(executionEngineClient);

    when(executionEngineClient.newPayloadV3(executionPayloadV1))
        .thenReturn(dummySuccessfulResponse());

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayload).build();

    assertThat(jsonRpcMethod.execute(params)).isCompleted();

    verify(executionEngineClient).newPayloadV3(eq(executionPayloadV1));
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void shouldCallNewPayloadV3WithExecutionPayloadV2WhenInCapella() {
    final Spec capellaSpec = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtilCapella = new DataStructureUtil(capellaSpec);

    final ExecutionPayload executionPayload = dataStructureUtilCapella.randomExecutionPayload();
    final ExecutionPayloadV2 executionPayloadV2 =
        ExecutionPayloadV2.fromInternalExecutionPayload(executionPayload);
    assertThat(executionPayloadV2).isExactlyInstanceOf(ExecutionPayloadV2.class);

    jsonRpcMethod = new EngineNewPayloadV3(executionEngineClient);

    when(executionEngineClient.newPayloadV3(executionPayloadV2))
        .thenReturn(dummySuccessfulResponse());

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayload).build();

    assertThat(jsonRpcMethod.execute(params)).isCompleted();

    verify(executionEngineClient).newPayloadV3(eq(executionPayloadV2));
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void shouldCallNewPayloadV3WithExecutionPayloadV3WhenInDeneb() {
    final Spec denebSpec = TestSpecFactory.createMinimalDeneb();
    final DataStructureUtil dataStructureUtilDeneb = new DataStructureUtil(denebSpec);

    final ExecutionPayload executionPayload = dataStructureUtilDeneb.randomExecutionPayload();
    final ExecutionPayloadV3 executionPayloadV3 =
        ExecutionPayloadV3.fromInternalExecutionPayload(executionPayload);
    assertThat(executionPayloadV3).isExactlyInstanceOf(ExecutionPayloadV3.class);

    jsonRpcMethod = new EngineNewPayloadV3(executionEngineClient);

    when(executionEngineClient.newPayloadV3(executionPayloadV3))
        .thenReturn(dummySuccessfulResponse());

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayload).build();

    assertThat(jsonRpcMethod.execute(params)).isCompleted();

    verify(executionEngineClient).newPayloadV3(eq(executionPayloadV3));
    verifyNoMoreInteractions(executionEngineClient);
  }

  private SafeFuture<Response<PayloadStatusV1>> dummySuccessfulResponse() {
    return SafeFuture.completedFuture(
        new Response<>(
            new PayloadStatusV1(
                ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), null)));
  }

  private SafeFuture<Response<PayloadStatusV1>> dummyFailedResponse(final String errorMessage) {
    return SafeFuture.completedFuture(Response.withErrorMessage(errorMessage));
  }
}
