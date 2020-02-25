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
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BeaconStateHandler implements Handler {
  public static final String ROUTE = "/beacon/state";
  public static final String ROOT = "root";
  public static final String SLOT = "slot";
  private final CombinedChainDataClient combinedClient;
  private final JsonProvider jsonProvider;

  public BeaconStateHandler(CombinedChainDataClient combinedClient, JsonProvider jsonProvider) {
    this.combinedClient = combinedClient;
    this.jsonProvider = jsonProvider;
  }

  private Optional<BeaconState> queryByRootHash(String root)
      throws ExecutionException, InterruptedException {
    Bytes32 root32 = Bytes32.fromHexString(root);

    SafeFuture<Optional<BeaconState>> future = combinedClient.getStateAtBlock(root32);
    Optional<BeaconState> result = future.get();
    return result;
  }

  private Optional<BeaconState> queryBySlot(String slotString)
      throws ExecutionException, InterruptedException {
    UnsignedLong slot = UnsignedLong.valueOf(slotString);
    Bytes32 head = combinedClient.getBestBlockRoot().orElse(null);

    SafeFuture<Optional<BeaconState>> future = combinedClient.getStateAtSlot(slot, head);
    Optional<BeaconState> result = future.get();
    return result;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the beacon chain state that matches the specified tree hash root.",
      tags = {"Beacon"},
      queryParams = {
        @OpenApiParam(name = ROOT, description = "Tree hash root to query (Bytes32)"),
        @OpenApiParam(
            name = SLOT,
            description =
                "Query by slot number in the canonical chain (head or ancestor of the head)")
      },
      description =
          "Request that the node return a beacon chain state that matches the specified tree hash root.",
      responses = {
        @OpenApiResponse(status = "200", content = @OpenApiContent(from = BeaconState.class)),
        @OpenApiResponse(
            status = "404",
            description = "The beacon state matching the supplied query parameter was not found."),
        @OpenApiResponse(status = "400", description = "Missing a query parameter")
      })
  private String validateParams(final Map<String, List<String>> params, final String key) {
    if (params.containsKey(key)
        && params.get(key).size() == 1
        && !StringUtils.isEmpty(params.get(key).get(0))) {
      return params.get(key).get(0);
    } else {
      throw new IllegalArgumentException(String.format("'%s' cannot be null or empty.", key));
    }
  }

  @Override
  public void handle(Context ctx) throws Exception {
    try {
      Map<String, List<String>> parameters = ctx.queryParamMap();
      Optional<BeaconState> result = Optional.empty();
      if (parameters.size() == 0) {
        throw new IllegalArgumentException("No query parameters specified");
      }

      if (parameters.containsKey(ROOT)) {
        result = queryByRootHash(validateParams(parameters, ROOT));
      } else if (parameters.containsKey(SLOT)) {
        result = queryBySlot(validateParams(parameters, SLOT));
      }

      if (result.isPresent()) {
        ctx.result(jsonProvider.objectToJSON(result.get()));
        return;
      }
      ctx.status(SC_NOT_FOUND);
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }
}
