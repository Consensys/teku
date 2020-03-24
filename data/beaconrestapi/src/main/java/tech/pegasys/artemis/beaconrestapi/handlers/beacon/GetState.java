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

package tech.pegasys.artemis.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.getMaxAgeForBeaconState;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBytes32;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUnsignedLong;

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
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.BeaconState;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

public class GetState implements Handler {
  public static final String ROUTE = "/beacon/state";

  private final ChainDataProvider provider;
  private final JsonProvider jsonProvider;

  public GetState(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the beacon chain state that matches the specified tree hash root, or slot.",
      tags = {TAG_BEACON},
      queryParams = {
        @OpenApiParam(name = ROOT, description = "Tree hash root to query."),
        @OpenApiParam(name = SLOT, description = "Slot to query in the canonical chain.")
      },
      description =
          "Returns the beacon chain state that matches the specified slot or tree hash root.",
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

      if (parameters.containsKey(ROOT)) {
        future = provider.getStateByBlockRoot(getParameterValueAsBytes32(parameters, ROOT));
      } else if (parameters.containsKey(SLOT)) {
        future = provider.getStateAtSlot(getParameterValueAsUnsignedLong(parameters, SLOT));
      } else {
        ctx.result(
            jsonProvider.objectToJSON(new BadRequest("expected one of " + SLOT + " or " + ROOT)));
        ctx.status(SC_BAD_REQUEST);
        return;
      }
      ctx.result(future.thenApplyChecked(state -> handleResponseContext(ctx, state)));
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private String handleResponseContext(Context ctx, final Optional<BeaconState> stateOptional)
      throws JsonProcessingException {
    if (stateOptional.isEmpty()) {
      ctx.status(SC_NOT_FOUND);
      return null;
    }
    BeaconState beaconState = stateOptional.get();
    ctx.header(Header.CACHE_CONTROL, getMaxAgeForBeaconState(provider, beaconState));
    return jsonProvider.objectToJSON(beaconState);
  }
}
