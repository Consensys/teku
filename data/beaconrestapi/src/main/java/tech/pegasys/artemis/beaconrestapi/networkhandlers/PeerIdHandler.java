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

package tech.pegasys.artemis.beaconrestapi.networkhandlers;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;

public class PeerIdHandler implements Handler {

  public static final String ROUTE = "/network/peer_id";

  private final P2PNetwork<?> network;

  public PeerIdHandler(P2PNetwork<?> network) {
    this.network = network;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get this beacon node's PeerId.",
      tags = {"Network"},
      description = "Requests that the beacon node return its PeerID as a base58 encoded String.",
      responses = {
        @OpenApiResponse(status = "200", content = @OpenApiContent(from = String.class))
      })
  @Override
  public void handle(@NotNull Context ctx) throws Exception {
    ctx.result(network.getNodeAddress());
  }
}
