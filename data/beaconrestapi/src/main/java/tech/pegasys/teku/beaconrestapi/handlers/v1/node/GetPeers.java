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

import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_NODE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.node.Peer;
import tech.pegasys.teku.api.response.v1.node.PeersResponse;
import tech.pegasys.teku.provider.JsonProvider;

public class GetPeers implements Handler {
  public static final String ROUTE = "/eth/v1/node/peers";
  private final JsonProvider jsonProvider;
  private final NetworkDataProvider network;

  public GetPeers(final DataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.network = provider.getNetworkDataProvider();
  }

  GetPeers(final NetworkDataProvider network, final JsonProvider jsonProvider) {
    this.network = network;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get node peers",
      tags = {TAG_V1_NODE},
      description = "Retrieves data about the node's network peers.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = PeersResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    List<Peer> peers = network.getPeers();
    ctx.result(jsonProvider.objectToJSON(new PeersResponse(peers)));
  }
}
