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
import io.javalin.plugin.openapi.OpenApiOptions;
import io.javalin.plugin.openapi.OpenApiPlugin;
import io.javalin.plugin.openapi.ui.SwaggerOptions;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
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

public class BeaconRestApi {

  private List<BeaconRestApiHandler> handlers = new ArrayList<>();
  private Javalin app;
  private final ChainStorageClient chainStorageClient;
  private final P2PNetwork<?> p2pNetwork;

  @SuppressWarnings("unchecked")
  private static BeaconRestApi beaconRestApi;

  public static BeaconRestApi getInstance(
      ChainStorageClient chainStorageClient,
      P2PNetwork<?> p2pNetwork,
      final int requestedPortNumber) {
    BeaconRestApi.beaconRestApi =
        new BeaconRestApi(chainStorageClient, p2pNetwork, requestedPortNumber);

    return beaconRestApi;
  }

  public static BeaconRestApi getInstance() {
    return beaconRestApi;
  }

  private BeaconRestApi(
      ChainStorageClient chainStorageClient,
      P2PNetwork<?> p2pNetwork,
      final int requestedPortNumber) {
    this.chainStorageClient = chainStorageClient;
    this.p2pNetwork = p2pNetwork;

    app =
        Javalin.create(
            config -> {
              config.registerPlugin(new OpenApiPlugin(getOpenApiOptions()));
              config.defaultContentType = "application/json";
            });
    app.server().setServerPort(requestedPortNumber);

    addNodeHandlers();
    addBeaconHandlers();
    addNetworkHandlers();
    addValidatorHandlers();
  }

  public int getPort() {
    return app.server().getServerPort();
  }

  public void start() {
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

    app.start();
  }

  private OpenApiOptions getOpenApiOptions() {
    Info applicationInfo =
        new Info()
            .version("0.1.0")
            .title("Minimal Beacon Nodea API for Validator")
            .description(
                "A minimal API specification for the beacon node, which enables a validator "
                    + "to connect and perform its obligations on the Ethereum 2.0 phase 0 beacon chain.")
            .license(
                new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html"));
    OpenApiOptions options =
        new OpenApiOptions(applicationInfo)
            .activateAnnotationScanningFor("tech.pegasys.artemis.beaconrestapi")
            .path("/swagger-docs")
            .swagger(new SwaggerOptions("/swagger-ui"));
    return options;
  }

  private void addNodeHandlers() {
    app.get("/node/genesis_time", GenesisTimeHandler::handleRequest);
    /*
     * TODO:
     *    /node/version
     *    /node/syncing
     *  Optional:
     *    /node/fork
     */
  }

  private void addBeaconHandlers() {
    // not in Minimal or optional specified set
    handlers.add(new BeaconHeadHandler(chainStorageClient));
    handlers.add(new BeaconChainHeadHandler(chainStorageClient));
    handlers.add(new BeaconBlockHandler(chainStorageClient));
    handlers.add(new BeaconStateHandler(chainStorageClient));
    handlers.add(new FinalizedCheckpointHandler(chainStorageClient));
  }

  private void addValidatorHandlers() {
    /*
     * TODO:
     *   reference: https://ethereum.github.io/eth2.0-APIs/#/
     *   /validator/{pubkey}
     *   /validator/duties
     *   /validator/block (GET/POST)
     *   /validator/attestation (GET/POST)
     **/
  }

  private void addNetworkHandlers() {
    // not in Minimal or optional specified set
    handlers.add(new PeerIdHandler(p2pNetwork));
    handlers.add(new PeersHandler(p2pNetwork));
    handlers.add(new ENRHandler());
  }

  public ChainStorageClient getChainStorageClient() {
    return chainStorageClient;
  }

  public P2PNetwork<?> getP2pNetwork() {
    return p2pNetwork;
  }

  public void stop() {
    app.stop();
  }
}
