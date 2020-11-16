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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_PARTIAL_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_PARTIAL_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_NODE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;

public class GetHealth implements Handler {
  public static final String ROUTE = "/eth/v1/node/health";
  private final SyncDataProvider syncProvider;
  private final ChainDataProvider chainDataProvider;

  public GetHealth(final DataProvider provider) {
    this.syncProvider = provider.getSyncDataProvider();
    this.chainDataProvider = provider.getChainDataProvider();
  }

  GetHealth(final SyncDataProvider syncProvider, final ChainDataProvider chainDataProvider) {
    this.syncProvider = syncProvider;
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get node health",
      description = "Returns node health status in http status codes. Useful for load balancers.",
      tags = {TAG_V1_NODE},
      responses = {
        @OpenApiResponse(status = RES_OK, description = "Node is ready"),
        @OpenApiResponse(
            status = RES_PARTIAL_CONTENT,
            description = "Node is syncing but can serve incomplete data"),
        @OpenApiResponse(
            status = RES_SERVICE_UNAVAILABLE,
            description = "Node not initialized or having issues")
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    if (!chainDataProvider.isStoreAvailable()) {
      ctx.status(SC_SERVICE_UNAVAILABLE);
    } else if (syncProvider.isSyncing()) {
      ctx.status(SC_PARTIAL_CONTENT);
    } else {
      ctx.status(SC_OK);
    }
  }
}
