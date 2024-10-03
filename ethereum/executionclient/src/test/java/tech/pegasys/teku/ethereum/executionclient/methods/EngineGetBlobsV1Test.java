/*
 * Copyright Consensys Software Inc., 2024
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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.InvalidRemoteResponseException;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class EngineGetBlobsV1Test {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EngineGetBlobsV1 jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EngineGetBlobsV1(executionEngineClient, spec);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_getBlobs");
    assertThat(jsonRpcMethod.isOptional()).isTrue();
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(1);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_getBlobsV1");
  }

  @Test
  public void blobVersionedHashesParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void slotParamIsRequired() {
    final List<VersionedHash> versionedHashes = dataStructureUtil.randomVersionedHashes(4);

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(versionedHashes).build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 1");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final List<VersionedHash> versionedHashes = dataStructureUtil.randomVersionedHashes(4);
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.getBlobsV1(any()))
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(versionedHashes).add(UInt64.ZERO).build();

    assertThat(jsonRpcMethod.execute(params))
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(InvalidRemoteResponseException.class)
        .withMessageContaining(
            "Invalid remote response from the execution client: %s", errorResponseFromClient);
  }

  @Test
  public void shouldCallGetBlobsV1AndParseResponseSuccessfully() {
    final List<VersionedHash> versionedHashes = dataStructureUtil.randomVersionedHashes(4);
    final List<BlobSidecar> blobSidecars =
        dataStructureUtil.randomBlobSidecars(spec.getMaxBlobsPerBlock().orElseThrow());

    when(executionEngineClient.getBlobsV1(eq(versionedHashes)))
        .thenReturn(dummySuccessfulResponse(blobSidecars));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(versionedHashes).add(UInt64.ZERO).build();

    jsonRpcMethod = new EngineGetBlobsV1(executionEngineClient, spec);

    final List<BlobAndProof> expectedResponse =
        blobSidecars.stream()
            .map(blobSidecar -> new BlobAndProof(blobSidecar.getBlob(), blobSidecar.getKZGProof()))
            .toList();
    assertThat(jsonRpcMethod.execute(params)).isCompletedWithValue(expectedResponse);

    verify(executionEngineClient).getBlobsV1(eq(versionedHashes));
    verifyNoMoreInteractions(executionEngineClient);
  }

  private SafeFuture<Response<List<BlobAndProofV1>>> dummySuccessfulResponse(
      final List<BlobSidecar> blobSidecars) {
    return SafeFuture.completedFuture(
        new Response<>(
            blobSidecars.stream()
                .map(
                    blobSidecar ->
                        new BlobAndProofV1(
                            blobSidecar.getBlob().getBytes(),
                            blobSidecar.getKZGProof().getBytesCompressed()))
                .toList()));
  }

  private SafeFuture<Response<List<BlobAndProofV1>>> dummyFailedResponse(
      final String errorMessage) {
    return SafeFuture.completedFuture(Response.withErrorMessage(errorMessage));
  }
}
