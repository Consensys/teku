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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.BadRequest.BAD_REQUEST_TYPE;

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
import tech.pegasys.teku.api.response.v1.beacon.GetStateForkResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Fork;

public class GetStateFork extends MigratingEndpointAdapter {
  private static final String OAPI_ROUTE = "/eth/v1/beacon/states/:state_id/fork";
  public static final String ROUTE = AbstractHandler.routeWithBracedParameters(OAPI_ROUTE);

  private static final SerializableTypeDefinition<StateForkData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(StateForkData.class)
          .name("GetStateForkResponse")
          .description(
              "Returns [Fork](https://github.com/ethereum/consensus-specs/blob/v0.11.1/specs/phase0/beacon-chain.md#fork) object for state with given 'stateId'.")
          .withField("data", Fork.getJsonTypeDefinition(), StateForkData::getData)
          .build();

  private final ChainDataProvider chainDataProvider;

  public GetStateFork(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateFork(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getSateFork")
            .summary("Get state fork")
            .description("Returns Fork object for state with given 'state_id'.")
            .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
            .pathParam(PARAM_STATE_ID, CoreTypes.string(PARAM_STATE_ID_DESCRIPTION, "head"))
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .response(SC_NOT_FOUND, "Not found", BAD_REQUEST_TYPE)
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get state fork",
      tags = {TAG_BEACON, TAG_VALIDATOR_REQUIRED},
      description = "Returns Fork object for state with given 'state_id'.",
      pathParams = {
        @OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION),
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetStateForkResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ObjectAndMetaData<Fork>>> future =
        chainDataProvider.getFork(request.getPathParam(PARAM_STATE_ID));
    request.handleOptionalResult(future, this::handleResult, SC_NOT_FOUND);
  }

  private Optional<String> handleResult(Context ctx, final ObjectAndMetaData<Fork> response)
      throws JsonProcessingException {
    return Optional.of(
        JsonUtil.serialize(
            new StateForkData(response.isExecutionOptimisticForApi(), response.getData()),
            RESPONSE_TYPE));
  }

  static class StateForkData {
    public final Boolean execution_optimistic;
    public final Fork data;

    public StateForkData(final Boolean executionOptimistic, final Fork data) {
      this.execution_optimistic = executionOptimistic;
      this.data = data;
    }

    public Fork getData() {
      return data;
    }
  }
}
