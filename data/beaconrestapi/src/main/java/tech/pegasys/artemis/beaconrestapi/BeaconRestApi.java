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

package tech.pegasys.artemis.beaconrestapi;

import io.javalin.Javalin;
import java.util.ArrayList;
import java.util.List;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconBlockHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconStateHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.GenesisTimeHandler;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.HandlerInterface;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.ENRHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeerIDHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeersHandler;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconRestApi {

  private List<HandlerInterface> handlers = new ArrayList<>();

  public BeaconRestApi(
      ChainStorageClient chainStorageClient, P2PNetwork p2pNetwork, final int portNumber) {
    boolean isLibP2P = false;
    if (p2pNetwork instanceof JvmLibP2PNetwork) isLibP2P = true;
    Javalin app = Javalin.create().start(portNumber);

    handlers.add(new GenesisTimeHandler().init(app, chainStorageClient));
    handlers.add(new BeaconHeadHandler().init(app, chainStorageClient));
    handlers.add(new BeaconBlockHandler().init(app, chainStorageClient));
    handlers.add(new BeaconStateHandler().init(app, chainStorageClient));
    handlers.add(new PeerIDHandler().init(app, p2pNetwork, isLibP2P));
    handlers.add(new PeersHandler().init(app, p2pNetwork, isLibP2P));
    handlers.add(new ENRHandler().init(app, p2pNetwork, isLibP2P));
  }

  public void runHandlers() {
    for (HandlerInterface handler : handlers) {
      handler.run();
    }
  }
}
