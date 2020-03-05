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
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_NODE;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class GenesisTimeHandler implements Handler {
  private final Logger LOG = LogManager.getLogger();
  private final JsonProvider jsonProvider;
  public static final String ROUTE = "/node/genesis_time/";
  private final ChainStorageClient chainStorageClient;

  public GenesisTimeHandler(ChainStorageClient chainStorageClient, JsonProvider jsonProvider) {
    this.chainStorageClient = chainStorageClient;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = GenesisTimeHandler.ROUTE,
      method = HttpMethod.GET,
      summary = "Get the genesis time from the beacon node.",
      tags = {TAG_NODE},
      description = "Returns the genesis time from the beacon node.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = UnsignedLong.class)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      UnsignedLong result = chainStorageClient.getGenesisTime();
      ctx.result(jsonProvider.objectToJSON(result));
    } catch (Exception exception) {
      LOG.debug("Failed to get genesis time", exception);
      ctx.status(SC_NO_CONTENT);
    }
  }
}
