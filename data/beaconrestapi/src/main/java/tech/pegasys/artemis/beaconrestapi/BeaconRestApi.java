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
import io.javalin.plugin.openapi.jackson.JacksonModelConverterFactory;
import io.javalin.plugin.openapi.ui.SwaggerOptions;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconBlockHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconChainHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconCommitteesHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconStateHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconValidatorsHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.GenesisTimeHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.NodeSyncingHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.VersionHandler;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler.RequestParams;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.ENRHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeerIdHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeersHandler;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class BeaconRestApi {

  private final List<BeaconRestApiHandler> handlers = new ArrayList<>();
  private final Javalin app;
  private final JsonProvider jsonProvider = new JsonProvider();

  private void initialise(
      final ChainStorageClient chainStorageClient,
      final P2PNetwork<?> p2pNetwork,
      final CombinedChainDataClient combinedChainDataClient,
      final SyncService syncService,
      final int requestedPortNumber) {
    app.server().setServerPort(requestedPortNumber);

    addNodeHandlers(chainStorageClient, syncService);
    addBeaconHandlers(chainStorageClient, combinedChainDataClient);
    addNetworkHandlers(p2pNetwork);
    addValidatorHandlers(combinedChainDataClient);
  }

  public BeaconRestApi(
      final ChainStorageClient chainStorageClient,
      final P2PNetwork<?> p2pNetwork,
      final CombinedChainDataClient combinedChainDataClient,
      final SyncService syncService,
      final ArtemisConfiguration configuration) {
    this.app =
        Javalin.create(
            config -> {
              config.registerPlugin(
                  new OpenApiPlugin(getOpenApiOptions(jsonProvider, configuration)));
              config.defaultContentType = "application/json";
            });
    initialise(
        chainStorageClient,
        p2pNetwork,
        combinedChainDataClient,
        syncService,
        configuration.getBeaconRestAPIPortNumber());
  }

  BeaconRestApi(
      final ChainStorageClient chainStorageClient,
      final P2PNetwork<?> p2pNetwork,
      final CombinedChainDataClient combinedChainDataClient,
      final SyncService syncService,
      final ArtemisConfiguration configuration,
      final Javalin app) {
    this.app = app;
    initialise(
        chainStorageClient,
        p2pNetwork,
        combinedChainDataClient,
        syncService,
        configuration.getBeaconRestAPIPortNumber());
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
                    ctx.result(jsonProvider.objectToJSON(response));
                  } else {
                    ctx.status(SC_NOT_FOUND).result(jsonProvider.objectToJSON("Not found"));
                  }
                }));

    app.start();
  }

  private static OpenApiOptions getOpenApiOptions(
      JsonProvider jsonProvider, ArtemisConfiguration config) {
    JacksonModelConverterFactory factory =
        new JacksonModelConverterFactory(jsonProvider.getObjectMapper());

    Info applicationInfo =
        new Info()
            .title(StringUtils.capitalize(VersionProvider.CLIENT_IDENTITY))
            .version(VersionProvider.IMPLEMENTATION_VERSION)
            .description(
                "A minimal API specification for the beacon node, which enables a validator "
                    + "to connect and perform its obligations on the Ethereum 2.0 phase 0 beacon chain.")
            .license(
                new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html"));
    final OpenApiOptions options =
        new OpenApiOptions(applicationInfo).modelConverterFactory(factory);
    if (config.getBeaconRestAPIEnableSwagger()) {
      options.path("/swagger-docs").swagger(new SwaggerOptions("/swagger-ui"));
    }
    return options;
  }

  private void addNodeHandlers(ChainStorageClient chainStorageClient, SyncService syncService) {
    app.get(GenesisTimeHandler.ROUTE, new GenesisTimeHandler(chainStorageClient, jsonProvider));
    app.get(VersionHandler.ROUTE, new VersionHandler(jsonProvider));
    app.get(NodeSyncingHandler.ROUTE, new NodeSyncingHandler(syncService, jsonProvider));
    /*
     * TODO:
     *  Optional:
     *    /node/fork
     */
  }

  private void addBeaconHandlers(
      ChainStorageClient chainStorageClient, CombinedChainDataClient combinedChainDataClient) {
    app.get(
        BeaconBlockHandler.ROUTE, new BeaconBlockHandler(combinedChainDataClient, jsonProvider));
    app.get(
        BeaconChainHeadHandler.ROUTE, new BeaconChainHeadHandler(chainStorageClient, jsonProvider));
    app.get(BeaconHeadHandler.ROUTE, new BeaconHeadHandler(chainStorageClient, jsonProvider));
    app.get(
        BeaconCommitteesHandler.ROUTE,
        new BeaconCommitteesHandler(combinedChainDataClient, jsonProvider));
    app.get(
        BeaconStateHandler.ROUTE, new BeaconStateHandler(combinedChainDataClient, jsonProvider));
  }

  private void addValidatorHandlers(CombinedChainDataClient combinedChainDataClient) {
    app.get(
        BeaconValidatorsHandler.ROUTE,
        new BeaconValidatorsHandler(combinedChainDataClient, jsonProvider));
    /*
     * TODO:
     *   reference: https://ethereum.github.io/eth2.0-APIs/#/
     *   /validator/{pubkey}
     *   /validator/duties
     *   /validator/block (GET/POST)
     *   /validator/attestation (GET/POST)
     **/
  }

  private void addNetworkHandlers(P2PNetwork<?> p2pNetwork) {
    app.get(PeerIdHandler.ROUTE, new PeerIdHandler(p2pNetwork, jsonProvider));
    app.get(PeersHandler.ROUTE, new PeersHandler(p2pNetwork, jsonProvider));

    // not in Minimal or optional specified set
    handlers.add(new ENRHandler());
  }

  public void stop() {
    app.stop();
  }
}
