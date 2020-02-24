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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BeaconStateHandler implements Handler {
  public static final String ROUTE = "/beacon/state";
  public static final String ROOT_PARAMETER = "root";
  public static final String SLOT_PARAMETER = "slot";
  private final ChainStorageClient client;
  private final CombinedChainDataClient combinedClient;
  private final JsonProvider jsonProvider;

  public BeaconStateHandler(
      ChainStorageClient client,
      CombinedChainDataClient combinedClient,
      JsonProvider jsonProvider) {
    this.client = client;
    this.combinedClient = combinedClient;
    this.jsonProvider = jsonProvider;
  }

  private BeaconState queryByRootHash(String root) throws ExecutionException, InterruptedException {
    Bytes32 root32 = Bytes32.fromHexString(root);

    SafeFuture<Optional<BeaconState>> future = combinedClient.getStateAtBlock(root32);
    Optional<BeaconState> result = future.get();
    return result.orElse(null);
  }

  private BeaconState queryBySlot(UnsignedLong slot)
      throws ExecutionException, InterruptedException {
    Bytes32 head = client.getBestBlockRoot();

    SafeFuture<Optional<BeaconState>> future = combinedClient.getStateAtSlot(slot, head);
    Optional<BeaconState> result = future.get();
    return result.orElse(null);
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the beacon chain state that matches the specified tree hash root.",
      tags = {"Beacon"},
      queryParams = {
        @OpenApiParam(name = ROOT_PARAMETER, description = "Tree hash root to query (Bytes32)"),
        @OpenApiParam(
            name = SLOT_PARAMETER,
            description =
                "Query by slot number in the canonical chain (head or ancestor of the head)")
      },
      description =
          "Request that the node return a beacon chain state that matches the specified tree hash root.",
      responses = {
        @OpenApiResponse(status = "200", content = @OpenApiContent(from = BeaconState.class)),
        @OpenApiResponse(
            status = "404",
            description = "The beacon state matching the supplied query parameter was not found.")
      })
  @Override
  public void handle(Context ctx) throws Exception {
    Map<String, List<String>> parameters = ctx.queryParamMap();
    BeaconState result = null;
    if (parameters.containsKey(ROOT_PARAMETER)) {
      result = queryByRootHash(parameters.get(ROOT_PARAMETER).get(0));
    } else if (parameters.containsKey(SLOT_PARAMETER)) {
      result = queryBySlot(UnsignedLong.valueOf(parameters.get(SLOT_PARAMETER).get(0)));
    }

    if (result == null) {
      ctx.status(SC_NOT_FOUND);
    } else {
      ctx.result(jsonProvider.objectToJSON(result));
    }
  }
}
