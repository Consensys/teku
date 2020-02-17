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

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconHeadResponse;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconHeadHandler implements Handler {
  private final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/beacon/head";

  private final ChainStorageClient client;

  public BeaconHeadHandler(ChainStorageClient client) {
    this.client = client;
  }

  private BeaconHeadResponse getBeaconHead() {
    Bytes32 headBlockRoot = client.getBestBlockRoot();
    if (headBlockRoot == null) {
      return null;
    }
    Bytes32 headStateRoot = client.getBestBlockRootState().hash_tree_root();
    return BeaconHeadResponse.builder()
        .best_slot(client.getBestSlot().longValue())
        .block_root(headBlockRoot.toHexString())
        .state_root(headStateRoot.toHexString())
        .build();
  }

  @OpenApi(
      path = BeaconHeadHandler.ROUTE,
      method = HttpMethod.GET,
      summary = "Get the head of the beacon chain from the nodes perspective.",
      tags = {"Beacon"},
      description = "Requests the context of the best slot and head block from the beacon node.",
      responses = {
        @OpenApiResponse(
            status = "200",
            content = @OpenApiContent(from = BeaconHeadResponse.class)),
        @OpenApiResponse(status = "204")
      })
  @Override
  public void handle(Context ctx) throws Exception {
    BeaconHeadResponse result = getBeaconHead();
    if (result == null) {
      LOG.debug("Failed to get beacon head");
      ctx.status(SC_NO_CONTENT);
    } else {
      ctx.result(JsonProvider.objectToJSON(getBeaconHead()));
    }
  }
}
