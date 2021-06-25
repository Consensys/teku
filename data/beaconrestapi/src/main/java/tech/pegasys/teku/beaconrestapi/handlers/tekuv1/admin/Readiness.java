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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_TEKU;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;

public class Readiness implements Handler {
  public static final String ROUTE = "/teku/v1/admin/readiness";
  private final SyncDataProvider syncProvider;
  private final ChainDataProvider chainDataProvider;

  public Readiness(final DataProvider provider) {
    this.syncProvider = provider.getSyncDataProvider();
    this.chainDataProvider = provider.getChainDataProvider();
  }

  Readiness(final SyncDataProvider syncProvider, final ChainDataProvider chainDataProvider) {
    this.syncProvider = syncProvider;
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get node readiness",
      description = "Returns 200 if the node is ready to accept traffic",
      tags = {TAG_TEKU},
      responses = {
        @OpenApiResponse(status = RES_OK, description = "Node is ready"),
        @OpenApiResponse(
            status = RES_SERVICE_UNAVAILABLE,
            description = "Node not initialized or having issues")
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    if (!chainDataProvider.isStoreAvailable() || syncProvider.isSyncing()) {
      ctx.status(SC_SERVICE_UNAVAILABLE);
    } else {
      ctx.status(SC_OK);
    }
  }
}
