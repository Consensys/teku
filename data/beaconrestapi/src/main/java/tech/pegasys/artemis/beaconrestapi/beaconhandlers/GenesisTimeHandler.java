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

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class GenesisTimeHandler implements Handler {
  private final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/node/genesis_time/";
  ChainStorageClient chainStorageClient;

  public GenesisTimeHandler(ChainStorageClient chainStorageClient) {
    this.chainStorageClient = chainStorageClient;
  }

  @OpenApi(
      path = GenesisTimeHandler.ROUTE,
      method = HttpMethod.GET,
      summary = "Get the genesis_time parameter from beacon node configuration.",
      tags = {"MinimalSet"},
      description =
          "Requests the genesis_time parameter from the beacon node, which should be consistent across all beacon nodes that follow the same beacon chain.",
      responses = {
        @OpenApiResponse(status = "200", content = @OpenApiContent(from = UnsignedLong.class)),
        @OpenApiResponse(status = "500")
      })
  @Override
  public void handle(@NotNull Context ctx) throws Exception {
    try {
      UnsignedLong result = chainStorageClient.getGenesisTime();
      ctx.result(JsonProvider.objectToJSON(result));
    } catch (Exception exception) {
      LOG.error("Failed to get genesis time", exception);
      ctx.status(SC_INTERNAL_SERVER_ERROR);
    }
  }
}
