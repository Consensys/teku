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
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.node.Identity;
import tech.pegasys.teku.api.response.v1.node.IdentityResponse;
import tech.pegasys.teku.provider.JsonProvider;

public class GetIdentity implements Handler {
  public static final String ROUTE = "/eth/v1/node/identity";
  private final JsonProvider jsonProvider;
  private final NetworkDataProvider network;

  public GetIdentity(final DataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.network = provider.getNetworkDataProvider();
  }

  GetIdentity(final NetworkDataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.network = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get node identity",
      description = "Retrieves data about the node's network presence.",
      tags = {TAG_V1_NODE},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = IdentityResponse.class),
            description = "The identifying information of the node."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    final Identity networkIdentity =
        new Identity(
            network.getNodeIdAsBase58(),
            network.getEnr().orElse(""),
            network.getListeningAddresses(),
            network.getDiscoveryAddresses(),
            network.getMetadata());
    ctx.result(jsonProvider.objectToJSON(new IdentityResponse(networkIdentity)));
  }
}
