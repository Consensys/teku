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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;

import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetStateRootResponse;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;

public class GetStateRoot extends AbstractGetSimpleDataFromState {
  private static final String OAPI_ROUTE = "/eth/v1/beacon/states/:state_id/root";
  public static final String ROUTE = AbstractHandler.routeWithBracedParameters(OAPI_ROUTE);

  private static final SerializableTypeDefinition<Bytes32> ROOT_TYPE =
      SerializableTypeDefinition.object(Bytes32.class)
          .withField("root", BYTES32_TYPE, Function.identity())
          .build();

  private static final SerializableTypeDefinition<StateAndMetaData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(StateAndMetaData.class)
          .name("GetStateRootResponse")
          .withField(
              "data", ROOT_TYPE, stateAndMetaData -> stateAndMetaData.getData().hashTreeRoot())
          .build();

  public GetStateRoot(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateRoot(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateRoot")
            .summary("Get state root")
            .description(
                "Calculates HashTreeRoot for state with given 'state_id'. If stateId is root, same value will be returned.")
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
      summary = "Get state root",
      tags = {TAG_BEACON},
      description =
          "Calculates HashTreeRoot for state with given 'state_id'. If stateId is root, same value will be returned.",
      pathParams = {@OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION)},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetStateRootResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }
}
