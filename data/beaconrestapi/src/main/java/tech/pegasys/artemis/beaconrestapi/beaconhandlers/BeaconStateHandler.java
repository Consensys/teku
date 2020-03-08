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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.artemis.beaconrestapi.RestApiUtils.validateQueryParameter;

import com.google.common.primitives.UnsignedLong;
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
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BeaconStateHandler implements Handler {
  public static final String ROUTE = "/beacon/state";

  private final CombinedChainDataClient combinedClient;
  private final JsonProvider jsonProvider;

  public BeaconStateHandler(
      final CombinedChainDataClient combinedClient, final JsonProvider jsonProvider) {
    this.combinedClient = combinedClient;
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
      SafeFuture<Optional<BeaconState>> future = null;
      if (parameters.size() == 0) {
        throw new IllegalArgumentException("No query parameters specified");
      }
      if (!combinedClient.isStoreAvailable()) {
        ctx.status(SC_NO_CONTENT);
        return;
      }

      if (parameters.containsKey(ROOT)) {
        future = queryByRootHash(validateQueryParameter(parameters, ROOT));
      } else if (parameters.containsKey(SLOT)) {
        future = queryBySlot(validateQueryParameter(parameters, SLOT));
      } else {
        ctx.result(
            jsonProvider.objectToJSON(new BadRequest("expected one of " + SLOT + " or " + ROOT)));
        ctx.status(SC_BAD_REQUEST);
        return;
      }
      ctx.result(
          future.thenApplyChecked(
              state -> {
                if (state.isEmpty()) {
                  ctx.status(SC_NOT_FOUND);
                  return null;
                }
                return jsonProvider.objectToJSON(state.get());
              }));
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private SafeFuture<Optional<BeaconState>> queryByRootHash(final String root) {
    final Bytes32 root32 = Bytes32.fromHexString(root);
    return combinedClient.getStateByBlockRoot(root32);
  }

  private SafeFuture<Optional<BeaconState>> queryBySlot(final String slotString) {
    final UnsignedLong slot = UnsignedLong.valueOf(slotString);
    final Bytes32 head = combinedClient.getBestBlockRoot().orElse(null);
    return combinedClient.getStateAtSlot(slot, head);
  }
}
