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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetBlock extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/blocks/{block_id}";
  private final ChainDataProvider chainDataProvider;

  public GetBlock(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetBlock(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBlock")
            .summary("Get block")
            .description(
                "Retrieves block details for given block id.\n\n"
                    + "__NOTE__: only phase 0 blocks are returned, use `/eth/v2/beacon/blocks/{block_id}` for multiple milestone support.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(SC_OK, "Request successful", getResponseType(schemaDefinitionCache))
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    SafeFuture<? extends Optional<ObjectAndMetaData<?>>> future =
        chainDataProvider.getSignedBeaconBlock(request.getPathParameter(PARAMETER_BLOCK_ID));

    request.respondAsync(
        future.thenApply(
            maybeObjectAndMetaData -> {
              if (maybeObjectAndMetaData.isEmpty()) {
                return AsyncApiResponse.respondNotFound();
              }

              ObjectAndMetaData<SignedBeaconBlock> response =
                  (ObjectAndMetaData<SignedBeaconBlock>) maybeObjectAndMetaData.get();

              if (!chainDataProvider
                  .getMilestoneAtSlot(response.getData().getMessage().getSlot())
                  .equals(SpecMilestone.PHASE0)) {
                final String message =
                    String.format(
                        "Slot %s is not a phase0 slot, please fetch via /eth/v2/beacon/blocks",
                        response.getData().getMessage().getSlot());
                return AsyncApiResponse.respondWithError(SC_BAD_REQUEST, message);
              }

              return AsyncApiResponse.respondOk(response.getData());
            }));
  }

  private static SerializableTypeDefinition<SignedBeaconBlock> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    SerializableTypeDefinition<SignedBeaconBlock> schemaDefinition =
        schemaDefinitionCache
            .getSchemaDefinition(SpecMilestone.PHASE0)
            .getSignedBeaconBlockSchema()
            .getJsonTypeDefinition();

    return SerializableTypeDefinition.object(SignedBeaconBlock.class)
        .name("GetBlockResponse")
        .withField("data", schemaDefinition, Function.identity())
        .build();
  }
}
