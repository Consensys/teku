/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.getMaxAgeForBeaconState;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.ROOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.STATE_ROOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.STATE_ROOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBytes32;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUInt64;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
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
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class GetState extends AbstractHandler implements Handler {
  public static final String ROUTE = "/beacon/state";

  private final ChainDataProvider provider;

  public GetState(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = provider;
  }

  @OpenApi(
      deprecated = true,
      path = ROUTE,
      method = HttpMethod.GET,
      summary =
          "Get the beacon chain state matching the criteria.\n"
              + "Deprecated - use `/eth/v1/debug/states/{state_id}` instead.",
      tags = {TAG_BEACON},
      queryParams = {
        @OpenApiParam(name = ROOT, description = ROOT_QUERY_DESCRIPTION),
        @OpenApiParam(name = SLOT, description = SLOT_QUERY_DESCRIPTION),
        @OpenApiParam(name = STATE_ROOT, description = STATE_ROOT_QUERY_DESCRIPTION)
      },
      description = "Returns the beacon chain state that matches the specified slot or root.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = BeaconState.class)),
        @OpenApiResponse(
            status = RES_NOT_FOUND,
            description = "The beacon state matching the supplied query parameter was not found."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Missing a query parameter"),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      final Map<String, List<String>> parameters = ctx.queryParamMap();
      final SafeFuture<Optional<BeaconState>> future;
      if (parameters.size() == 0) {
        throw new IllegalArgumentException("No query parameters specified");
      }

      boolean isFinalizedState = false;
      if (parameters.containsKey(ROOT)) {
        future = provider.getStateByBlockRoot(getParameterValueAsBytes32(parameters, ROOT));
      } else if (parameters.containsKey(SLOT)) {
        final UInt64 slot = getParameterValueAsUInt64(parameters, SLOT);
        future = provider.getStateAtSlot(getParameterValueAsUInt64(parameters, SLOT));
        isFinalizedState = provider.isFinalized(slot);
      } else if (parameters.containsKey(STATE_ROOT)) {
        future = provider.getStateByStateRoot(getParameterValueAsBytes32(parameters, STATE_ROOT));
      } else {
        ctx.result(
            jsonProvider.objectToJSON(
                new BadRequest("expected one of " + SLOT + ", " + ROOT + ", or " + STATE_ROOT)));
        ctx.status(SC_BAD_REQUEST);
        return;
      }
      if (isFinalizedState) {
        this.handlePossiblyGoneResult(ctx, future, this::handleResult);
      } else {
        this.handlePossiblyMissingResult(ctx, future, this::handleResult);
      }
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private Optional<String> handleResult(Context ctx, final BeaconState beaconState)
      throws JsonProcessingException {
    ctx.header(Header.CACHE_CONTROL, getMaxAgeForBeaconState(provider, beaconState));
    return Optional.of(jsonProvider.objectToJSON(beaconState));
  }
}
