/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BLOB_INDICES_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class GetBlobSidecars extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/blob_sidecars/{block_id}";
  private final ChainDataProvider chainDataProvider;

  public GetBlobSidecars(final DataProvider dataProvider, final SchemaDefinitionCache schemaCache) {
    this(dataProvider.getChainDataProvider(), schemaCache);
  }

  public GetBlobSidecars(
      final ChainDataProvider chainDataProvider, final SchemaDefinitionCache schemaCache) {
    super(createEndpointMetadata(schemaCache));
    this.chainDataProvider = chainDataProvider;
  }

  private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("getBlobSidecars")
        .summary("Get blob sidecars")
        .description(
            "Retrieves blob sidecars for a given block id.\n"
                + "    Depending on `Accept` header it can be returned either as json or as bytes serialized by SSZ.\n"
                + "    If the `indices` parameter is specified, only the blob sidecars with the specified indices will be returned. There are no guarantees\n"
                + "    for the returned blob sidecars in terms of ordering.")
        .tags(TAG_BEACON)
        .pathParam(PARAMETER_BLOCK_ID)
        .queryListParam(BLOB_INDICES_PARAMETER)
        .response(SC_OK, "Request successful", getResponseType(schemaCache), getSszResponseType())
        .withNotFoundResponse()
        .build();
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ObjectAndMetaData<SignedBeaconBlock>>> blockFuture =
        chainDataProvider.getBlock(request.getPathParameter(PARAMETER_BLOCK_ID));
    final List<UInt64> indices = request.getQueryParameterList(BLOB_INDICES_PARAMETER);
    final SafeFuture<Optional<List<BlobSidecar>>> resultFuture =
        blockFuture.thenCompose(
            maybeSignedBlock ->
                maybeSignedBlock
                    .map(
                        signedBlock ->
                            chainDataProvider
                                .getBlobSidecars(
                                    signedBlock.getData().getSlotAndBlockRoot(), indices)
                                .thenApply(Optional::of))
                    .orElse(SafeFuture.completedFuture(Optional.empty())));

    request.respondAsync(
        resultFuture.thenApply(
            blobSidecarList ->
                blobSidecarList
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondNotFound())));
  }

  private static SerializableTypeDefinition<List<BlobSidecar>> getResponseType(
      final SchemaDefinitionCache schemaCache) {
    final DeserializableTypeDefinition<BlobSidecar> blobSidecarType =
        SchemaDefinitionsDeneb.required(schemaCache.getSchemaDefinition(SpecMilestone.DENEB))
            .getBlobSidecarSchema()
            .getJsonTypeDefinition();
    return SerializableTypeDefinition.<List<BlobSidecar>>object()
        .name("GetBlobsResponse")
        .withField("data", listOf(blobSidecarType), Function.identity())
        .build();
  }

  private static ResponseContentTypeDefinition<List<BlobSidecar>> getSszResponseType() {
    final OctetStreamResponseContentTypeDefinition.OctetStreamSerializer<List<BlobSidecar>>
        serializer =
            (data, out) ->
                data.stream().forEachOrdered(blobSidecar -> blobSidecar.sszSerialize(out));

    return new OctetStreamResponseContentTypeDefinition<>(serializer, __ -> Collections.emptyMap());
  }
}
