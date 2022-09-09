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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllMilestones;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.SIGNATURE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.AllBlocksAtSlotData;
import tech.pegasys.teku.api.response.v1.teku.GetAllBlocksAtSlotResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class GetAllBlocksAtSlot extends MigratingEndpointAdapter {
  public static final String ROUTE = "/teku/v1/beacon/blocks/{slot}";
  private final ChainDataProvider chainDataProvider;

  public GetAllBlocksAtSlot(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetAllBlocksAtSlot(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getAllBlocksAtSlot")
            .summary("Get blocks at slot")
            .description("Get all blocks (canonical and non-canonical) by slot.")
            .tags(TAG_TEKU)
            .pathParam(SLOT_PARAMETER.withDescription("slot of the blocks to retrieve."))
            .response(SC_OK, "Request successful", getResponseType(schemaDefinitionCache))
            .withServiceUnavailableResponse()
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get blocks at slot",
      tags = {TAG_TEKU},
      description = "Get all blocks (canonical and non-canonical) by slot.",
      pathParams = {@OpenApiParam(name = SLOT, description = "slot of the blocks to retrieve.")},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetAllBlocksAtSlotResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);

    final UInt64 slot = request.getPathParameter(SLOT_PARAMETER);
    final SafeFuture<List<BlockAndMetaData>> future = chainDataProvider.getAllBlocksAtSlot(slot);

    request.respondAsync(
        future.thenApply(
            blockAndMetaDataList -> {
              if (blockAndMetaDataList.isEmpty()) {
                return AsyncApiResponse.respondWithError(SC_NOT_FOUND, "Blocks not found: " + slot);
              }

              return AsyncApiResponse.respondOk(new AllBlocksAtSlotData(blockAndMetaDataList));
            }));
  }

  public static SerializableTypeDefinition<AllBlocksAtSlotData> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableOneOfTypeDefinition<BeaconBlock> messageType =
        getSchemaDefinitionForAllMilestones(
            schemaDefinitionCache,
            "BeaconBlock",
            SchemaDefinitions::getBeaconBlockSchema,
            (beaconBlock, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(beaconBlock.getSlot()).equals(milestone));

    final SerializableTypeDefinition<SignedBeaconBlock> dataType =
        SerializableTypeDefinition.object(SignedBeaconBlock.class)
            .withField("message", messageType, SignedBeaconBlock::getMessage)
            .withField("signature", SIGNATURE_TYPE, SignedBeaconBlock::getSignature)
            .withField("root", BYTES32_TYPE, SignedBeaconBlock::getRoot)
            .build();

    return SerializableTypeDefinition.object(AllBlocksAtSlotData.class)
        .name("GetAllBlocksAtSlotResponse")
        .withField("version", MILESTONE_TYPE, AllBlocksAtSlotData::getVersion)
        .withField("data", listOf(dataType), AllBlocksAtSlotData::getBlocks)
        .build();
  }
}
