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

import static io.javalin.core.util.Header.CACHE_CONTROL;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_NODE;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

public class FinalizedCheckpointHandler implements Handler {

  private final ChainStorageClient client;
  private final JsonProvider jsonProvider;

  public FinalizedCheckpointHandler(ChainStorageClient client, JsonProvider jsonProvider) {
    this.client = client;
    this.jsonProvider = jsonProvider;
  }

  public static final String ROUTE = "/beacon/finalized_checkpoint";

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Finalized checkpoint.",
      tags = {TAG_NODE},
      description = "Requests that the beacon node give finalized checkpoint info.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = Checkpoint.class)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    ctx.header(CACHE_CONTROL, CACHE_NONE);
    Store store = client.getStore();
    if (store == null) {
      ctx.status(SC_NO_CONTENT);
      return;
    }
    Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();
    ctx.result(jsonProvider.objectToJSON(finalizedCheckpoint));
  }
}
