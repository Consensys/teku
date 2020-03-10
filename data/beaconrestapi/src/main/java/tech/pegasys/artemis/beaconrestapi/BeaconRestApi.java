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
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconChainHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconStateHandler;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetBlock;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetCommittees;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetHead;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetStateRoot;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetValidators;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetEthereumNameRecord;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetPeerCount;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetPeerId;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetPeers;
import tech.pegasys.artemis.beaconrestapi.handlers.node.GetGenesisTime;
import tech.pegasys.artemis.beaconrestapi.handlers.node.GetSyncing;
import tech.pegasys.artemis.beaconrestapi.handlers.node.GetVersion;
import tech.pegasys.artemis.beaconrestapi.handlers.validator.GetAttestation;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class BeaconRestApi {
  private final Javalin app;
  private final JsonProvider jsonProvider = new JsonProvider();

  private void initialise(final DataProvider dataProvider, final int requestedPortNumber) {
    app.server().setServerPort(requestedPortNumber);

    addBeaconHandlers(dataProvider);
    addNetworkHandlers(new NetworkDataProvider(dataProvider.getP2pNetwork()));
    addNodeHandlers(dataProvider);
    addValidatorHandlers(dataProvider);
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
      final JsonProvider jsonProvider, final ArtemisConfiguration config) {
    final JacksonModelConverterFactory factory =
        new JacksonModelConverterFactory(jsonProvider.getObjectMapper());

    final Info applicationInfo =
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

  private void addNodeHandlers(final DataProvider provider) {
    app.get(
        GetGenesisTime.ROUTE, new GetGenesisTime(provider.getChainDataProvider(), jsonProvider));
    app.get(GetVersion.ROUTE, new GetVersion(jsonProvider));
    app.get(GetSyncing.ROUTE, new GetSyncing(provider.getSyncDataProvider(), jsonProvider));
  }

  private void addBeaconHandlers(final DataProvider dataProvider) {
    final ChainStorageClient chainStorageClient = dataProvider.getChainStorageClient();
    final CombinedChainDataClient combinedChainDataClient =
            dataProvider.getCombinedChainDataClient();
    final ChainDataProvider provider = new ChainDataProvider(chainStorageClient, combinedChainDataClient);
    app.get(GetBlock.ROUTE, new GetBlock(provider, jsonProvider));
    app.get(
        BeaconChainHeadHandler.ROUTE, new BeaconChainHeadHandler(dataProvider.getChainDataProvider(), jsonProvider));
    app.get(GetHead.ROUTE, new GetHead(provider, jsonProvider));
    app.get(GetCommittees.ROUTE, new GetCommittees(provider, jsonProvider));
    app.get(BeaconStateHandler.ROUTE, new BeaconStateHandler(provider, jsonProvider));
    app.get(GetStateRoot.ROUTE, new GetStateRoot(provider, jsonProvider));
  }

  private void addValidatorHandlers(DataProvider dataProvider) {
    ChainDataProvider provider = dataProvider.getChainDataProvider();
    app.get(GetAttestation.ROUTE, new GetAttestation(provider, jsonProvider));
    app.get(GetValidators.ROUTE, new GetValidators(provider, jsonProvider));
  }

  private void addNetworkHandlers(NetworkDataProvider networkDataProvider) {
    app.get(
        GetEthereumNameRecord.ROUTE, new GetEthereumNameRecord(networkDataProvider, jsonProvider));
    app.get(GetPeerId.ROUTE, new GetPeerId(networkDataProvider, jsonProvider));
    app.get(GetPeers.ROUTE, new GetPeers(networkDataProvider, jsonProvider));
    app.get(GetPeerCount.ROUTE, new GetPeerCount(networkDataProvider, jsonProvider));
  }

  public void stop() {
    app.stop();
  }
}
