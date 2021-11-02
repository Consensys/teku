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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetStateForkResponse;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

public class GetStateFork extends AbstractHandler implements Handler {
  private static final String OAPI_ROUTE = "/eth/v1/beacon/states/:state_id/fork";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);
  private final ChainDataProvider chainDataProvider;

  public GetStateFork(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = dataProvider.getChainDataProvider();
  }

  GetStateFork(final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
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
    final Map<String, String> pathParamMap = ctx.pathParamMap();
    final SafeFuture<Optional<Fork>> future =
        chainDataProvider.getStateFork(pathParamMap.get(PARAM_STATE_ID));
    handleOptionalResult(ctx, future, this::handleResult, SC_NOT_FOUND);
  }

  private Optional<String> handleResult(Context ctx, final Fork response)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(new GetStateForkResponse(response)));
  }
}
