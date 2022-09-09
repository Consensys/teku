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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.JSON;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.v1.debug.GetStateResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetState extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/debug/beacon/states/{state_id}";
  private final ChainDataProvider chainDataProvider;

  public GetState(
      final DataProvider dataProvider, final Spec spec, final SchemaDefinitionCache schemaCache) {
    this(dataProvider.getChainDataProvider(), spec, schemaCache);
  }

  @SuppressWarnings("unchecked")
  public GetState(
      final ChainDataProvider chainDataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getState")
            .summary("Get state")
            .description(
                "Returns full BeaconState object for given state_id.\n\n"
                    + "Use Accept header to select `application/octet-stream` if SSZ response type is required.\n\n"
                    + "__NOTE__: Only phase0 beacon state will be returned in JSON, use `/eth/v2/beacon/states/{state_id}` for altair.")
            .tags(TAG_DEBUG)
            .pathParam(PARAMETER_STATE_ID)
            .response(
                SC_OK,
                "Request successful",
                SerializableTypeDefinition.<BeaconState>object()
                    .name("GetStateResponse")
                    .withField(
                        "data",
                        (DeserializableTypeDefinition<BeaconState>)
                            schemaCache
                                .getSchemaDefinition(SpecMilestone.PHASE0)
                                .getBeaconStateSchema()
                                .getJsonTypeDefinition(),
                        Function.identity())
                    .build(),
                sszResponseType(
                    beaconState ->
                        spec.getForkSchedule().getSpecMilestoneAtSlot(beaconState.getSlot())))
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      deprecated = true,
      method = HttpMethod.GET,
      summary = "Get state",
      tags = {TAG_DEBUG},
      description =
          "Returns full BeaconState object for given state_id.\n\n"
              + "Use Accept header to select `application/octet-stream` if SSZ response type is required.\n\n"
              + "__NOTE__: Only phase0 beacon state will be returned in JSON, use `/eth/v2/beacon/states/{state_id}` for altair.",
      pathParams = {@OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION)},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = {
              @OpenApiContent(type = JSON, from = GetStateResponse.class),
              @OpenApiContent(type = OCTET_STREAM)
            }),
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
    final SafeFuture<Optional<StateAndMetaData>> future =
        chainDataProvider.getBeaconStateAndMetadata(request.getPathParameter(PARAMETER_STATE_ID));

    request.respondAsync(
        future.thenApply(
            maybeStateAndMetaData ->
                maybeStateAndMetaData
                    .map(
                        stateAndMetaData -> {
                          if (JSON.equals(request.getResponseContentType(SC_OK))
                              && (stateAndMetaData.getMilestone() != SpecMilestone.PHASE0)) {
                            throw new BadRequestException("SpecMilestone not PHASE0");
                          }
                          return AsyncApiResponse.respondOk(stateAndMetaData.getData());
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }
}
