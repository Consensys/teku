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

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_VALIDATOR_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.STATUS;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_BEACON;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorsResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.beaconrestapi.ListQueryParameterUtils;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

public class GetStateValidators extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/beacon/states/:state_id/validators";

  private final StateValidatorsUtil stateValidatorsUtil = new StateValidatorsUtil();
  private final ChainDataProvider chainDataProvider;

  public GetStateValidators(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getChainDataProvider(), jsonProvider);
  }

  GetStateValidators(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get validators from state",
      tags = {TAG_V1_BEACON},
      description = "Returns filterable list of validators with their balance, status and index.",
      pathParams = {
        @OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION),
      },
      queryParams = {
        @OpenApiParam(
            name = PARAM_ID,
            description = PARAM_VALIDATOR_DESCRIPTION,
            isRepeatable = true),
        @OpenApiParam(
            name = STATUS,
            description =
                "valid values:   pending_initialized, "
                    + "  pending_queued, "
                    + "  active_ongoing, "
                    + "  active_exiting, "
                    + "  active_slashed, "
                    + "  exited_unslashed, "
                    + "  exited_slashed, "
                    + "  withdrawal_possible, "
                    + "  withdrawal_done",
            isRepeatable = true)
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetStateValidatorsResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    final Map<String, List<String>> queryParamMap = ctx.queryParamMap();
    final Map<String, String> pathParamMap = ctx.pathParamMap();

    final List<String> validators =
        queryParamMap.containsKey(PARAM_ID)
            ? ListQueryParameterUtils.getParameterAsStringList(ctx.queryParamMap(), PARAM_ID)
            : Collections.emptyList();

    final Set<ValidatorStatus> statusFilter = stateValidatorsUtil.parseStatusFilter(queryParamMap);

    SafeFuture<Optional<List<ValidatorResponse>>> future =
        chainDataProvider.getStateValidators(
            pathParamMap.getOrDefault(PARAM_STATE_ID, "head"), validators, statusFilter);

    handleOptionalResult(ctx, future, this::handleResult, SC_NOT_FOUND);
  }

  private Optional<String> handleResult(Context ctx, final List<ValidatorResponse> response)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(new GetStateValidatorsResponse(response)));
  }
}
