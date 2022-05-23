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
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_VALIDATOR_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_VALIDATOR_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_VALIDATOR_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.StateValidatorData;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Validator;

public class GetStateValidator extends MigratingEndpointAdapter {
  private static final String OAPI_ROUTE =
      "/eth/v1/beacon/states/:state_id/validators/:validator_id";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);

  private static final DeserializableTypeDefinition<ValidatorStatus> STATUS_TYPE =
      DeserializableTypeDefinition.enumOf(ValidatorStatus.class);

  public static final SerializableTypeDefinition<StateValidatorData> STATE_VALIDATOR_DATA_TYPE =
      SerializableTypeDefinition.object(StateValidatorData.class)
          .withField("index", UINT64_TYPE, StateValidatorData::getIndex)
          .withField("balance", UINT64_TYPE, StateValidatorData::getBalance)
          .withField("status", STATUS_TYPE, StateValidatorData::getStatus)
          .withField(
              "validator",
              Validator.SSZ_SCHEMA.getJsonTypeDefinition(),
              StateValidatorData::getValidator)
          .build();

  private static final SerializableTypeDefinition<ObjectAndMetaData<StateValidatorData>>
      RESPONSE_TYPE =
          SerializableTypeDefinition.<ObjectAndMetaData<StateValidatorData>>object()
              .name("GetStateValidatorResponse")
              .withField(
                  "execution_optimistic", BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
              .withField("data", STATE_VALIDATOR_DATA_TYPE, ObjectAndMetaData::getData)
              .build();

  private final ChainDataProvider chainDataProvider;

  public GetStateValidator(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateValidator(final ChainDataProvider provider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateValidator")
            .summary("Get validator from state")
            .description("Retrieves data about the given peer.")
            .pathParam(PARAMETER_STATE_ID)
            .pathParam(PARAMETER_VALIDATOR_ID)
            .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = provider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get validator from state",
      tags = {TAG_BEACON, TAG_VALIDATOR_REQUIRED},
      description =
          "Returns validator specified by state and id or public key along with status and balance.",
      pathParams = {
        @OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION),
        @OpenApiParam(name = PARAM_VALIDATOR_ID, description = PARAM_VALIDATOR_DESCRIPTION)
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetStateValidatorResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<StateAndMetaData>> future =
        chainDataProvider.getBeaconStateAndMetadata(request.getPathParameter(PARAMETER_STATE_ID));

    request.respondAsync(
        future.thenApply(
            maybeStateAndMetadata -> {
              if (maybeStateAndMetadata.isEmpty()) {
                return AsyncApiResponse.respondNotFound();
              }

              final String validatorIdParam = request.getPathParameter(PARAMETER_VALIDATOR_ID);
              Optional<ObjectAndMetaData<StateValidatorData>> response =
                  chainDataProvider.getStateValidator(
                      maybeStateAndMetadata.get(), validatorIdParam);

              return response
                  .map(AsyncApiResponse::respondOk)
                  .orElseGet(AsyncApiResponse::respondNotFound);
            }));
  }
}
