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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_PARTIAL_CONTENT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SYNCING_STATUS;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SYNCING_STATUS_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetHealth extends MigratingEndpointAdapter {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/node/health";
  private final SyncDataProvider syncProvider;
  private final ChainDataProvider chainDataProvider;

  public GetHealth(final DataProvider provider) {
    this(provider.getSyncDataProvider(), provider.getChainDataProvider());
  }

  GetHealth(final SyncDataProvider syncProvider, final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getNodePeers")
            .summary("Get node peers")
            .description("Retrieves data about the node's network peers.")
            .queryParam(SYNCING_STATUS, CoreTypes.string(SYNCING_STATUS_DESCRIPTION))
            .tags(TAG_NODE)
            .response(SC_OK, "Node is ready")
            .response(SC_PARTIAL_CONTENT, "Node is syncing but can serve incomplete data")
            .response(SC_SERVICE_UNAVAILABLE, "Node not initialized or having issues")
            .build());
    this.syncProvider = syncProvider;
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get node health",
      description = "Returns node health status in http status codes. Useful for load balancers.",
      tags = {TAG_NODE},
      queryParams = {
        @OpenApiParam(name = SYNCING_STATUS, description = SYNCING_STATUS_DESCRIPTION)
      },
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
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    if (!chainDataProvider.isStoreAvailable()) {
      request.respondWithCode(SC_SERVICE_UNAVAILABLE);
    } else if (syncProvider.isSyncing()) {
      request.respondWithCode(getResponseCodeFromQueryParams(request));
    } else {
      request.respondWithCode(SC_OK);
    }
  }

  private int getResponseCodeFromQueryParams(final RestApiRequest request) {
    try {
      return request.getQueryParamAsOptionalInteger(SYNCING_STATUS).orElse(SC_PARTIAL_CONTENT);
    } catch (IllegalArgumentException ex) {
      LOG.trace("Illegal parameter in GetHealth", ex);
    }
    return SC_PARTIAL_CONTENT;
  }
}
