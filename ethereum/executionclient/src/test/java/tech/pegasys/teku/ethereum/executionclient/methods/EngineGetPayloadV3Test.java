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
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.InvalidRemoteResponseException;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadWithValue;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineGetPayloadV3Test {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EngineGetPayloadV3 jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EngineGetPayloadV3(executionEngineClient, spec);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_getPayload");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(3);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_getPayloadV3");
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

    when(executionEngineClient.getPayloadV3(any()))
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
  public void shouldCallGetPayloadV3AndParseResponseSuccessfullyWhenInBellatrix() {
    final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtilBellatrix = new DataStructureUtil(bellatrixSpec);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtilBellatrix.randomPayloadExecutionContext(false);
    final UInt256 blockValue = UInt256.MAX_VALUE;
    final ExecutionPayload executionPayloadBellatrix =
        dataStructureUtilBellatrix.randomExecutionPayload();
    assertThat(executionPayloadBellatrix).isInstanceOf(ExecutionPayloadBellatrix.class);

    when(executionEngineClient.getPayloadV3(eq(executionPayloadContext.getPayloadId())))
        .thenReturn(
            dummySuccessfulResponseWithNoBlobsBundle(executionPayloadBellatrix, blockValue));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).add(UInt64.ZERO).build();

    jsonRpcMethod = new EngineGetPayloadV3(executionEngineClient, bellatrixSpec);

    final ExecutionPayloadWithValue expectedPayloadWithValue =
        new ExecutionPayloadWithValue(executionPayloadBellatrix, blockValue);
    assertThat(jsonRpcMethod.execute(params)).isCompletedWithValue(expectedPayloadWithValue);

    verify(executionEngineClient).getPayloadV3(eq(executionPayloadContext.getPayloadId()));
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void shouldCallGetPayloadV3AndParseResponseSuccessfullyWhenInCapella() {
    final Spec capellaSpec = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtilCapella = new DataStructureUtil(capellaSpec);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtilCapella.randomPayloadExecutionContext(false);
    final UInt256 blockValue = UInt256.MAX_VALUE;
    final ExecutionPayload executionPayloadCapella =
        dataStructureUtilCapella.randomExecutionPayload();
    assertThat(executionPayloadCapella).isInstanceOf(ExecutionPayloadCapella.class);

    when(executionEngineClient.getPayloadV3(eq(executionPayloadContext.getPayloadId())))
        .thenReturn(dummySuccessfulResponseWithNoBlobsBundle(executionPayloadCapella, blockValue));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).add(UInt64.ZERO).build();

    jsonRpcMethod = new EngineGetPayloadV3(executionEngineClient, capellaSpec);

    final ExecutionPayloadWithValue expectedPayloadWithValue =
        new ExecutionPayloadWithValue(executionPayloadCapella, blockValue);
    assertThat(jsonRpcMethod.execute(params)).isCompletedWithValue(expectedPayloadWithValue);

    verify(executionEngineClient).getPayloadV3(eq(executionPayloadContext.getPayloadId()));
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void shouldCallGetPayloadV3AndParseResponseSuccessfullyWhenInDeneb() {
    final Spec denebSpec = TestSpecFactory.createMinimalDeneb();
    final DataStructureUtil dataStructureUtilDeneb = new DataStructureUtil(denebSpec);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtilDeneb.randomPayloadExecutionContext(false);
    final UInt256 blockValue = UInt256.MAX_VALUE;
    final BlobsBundle blobsBundle = dataStructureUtilDeneb.randomBlobsBundle();
    final ExecutionPayload executionPayloadCapella =
        dataStructureUtilDeneb.randomExecutionPayload();
    assertThat(executionPayloadCapella).isInstanceOf(ExecutionPayloadDeneb.class);

    when(executionEngineClient.getPayloadV3(eq(executionPayloadContext.getPayloadId())))
        .thenReturn(dummySuccessfulResponse(executionPayloadCapella, blockValue, blobsBundle));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).add(UInt64.ZERO).build();

    jsonRpcMethod = new EngineGetPayloadV3(executionEngineClient, denebSpec);

    final ExecutionPayloadWithValue expectedPayloadWithValue =
        new ExecutionPayloadWithValue(executionPayloadCapella, blockValue);
    assertThat(jsonRpcMethod.execute(params)).isCompletedWithValue(expectedPayloadWithValue);

    verify(executionEngineClient).getPayloadV3(eq(executionPayloadContext.getPayloadId()));
    verifyNoMoreInteractions(executionEngineClient);
  }

  private SafeFuture<Response<GetPayloadV3Response>> dummySuccessfulResponseWithNoBlobsBundle(
      final ExecutionPayload executionPayload, final UInt256 blockValue) {
    return SafeFuture.completedFuture(
        new Response<>(
            new GetPayloadV3Response(
                ExecutionPayloadV3.fromInternalExecutionPayload(executionPayload),
                blockValue,
                null)));
  }

  private SafeFuture<Response<GetPayloadV3Response>> dummySuccessfulResponse(
      final ExecutionPayload executionPayload,
      final UInt256 blockValue,
      final BlobsBundle blobsBundle) {
    return SafeFuture.completedFuture(
        new Response<>(
            new GetPayloadV3Response(
                ExecutionPayloadV3.fromInternalExecutionPayload(executionPayload),
                blockValue,
                BlobsBundleV1.fromInternalBlobsBundle(blobsBundle))));
  }

  private SafeFuture<Response<GetPayloadV3Response>> dummyFailedResponse(
      final String errorMessage) {
    return SafeFuture.completedFuture(Response.withErrorMessage(errorMessage));
  }
}
