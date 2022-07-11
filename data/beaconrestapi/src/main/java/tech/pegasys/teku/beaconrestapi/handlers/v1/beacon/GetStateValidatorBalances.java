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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ID_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_VALIDATOR_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.StateValidatorBalanceData;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorBalancesResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetStateValidatorBalances extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/validator_balances";

  private static final SerializableTypeDefinition<
          ObjectAndMetaData<List<StateValidatorBalanceData>>>
      RESPONSE_TYPE =
          SerializableTypeDefinition.<ObjectAndMetaData<List<StateValidatorBalanceData>>>object()
              .name("GetStateValidatorBalancesResponse")
              .withField(
                  EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
              .withField(
                  "data",
                  listOf(StateValidatorBalanceData.getJsonTypeDefinition()),
                  ObjectAndMetaData::getData)
              .build();

  private final ChainDataProvider chainDataProvider;

  public GetStateValidatorBalances(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateValidatorBalances(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateValidatorBalances")
            .summary("Get validator balances from state")
            .description("Returns filterable list of validator balances.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_STATE_ID)
            .queryListParam(ID_PARAMETER)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get validator balances from state",
      tags = {TAG_BEACON},
      description = "Returns filterable list of validator balances.",
      pathParams = {@OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION)},
      queryParams = {
        @OpenApiParam(
            name = PARAM_ID,
            description = PARAM_VALIDATOR_DESCRIPTION,
            isRepeatable = true)
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetStateValidatorBalancesResponse.class)),
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
    final List<String> validators = request.getQueryParameterList(ID_PARAMETER);

    final SafeFuture<Optional<ObjectAndMetaData<List<StateValidatorBalanceData>>>> future =
        chainDataProvider.getStateValidatorBalances(
            request.getPathParameter(PARAMETER_STATE_ID), validators);

    request.respondAsync(
        future.thenApply(
            maybeDataList ->
                maybeDataList
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondNotFound())));
  }
}
