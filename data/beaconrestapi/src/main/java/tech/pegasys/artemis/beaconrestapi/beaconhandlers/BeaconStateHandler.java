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

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconStateResponse;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconStateHandler implements Handler {
  public static final String ROUTE = "/beacon/state/";
  private final Logger LOG = LogManager.getLogger();
  private final ChainStorageClient client;

  public BeaconStateHandler(ChainStorageClient client) {
    this.client = client;
  }

  private BeaconState queryByRootHash(String root) {
    Bytes32 root32 = Bytes32.fromHexString(root);
    return client.getStore().getBlockState(root32);
  }

  @OpenApi(
      path = BeaconStateHandler.ROUTE,
      method = HttpMethod.GET,
      summary = "Get the beacon chain state that matches the specified tree hash root.",
      tags = {"beacon"},
      queryParams = {@OpenApiParam(name = "root")},
      description =
          "Request that the node return a beacon chain state that matches the provided criteria.",
      responses = {
        @OpenApiResponse(
            status = "200",
            content = @OpenApiContent(from = BeaconStateResponse.class)),
        @OpenApiResponse(status = "404")
      })
  @Override
  public void handle(Context ctx) throws Exception {
    String rootParam = ctx.queryParam("root");
    BeaconState result = queryByRootHash(rootParam);
    if (result == null) {
      LOG.debug("Block root {} not found", rootParam);
      ctx.status(SC_NOT_FOUND);
    } else {
      ctx.result(JsonProvider.objectToJSON(result));
    }
  }
}
