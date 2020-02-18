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
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

public class FinalizedCheckpointHandler implements Handler {

  private final ChainStorageClient client;

  public FinalizedCheckpointHandler(ChainStorageClient client) {
    this.client = client;
  }

  public static final String ROUTE = "/beacon/finalized_checkpoint";

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Finalized checkpoint.",
      tags = {"Node"},
      description = "Requests that the beacon node give finalized checkpoint info.",
      responses = {
        @OpenApiResponse(status = "200", content = @OpenApiContent(from = Checkpoint.class)),
      })
  @Override
  public void handle(@NotNull Context ctx) throws Exception {
    Store store = client.getStore();
    if (store == null) {
      ctx.status(SC_NO_CONTENT);
    }
    Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();
    ctx.result(JsonProvider.objectToJSON(finalizedCheckpoint));
  }
}
