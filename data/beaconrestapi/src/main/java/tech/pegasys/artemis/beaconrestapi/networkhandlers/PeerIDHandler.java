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

import io.javalin.Javalin;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.P2PNetworkHandlerInterface;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;

public class PeerIDHandler implements P2PNetworkHandlerInterface {

  private Javalin app;
  private P2PNetwork network;
  private boolean isLibP2P;

  @Override
  public PeerIDHandler init(Javalin app, P2PNetwork network, boolean isLibP2P) {
    this.app = app;
    this.network = network;
    this.isLibP2P = isLibP2P;
    return this;
  }

  @Override
  public void run() {
    app.get(
        "/network/peer_id",
        ctx -> {
          if (isLibP2P) {
            ctx.result(((JvmLibP2PNetwork) network).getPeerIDString());
          } else {
            ctx.result("p2pNetwork not set to libP2P");
          }
        });
  }
}
