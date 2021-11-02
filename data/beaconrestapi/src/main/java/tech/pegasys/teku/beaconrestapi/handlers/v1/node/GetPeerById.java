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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.node.Peer;
import tech.pegasys.teku.api.response.v1.node.PeerResponse;
import tech.pegasys.teku.provider.JsonProvider;

public class GetPeerById implements Handler {
  private static final String OAPI_ROUTE = "/eth/v1/node/peers/:peer_id";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);
  private final JsonProvider jsonProvider;
  private final NetworkDataProvider network;

  public GetPeerById(final DataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.network = provider.getNetworkDataProvider();
  }

  GetPeerById(final NetworkDataProvider network, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.network = network;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get node peer",
      tags = {TAG_NODE},
      description = "Retrieves data about the given peer.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = PeerResponse.class)),
        @OpenApiResponse(status = RES_NOT_FOUND, description = "Peer not found"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    final Map<String, String> parameters = ctx.pathParamMap();
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    Optional<Peer> peer = network.getPeerById(parameters.get("peer_id"));
    if (peer.isEmpty()) {
      ctx.status(SC_NOT_FOUND);
    } else {
      ctx.json(jsonProvider.objectToJSON(new PeerResponse(peer.get())));
    }
  }
}
