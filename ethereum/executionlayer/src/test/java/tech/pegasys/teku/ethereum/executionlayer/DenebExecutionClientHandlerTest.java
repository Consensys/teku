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
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV1;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DenebExecutionClientHandlerTest extends ExecutionHandlerClientTest {

  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalDeneb();
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void engineGetPayload_shouldCallGetPayloadV3() {
    final ExecutionClientHandler handler = getHandler();
    final ExecutionPayloadContext context = randomContext();
    final GetPayloadV3Response responseData =
        new GetPayloadV3Response(
            ExecutionPayloadV3.fromInternalExecutionPayload(
                dataStructureUtil.randomExecutionPayload()),
            UInt256.MAX_VALUE,
            BlobsBundleV1.fromInternalBlobsBundle(dataStructureUtil.randomBlobsBundle()),
            true);
    final SafeFuture<Response<GetPayloadV3Response>> dummyResponse =
        SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData));
    when(executionEngineClient.getPayloadV3(context.getPayloadId())).thenReturn(dummyResponse);

    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final SafeFuture<GetPayloadResponse> future = handler.engineGetPayload(context, slot);
    verify(executionEngineClient).getPayloadV3(context.getPayloadId());
    final SchemaDefinitionsDeneb schemaDefinitionDeneb =
        spec.atSlot(slot).getSchemaDefinitions().toVersionDeneb().orElseThrow();
    final ExecutionPayloadSchema<?> executionPayloadSchema =
        schemaDefinitionDeneb.getExecutionPayloadSchema();
    final BlobSchema blobSchema = schemaDefinitionDeneb.getBlobSchema();
    assertThat(future)
        .isCompletedWithValue(
            responseData.asInternalGetPayloadResponse(executionPayloadSchema, blobSchema));
  }

  @Test
  void engineNewPayload_shouldCallNewPayloadV3() {
    final ExecutionClientHandler handler = getHandler();
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    final Bytes32 parentBeaconBlockRoot = dataStructureUtil.randomBytes32();
    final List<VersionedHash> versionedHashes = dataStructureUtil.randomVersionedHashes(3);
    final NewPayloadRequest newPayloadRequest =
        new NewPayloadRequest(payload, versionedHashes, parentBeaconBlockRoot);
    final ExecutionPayloadV3 payloadV3 = ExecutionPayloadV3.fromInternalExecutionPayload(payload);
    final PayloadStatusV1 responseData =
        new PayloadStatusV1(
            ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), null);
    final SafeFuture<Response<PayloadStatusV1>> dummyResponse =
        SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData));
    when(executionEngineClient.newPayloadV3(payloadV3, versionedHashes, parentBeaconBlockRoot))
        .thenReturn(dummyResponse);
    final SafeFuture<PayloadStatus> future =
        handler.engineNewPayload(newPayloadRequest, UInt64.ZERO);
    verify(executionEngineClient).newPayloadV3(payloadV3, versionedHashes, parentBeaconBlockRoot);
    assertThat(future).isCompletedWithValue(responseData.asInternalExecutionPayload());
  }

  @Test
  void engineForkChoiceUpdated_shouldCallEngineForkChoiceUpdatedV3() {
    final ExecutionClientHandler handler = getHandler();
    final ForkChoiceState forkChoiceState = dataStructureUtil.randomForkChoiceState(false);
    final ForkChoiceStateV1 forkChoiceStateV1 =
        ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState);
    final PayloadBuildingAttributes attributes =
        new PayloadBuildingAttributes(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEth1Address(),
            Optional.empty(),
            Optional.of(List.of()),
            dataStructureUtil.randomBytes32());
    final Optional<PayloadAttributesV3> payloadAttributes =
        PayloadAttributesV3.fromInternalPayloadBuildingAttributesV3(Optional.of(attributes));
    final ForkChoiceUpdatedResult responseData =
        new ForkChoiceUpdatedResult(
            new PayloadStatusV1(
                ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), ""),
            dataStructureUtil.randomBytes8());
    final SafeFuture<Response<ForkChoiceUpdatedResult>> dummyResponse =
        SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData));
    when(executionEngineClient.forkChoiceUpdatedV3(forkChoiceStateV1, payloadAttributes))
        .thenReturn(dummyResponse);
    final SafeFuture<tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult> future =
        handler.engineForkChoiceUpdated(forkChoiceState, Optional.of(attributes));
    verify(executionEngineClient).forkChoiceUpdatedV3(forkChoiceStateV1, payloadAttributes);
    assertThat(future).isCompletedWithValue(responseData.asInternalExecutionPayload());
  }

  @Test
  void engineGetBlobs_shouldCallGetBlobsV1() {
    final ExecutionClientHandler handler = getHandler();
    final int maxBlobsPerBlock =
        SpecConfigDeneb.required(spec.getGenesisSpecConfig()).getMaxBlobsPerBlock();
    final List<VersionedHash> versionedHashes =
        dataStructureUtil.randomVersionedHashes(maxBlobsPerBlock - 1);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecars(maxBlobsPerBlock);
    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final List<BlobAndProofV1> responseData =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndProofV1(
                        blobSidecar.getBlob().getBytes(),
                        blobSidecar.getKZGProof().getBytesCompressed()))
            .toList();
    final SafeFuture<Response<List<BlobAndProofV1>>> dummyResponse =
        SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData));
    when(executionEngineClient.getBlobsV1(versionedHashes)).thenReturn(dummyResponse);
    final SafeFuture<List<BlobAndProof>> future = handler.engineGetBlobsV1(versionedHashes, slot);
    verify(executionEngineClient).getBlobsV1(versionedHashes);
    final BlobSchema blobSchema =
        spec.atSlot(slot).getSchemaDefinitions().toVersionDeneb().orElseThrow().getBlobSchema();
    assertThat(future)
        .isCompletedWithValue(
            responseData.stream()
                .map(blobAndProofV1 -> blobAndProofV1.asInternalBlobAndProof(blobSchema))
                .toList());
  }

  private ExecutionPayloadContext randomContext() {
    return new ExecutionPayloadContext(
        dataStructureUtil.randomBytes8(),
        dataStructureUtil.randomForkChoiceState(false),
        dataStructureUtil.randomPayloadBuildingAttributes(false));
  }
}
