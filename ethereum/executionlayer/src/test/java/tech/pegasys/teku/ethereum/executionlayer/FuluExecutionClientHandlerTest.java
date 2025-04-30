/*
 * Copyright Consensys Software Inc., 2025
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV2;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV5Response;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.execution.BlobsCellBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class FuluExecutionClientHandlerTest extends ExecutionHandlerClientTest {

  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalFulu();
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void engineGetPayload_shouldCallGetPayloadV5() {
    final ExecutionClientHandler handler = getHandler();
    final ExecutionPayloadContext context = randomContext();
    final GetPayloadV5Response responseData =
        new GetPayloadV5Response(
            ExecutionPayloadV3.fromInternalExecutionPayload(
                dataStructureUtil.randomExecutionPayload()),
            UInt256.MAX_VALUE,
            BlobsBundleV2.fromInternalBlobsBundle(dataStructureUtil.randomBlobsCellBundle()),
            true,
            dataStructureUtil.randomEncodedExecutionRequests());
    final SafeFuture<Response<GetPayloadV5Response>> dummyResponse =
        SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData));
    when(executionEngineClient.getPayloadV5(context.getPayloadId())).thenReturn(dummyResponse);

    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final SafeFuture<GetPayloadResponse> future = handler.engineGetPayload(context, slot);
    verify(executionEngineClient).getPayloadV5(context.getPayloadId());
    final SchemaDefinitionsFulu schemaDefinitionFulu =
        spec.atSlot(slot).getSchemaDefinitions().toVersionFulu().orElseThrow();
    final ExecutionPayloadSchema<?> executionPayloadSchema =
        schemaDefinitionFulu.getExecutionPayloadSchema();
    final BlobSchema blobSchema = schemaDefinitionFulu.getBlobSchema();
    final GetPayloadResponse expectedGetPayloadResponse =
        responseData.asInternalGetPayloadResponse(
            executionPayloadSchema, blobSchema, schemaDefinitionFulu.getExecutionRequestsSchema());
    assertThat(future).isCompletedWithValue(expectedGetPayloadResponse);
  }

  @Test
  void engineGetBlobs_shouldCallGetBlobsV2() {
    final ExecutionClientHandler handler = getHandler();
    final int maxBlobsPerBlock =
        SpecConfigDeneb.required(spec.getGenesisSpecConfig()).getMaxBlobsPerBlock();
    final List<VersionedHash> versionedHashes =
        dataStructureUtil.randomVersionedHashes(maxBlobsPerBlock);
    final BlobsCellBundle blobsCellBundle =
        dataStructureUtil.randomBlobsCellBundle(maxBlobsPerBlock);
    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final List<BlobAndProofV2> responseData =
        blobsCellBundle.getBlobs().stream()
            .map(
                blob ->
                    new BlobAndProofV2(
                        blob.getBytes(),
                        blobsCellBundle.getProofs().stream()
                            .map(KZGProof::getBytesCompressed)
                            .toList()))
            .toList();
    final SafeFuture<Response<List<BlobAndProofV2>>> dummyResponse =
        SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData));
    when(executionEngineClient.getBlobsV2(versionedHashes)).thenReturn(dummyResponse);
    final SafeFuture<List<BlobAndCellProofs>> future =
        handler.engineGetBlobsV2(versionedHashes, slot);
    verify(executionEngineClient).getBlobsV2(versionedHashes);
    final BlobSchema blobSchema =
        spec.atSlot(slot).getSchemaDefinitions().toVersionDeneb().orElseThrow().getBlobSchema();
    assertThat(future)
        .isCompletedWithValue(
            responseData.stream()
                .map(blobAndProofV2 -> blobAndProofV2.asInternalBlobAndProofs(blobSchema))
                .toList());
  }

  private ExecutionPayloadContext randomContext() {
    return new ExecutionPayloadContext(
        dataStructureUtil.randomBytes8(),
        dataStructureUtil.randomForkChoiceState(false),
        dataStructureUtil.randomPayloadBuildingAttributes(false));
  }
}
