/*
 * Copyright 2020 ConsenSys AG.
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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetStateFinalityCheckpointsResponse;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateFinalityCheckpoints extends AbstractGetSimpleDataFromState {
  private static final String OAPI_ROUTE = "/eth/v1/beacon/states/:state_id/finality_checkpoints";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);

  private static final SerializableTypeDefinition<BeaconState> DATA_TYPE =
      SerializableTypeDefinition.object(BeaconState.class)
          .withField(
              "previous_justified",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              BeaconState::getPreviousJustifiedCheckpoint)
          .withField(
              "current_justified",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              BeaconState::getCurrentJustifiedCheckpoint)
          .withField(
              "finalized",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              BeaconState::getFinalizedCheckpoint)
          .build();

  private static final SerializableTypeDefinition<StateAndMetaData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(StateAndMetaData.class)
          .withField("data", DATA_TYPE, StateAndMetaData::getData)
          .build();

  public GetStateFinalityCheckpoints(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateFinalityCheckpoints(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateFinalityCheckpoints")
            .summary("Get state finality checkpoints")
            .description(
                "Returns finality checkpoints for state with given 'state_id'. In case finality is not yet achieved, checkpoint should return epoch 0 and ZERO_HASH as root.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_STATE_ID)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .build(),
        chainDataProvider);
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get state finality checkpoints",
      tags = {TAG_BEACON},
      description =
          "Returns finality checkpoints for state with given 'state_id'. In case finality is not yet achieved, checkpoint should return epoch 0 and ZERO_HASH as root.",
      pathParams = {@OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION)},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetStateFinalityCheckpointsResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }
}
