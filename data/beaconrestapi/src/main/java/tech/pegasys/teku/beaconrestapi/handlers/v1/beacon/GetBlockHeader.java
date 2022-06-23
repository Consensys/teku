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
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BLOCK_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BLOCK_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.BlockHeaderData;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockHeaderResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetBlockHeader extends MigratingEndpointAdapter {
  private static final String OAPI_ROUTE = "/eth/v1/beacon/headers/:block_id";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<BlockAndMetaData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(BlockAndMetaData.class)
          .name("GetBlockHeaderResponse")
          .withField("data", BlockHeaderData.getJsonTypeDefinition(), BlockHeaderData::new)
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
          .build();

  public GetBlockHeader(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetBlockHeader(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBlockHeader")
            .summary("Get block header")
            .description("Retrieves block header for given block id.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get block header",
      tags = {TAG_BEACON},
      description = "Retrieves block header for given block id.",
      pathParams = {@OpenApiParam(name = PARAM_BLOCK_ID, description = PARAM_BLOCK_ID_DESCRIPTION)},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetBlockHeaderResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<BlockAndMetaData>> future =
        chainDataProvider.getBlockAndMetaData(request.getPathParameter(PARAMETER_BLOCK_ID));

    request.respondAsync(
        future.thenApply(
            maybeBlockAndMetaData -> {
              if (maybeBlockAndMetaData.isEmpty()) {
                return AsyncApiResponse.respondNotFound();
              }

              final BlockAndMetaData blockAndMetaData = maybeBlockAndMetaData.get();
              return AsyncApiResponse.respondOk(blockAndMetaData);
            }));
  }
}
