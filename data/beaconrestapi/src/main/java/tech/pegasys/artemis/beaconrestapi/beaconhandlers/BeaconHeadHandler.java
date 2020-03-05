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
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_BEACON;

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
  private final JsonProvider jsonProvider;

  private final ChainStorageClient client;

  public BeaconHeadHandler(ChainStorageClient client, JsonProvider jsonProvider) {
    this.client = client;
    this.jsonProvider = jsonProvider;
  }

  private BeaconHeadResponse getBeaconHead() {
    Bytes32 headBlockRoot = client.getBestBlockRoot();
    if (headBlockRoot == null) {
      return null;
    }
    Bytes32 headStateRoot = client.getBestBlockRootState().hash_tree_root();
    return new BeaconHeadResponse(client.getBestSlot(), headBlockRoot, headStateRoot);
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the head of the beacon chain from the node's perspective.",
      tags = {TAG_BEACON},
      description =
          "Returns information about the head of the beacon chain from the node’s perspective.\n\nTo retrieve finalized and justified information use /beacon/chainhead instead.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = BeaconHeadResponse.class)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    BeaconHeadResponse result = getBeaconHead();
    if (result == null) {
      LOG.trace("Failed to get beacon head");
      ctx.status(SC_NO_CONTENT);
    } else {
      ctx.result(jsonProvider.objectToJSON(getBeaconHead()));
    }
  }
}
