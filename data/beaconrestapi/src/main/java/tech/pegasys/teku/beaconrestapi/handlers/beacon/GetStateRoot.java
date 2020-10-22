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
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.getMaxAgeForSlot;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUInt64;

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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class GetStateRoot implements Handler {
  public static final String ROUTE = "/beacon/state_root";

  private final ChainDataProvider provider;
  private final JsonProvider jsonProvider;

  public GetStateRoot(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      deprecated = true,
      path = ROUTE,
      method = HttpMethod.GET,
      summary =
          "Get the beacon chain state root for the specified slot.\n"
              + "Deprecated - use `/eth/v1/beacon/states/{state_id}/root` instead.",
      tags = {TAG_BEACON},
      queryParams = {@OpenApiParam(name = SLOT, description = SLOT_QUERY_DESCRIPTION)},
      description = "Returns the beacon chain state root for the specified slot.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = String.class),
            description = "The beacon chain `state_root`(`Bytes32`) for the specified slot."),
        @OpenApiResponse(
            status = RES_NOT_FOUND,
            description = "The beacon state root matching the supplied parameter was not found."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Missing a query parameter."),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      final Map<String, List<String>> parameters = ctx.queryParamMap();
      SafeFuture<Optional<Bytes32>> future = null;
      if (parameters.size() == 0) {
        throw new IllegalArgumentException("No query parameters specified");
      }

      if (parameters.containsKey(SLOT)) {
        UInt64 slot = getParameterValueAsUInt64(parameters, SLOT);
        future = queryBySlot(slot);
        ctx.result(
            future.thenApplyChecked(
                hashTreeRoot -> {
                  if (hashTreeRoot.isEmpty()) {
                    ctx.status(SC_NOT_FOUND);
                    return null;
                  }
                  ctx.header(Header.CACHE_CONTROL, getMaxAgeForSlot(provider, slot));
                  return jsonProvider.objectToJSON(hashTreeRoot.get());
                }));
      } else {
        throw new IllegalArgumentException(SLOT + " parameter was not specified.");
      }
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private SafeFuture<Optional<Bytes32>> queryBySlot(final UInt64 slot) {
    return provider.getStateRootAtSlot(slot);
  }
}
