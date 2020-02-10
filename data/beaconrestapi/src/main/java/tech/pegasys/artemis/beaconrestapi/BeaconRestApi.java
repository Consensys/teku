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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import io.javalin.Javalin;
import java.util.ArrayList;
import java.util.List;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconBlockHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconChainHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconStateHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.FinalizedCheckpointHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.GenesisTimeHandler;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler.RequestParams;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.ENRHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeerIdHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeersHandler;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;

public class BeaconRestApi {

  private List<BeaconRestApiHandler> handlers = new ArrayList<>();
  private Javalin app;

  public BeaconRestApi(
      ChainStorageClient chainStorageClient,
      P2PNetwork<?> p2pNetwork,
      HistoricalChainData historicalChainData,
      final int requestedPortNumber) {
    app = Javalin.create();
    app.server().setServerPort(requestedPortNumber);

    handlers.add(new GenesisTimeHandler(chainStorageClient));
    handlers.add(new BeaconHeadHandler(chainStorageClient));
    handlers.add(new BeaconChainHeadHandler(chainStorageClient));
    handlers.add(new BeaconBlockHandler(chainStorageClient, historicalChainData));
    handlers.add(new BeaconStateHandler(chainStorageClient));
    handlers.add(new FinalizedCheckpointHandler(chainStorageClient));
    handlers.add(new PeerIdHandler(p2pNetwork));
    handlers.add(new PeersHandler(p2pNetwork));
    handlers.add(new ENRHandler());
  }

  public int getPort() {
    return app.server().getServerPort();
  }

  public void start() {
    app.start();
    handlers.forEach(
        handler ->
            app.get(
                handler.getPath(),
                ctx -> {
                  ctx.contentType("application/json");
                  final Object response = handler.handleRequest(new RequestParams(ctx));
                  if (response != null) {
                    ctx.result(JsonProvider.objectToJSON(response));
                  } else {
                    ctx.status(SC_NOT_FOUND).result(JsonProvider.objectToJSON("Not found"));
                  }
                }));
  }

  public void stop() {
    app.stop();
  }
}
