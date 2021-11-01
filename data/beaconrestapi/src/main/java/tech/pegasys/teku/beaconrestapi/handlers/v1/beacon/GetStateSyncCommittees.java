/*
 * Copyright 2021 ConsenSys AG.
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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetStateSyncCommitteesResponse;
import tech.pegasys.teku.api.response.v1.beacon.StateSyncCommittees;
import tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class GetStateSyncCommittees extends AbstractHandler implements Handler {
  private static final String OAPI_ROUTE = "/eth/v1/beacon/states/:state_id/sync_committees";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);
  private final ChainDataProvider chainDataProvider;

  public GetStateSyncCommittees(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getChainDataProvider(), jsonProvider);
  }

  public GetStateSyncCommittees(
      final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get sync committees",
      tags = {TAG_BEACON, TAG_VALIDATOR_REQUIRED},
      description = "Retrieves the sync committees for the given state.",
      pathParams = {
        @OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION),
      },
      queryParams = {@OpenApiParam(name = EPOCH, description = EPOCH_QUERY_DESCRIPTION)},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetStateSyncCommitteesResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    try {
      final Map<String, String> pathParams = ctx.pathParamMap();
      final Map<String, List<String>> queryParameters = ctx.queryParamMap();
      final Optional<UInt64> epoch =
          SingleQueryParameterUtils.getParameterValueAsUInt64IfPresent(queryParameters, EPOCH);

      if (!chainDataProvider.stateParameterMaySupportAltair(pathParams.get(PARAM_STATE_ID))) {
        ctx.status(SC_OK);
        ctx.json(jsonProvider.objectToJSON(List.of()));
      }

      final SafeFuture<Optional<StateSyncCommittees>> future =
          chainDataProvider.getStateSyncCommittees(pathParams.get(PARAM_STATE_ID), epoch);

      handleOptionalResult(
          ctx, future, this::handleResult, this::handleError, SC_SERVICE_UNAVAILABLE);
    } catch (IllegalArgumentException ex) {
      ctx.status(SC_BAD_REQUEST);
      ctx.json(BadRequest.badRequest(jsonProvider, ex.getMessage()));
    }
  }

  private SafeFuture<String> handleError(final Context context, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof IllegalArgumentException) {
      return SafeFuture.of(() -> BadRequest.badRequest(jsonProvider, rootCause.getMessage()));
    } else if (rootCause instanceof IllegalStateException) {
      return SafeFuture.of(() -> BadRequest.badRequest(jsonProvider, rootCause.getMessage()));
    } else {
      return failedFuture(error);
    }
  }

  private Optional<String> handleResult(final Context context, final StateSyncCommittees response)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(new GetStateSyncCommitteesResponse(response)));
  }
}
