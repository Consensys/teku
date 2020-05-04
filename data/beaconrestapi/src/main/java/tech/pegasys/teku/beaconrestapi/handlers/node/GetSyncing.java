/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.node;

import static io.javalin.core.util.Header.CACHE_CONTROL;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_NODE;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.beaconrestapi.schema.SyncingResponse;
import tech.pegasys.teku.provider.JsonProvider;

public class GetSyncing implements Handler {

  private final SyncDataProvider syncDataProvider;

  public GetSyncing(SyncDataProvider syncDataProvider, JsonProvider jsonProvider) {
    this.syncDataProvider = syncDataProvider;
    this.jsonProvider = jsonProvider;
  }

  public static final String ROUTE = "/node/syncing";
  private final JsonProvider jsonProvider;

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get synchronization status.",
      tags = {TAG_NODE},
      description =
          "Returns an object with data about the synchronization status, or false if not synchronizing.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = SyncingResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    ctx.header(CACHE_CONTROL, CACHE_NONE);
    ctx.result(jsonProvider.objectToJSON(new SyncingResponse(syncDataProvider.getSyncStatus())));
  }
}
