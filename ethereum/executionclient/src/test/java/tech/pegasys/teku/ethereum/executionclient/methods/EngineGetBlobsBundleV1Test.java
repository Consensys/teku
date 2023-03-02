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
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineGetBlobsBundleV1Test {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EngineGetBlobsBundleV1 jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EngineGetBlobsBundleV1(executionEngineClient, spec);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_getBlobsBundle");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(1);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_getBlobsBundleV1");
  }

  @Test
  public void payloadIdParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void slotParamIsRequired() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();

    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().add(payloadId).build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 1");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.getBlobsBundleV1(any()))
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(payloadId).add(UInt64.ZERO).build();

    assertThat(jsonRpcMethod.execute(params))
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(InvalidRemoteResponseException.class)
        .withMessageContaining(
            "Invalid remote response from the execution client: %s", errorResponseFromClient);
  }

  @Test
  public void shouldFailForPreDenebMilestone() {
    final Spec capellaSpec = TestSpecFactory.createMinimalCapella();
    jsonRpcMethod = new EngineGetBlobsBundleV1(executionEngineClient, capellaSpec);

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(dataStructureUtil.randomBytes8())
            .add(UInt64.ZERO)
            .build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Pre-Deneb execution client handler is called to get Deneb BlobsBundleV1");
  }

  @Test
  public void shouldCallExecutionEngineClientGetBlobsBundleV1() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle();

    when(executionEngineClient.getBlobsBundleV1(eq(payloadId)))
        .thenReturn(dummySuccessfulResponse(blobsBundle));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(payloadId).add(UInt64.ZERO).build();

    assertThat(jsonRpcMethod.execute(params)).isCompletedWithValue(blobsBundle);

    verify(executionEngineClient).getBlobsBundleV1(eq(payloadId));
    verifyNoMoreInteractions(executionEngineClient);
  }

  private SafeFuture<Response<BlobsBundleV1>> dummySuccessfulResponse(BlobsBundle blobsBundle) {
    return SafeFuture.completedFuture(
        new Response<>(BlobsBundleV1.fromInternalBlobsBundle(blobsBundle)));
  }

  private SafeFuture<Response<BlobsBundleV1>> dummyFailedResponse(final String errorMessage) {
    return SafeFuture.completedFuture(Response.withErrorMessage(errorMessage));
  }
}
