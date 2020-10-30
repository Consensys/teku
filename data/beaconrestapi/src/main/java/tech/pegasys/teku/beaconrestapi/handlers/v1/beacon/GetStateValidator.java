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

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_VALIDATOR_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_VALIDATOR_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_BEACON;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

public class GetStateValidator extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/beacon/states/:state_id/validators/:validator_id";
  private final ChainDataProvider chainDataProvider;

  public GetStateValidator(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = dataProvider.getChainDataProvider();
  }

  GetStateValidator(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get validator from state",
      tags = {TAG_V1_BEACON, TAG_VALIDATOR_REQUIRED},
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
  public void handle(final Context ctx) throws Exception {
    final Map<String, String> pathParamMap = ctx.pathParamMap();
    SafeFuture<Optional<ValidatorResponse>> future =
        chainDataProvider.getStateValidator(
            pathParamMap.get(PARAM_STATE_ID), pathParamMap.get(PARAM_VALIDATOR_ID));
    handleOptionalResult(ctx, future, this::handleResult, SC_NOT_FOUND);
  }

  private Optional<String> handleResult(Context ctx, final ValidatorResponse response)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(new GetStateValidatorResponse(response)));
  }
}
