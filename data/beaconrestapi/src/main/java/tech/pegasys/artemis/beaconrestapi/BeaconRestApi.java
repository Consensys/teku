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
import io.javalin.plugin.openapi.OpenApiOptions;
import io.javalin.plugin.openapi.OpenApiPlugin;
import io.javalin.plugin.openapi.jackson.JacksonModelConverterFactory;
import io.javalin.plugin.openapi.ui.SwaggerOptions;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.DataProvider;
import tech.pegasys.artemis.api.NetworkDataProvider;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconBlockHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconChainHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconCommitteesHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconStateHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconStateRootHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconValidatorsHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.GenesisTimeHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.NodeSyncingHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.VersionHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.ENRHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeerIdHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeersHandler;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class BeaconRestApi {
  private final Javalin app;
  private final JsonProvider jsonProvider = new JsonProvider();

  private void initialise(DataProvider dataProvider, final int requestedPortNumber) {
    initialise(
        dataProvider.getChainStorageClient(),
        dataProvider.getP2pNetwork(),
        dataProvider.getCombinedChainDataClient(),
        requestedPortNumber);

    addNodeHandlers(dataProvider);
    addValidatorHandlers(dataProvider);
  }

  private void initialise(
      final ChainStorageClient chainStorageClient,
      final P2PNetwork<?> p2pNetwork,
      final CombinedChainDataClient combinedChainDataClient,
      final int requestedPortNumber) {
    app.server().setServerPort(requestedPortNumber);
    addBeaconHandlers(chainStorageClient, combinedChainDataClient);
    addNetworkHandlers(new NetworkDataProvider(p2pNetwork));
  }

  public BeaconRestApi(final DataProvider dataProvider, final ArtemisConfiguration configuration) {
    this.app =
        Javalin.create(
            config -> {
              config.registerPlugin(
                  new OpenApiPlugin(getOpenApiOptions(jsonProvider, configuration)));
              config.defaultContentType = "application/json";
            });
    initialise(dataProvider, configuration.getBeaconRestAPIPortNumber());
  }

  BeaconRestApi(
      final DataProvider dataProvider,
      final ArtemisConfiguration configuration,
      final Javalin app) {
    this.app = app;
    initialise(dataProvider, configuration.getBeaconRestAPIPortNumber());
  }

  public void start() {
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

  private void addNodeHandlers(DataProvider provider) {
    app.get(
        GenesisTimeHandler.ROUTE,
        new GenesisTimeHandler(provider.getChainDataProvider(), jsonProvider));
    app.get(VersionHandler.ROUTE, new VersionHandler(jsonProvider));
    app.get(
        NodeSyncingHandler.ROUTE,
        new NodeSyncingHandler(provider.getSyncDataProvider(), jsonProvider));
  }

  private void addBeaconHandlers(
      ChainStorageClient chainStorageClient, CombinedChainDataClient combinedChainDataClient) {
    ChainDataProvider provider = new ChainDataProvider(chainStorageClient, combinedChainDataClient);
    app.get(BeaconBlockHandler.ROUTE, new BeaconBlockHandler(provider, jsonProvider));
    app.get(
        BeaconChainHeadHandler.ROUTE, new BeaconChainHeadHandler(chainStorageClient, jsonProvider));
    app.get(BeaconHeadHandler.ROUTE, new BeaconHeadHandler(provider, jsonProvider));
    app.get(BeaconCommitteesHandler.ROUTE, new BeaconCommitteesHandler(provider, jsonProvider));
    app.get(BeaconStateHandler.ROUTE, new BeaconStateHandler(provider, jsonProvider));
    app.get(
        BeaconStateRootHandler.ROUTE,
        new BeaconStateRootHandler(combinedChainDataClient, jsonProvider));
  }

  private void addValidatorHandlers(DataProvider dataProvider) {
    app.get(
        BeaconValidatorsHandler.ROUTE,
        new BeaconValidatorsHandler(dataProvider.getCombinedChainDataClient(), jsonProvider));
  }

  private void addNetworkHandlers(NetworkDataProvider networkDataProvider) {
    app.get(ENRHandler.ROUTE, new ENRHandler(networkDataProvider, jsonProvider));
    app.get(PeerIdHandler.ROUTE, new PeerIdHandler(networkDataProvider, jsonProvider));
    app.get(PeersHandler.ROUTE, new PeersHandler(networkDataProvider, jsonProvider));
  }

  public void stop() {
    app.stop();
  }
}
