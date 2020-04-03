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

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.google.common.io.Resources;
import io.javalin.Javalin;
import io.javalin.plugin.openapi.OpenApiOptions;
import io.javalin.plugin.openapi.OpenApiPlugin;
import io.javalin.plugin.openapi.jackson.JacksonModelConverterFactory;
import io.javalin.plugin.openapi.ui.SwaggerOptions;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import java.io.IOException;
import java.nio.charset.Charset;
import kotlin.text.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.DataProvider;
import tech.pegasys.artemis.api.NetworkDataProvider;
import tech.pegasys.artemis.api.ValidatorDataProvider;
import tech.pegasys.artemis.beaconrestapi.handlers.admin.PutLogLevel;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetBlock;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetChainHead;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetCommittees;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetHead;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetState;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetStateRoot;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetValidators;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.PostValidators;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetEthereumNameRecord;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetListenAddresses;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetListenPort;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetPeerCount;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetPeerId;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetPeers;
import tech.pegasys.artemis.beaconrestapi.handlers.node.GetFork;
import tech.pegasys.artemis.beaconrestapi.handlers.node.GetGenesisTime;
import tech.pegasys.artemis.beaconrestapi.handlers.node.GetSyncing;
import tech.pegasys.artemis.beaconrestapi.handlers.node.GetVersion;
import tech.pegasys.artemis.beaconrestapi.handlers.validator.GetAttestation;
import tech.pegasys.artemis.beaconrestapi.handlers.validator.GetNewBlock;
import tech.pegasys.artemis.beaconrestapi.handlers.validator.PostAttestation;
import tech.pegasys.artemis.beaconrestapi.handlers.validator.PostDuties;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.client.ChainDataUnavailableException;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class BeaconRestApi {
  private final Javalin app;
  private final JsonProvider jsonProvider = new JsonProvider();
  private static final Logger LOG = LogManager.getLogger();
  public static final String FILE_NOT_FOUND_HTML = "404.html";

  private void initialise(
      final DataProvider dataProvider, final ArtemisConfiguration configuration) {
    app.server().setServerPort(configuration.getRestApiPort());

    addExceptionHandlers();
    addAdminHandlers();
    addBeaconHandlers(dataProvider);
    addNetworkHandlers(dataProvider.getNetworkDataProvider());
    addNodeHandlers(dataProvider);
    addValidatorHandlers(dataProvider);
    addCustomErrorPages(configuration);
  }

  private void addCustomErrorPages(final ArtemisConfiguration configuration) {
    if (configuration.isRestApiDocsEnabled()) {
      try {
        String content = readResource(FILE_NOT_FOUND_HTML, Charsets.UTF_8);
        app.error(
            SC_NOT_FOUND,
            ctx -> {
              ctx.result(content);
              ctx.contentType("text/html");
            });
      } catch (IOException ex) {
        LOG.error("Could not read custom " + FILE_NOT_FOUND_HTML, ex);
      }
    }
  }

  private String readResource(final String fileName, Charset charset) throws IOException {
    return Resources.toString(Resources.getResource(fileName), charset);
  }

  private void addExceptionHandlers() {
    app.exception(
        ChainDataUnavailableException.class,
        (e, ctx) -> {
          ctx.status(SC_NO_CONTENT);
        });
    // Add catch-all handler
    app.exception(
        Exception.class,
        (e, ctx) -> {
          ctx.status(SC_INTERNAL_SERVER_ERROR);
        });
  }

  public BeaconRestApi(final DataProvider dataProvider, final ArtemisConfiguration configuration) {
    this.app =
        Javalin.create(
            config -> {
              config.registerPlugin(
                  new OpenApiPlugin(getOpenApiOptions(jsonProvider, configuration)));
              config.defaultContentType = "application/json";
            });
    initialise(dataProvider, configuration);
  }

  BeaconRestApi(
      final DataProvider dataProvider,
      final ArtemisConfiguration configuration,
      final Javalin app) {
    this.app = app;
    initialise(dataProvider, configuration);
  }

  public void start() {
    app.start();
  }

  public int getListenPort() {
    return app.server().getServerPort();
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
    if (config.isRestApiDocsEnabled()) {
      options.path("/swagger-docs").swagger(new SwaggerOptions("/swagger-ui"));
    }
    return options;
  }

  private void addAdminHandlers() {
    app.put(PutLogLevel.ROUTE, new PutLogLevel(jsonProvider));
  }

  private void addNodeHandlers(final DataProvider provider) {
    app.get(GetFork.ROUTE, new GetFork(provider.getChainDataProvider(), jsonProvider));
    app.get(
        GetGenesisTime.ROUTE, new GetGenesisTime(provider.getChainDataProvider(), jsonProvider));
    app.get(GetSyncing.ROUTE, new GetSyncing(provider.getSyncDataProvider(), jsonProvider));
    app.get(GetVersion.ROUTE, new GetVersion(jsonProvider));
  }

  private void addBeaconHandlers(final DataProvider dataProvider) {
    final ChainDataProvider provider = dataProvider.getChainDataProvider();
    app.get(GetBlock.ROUTE, new GetBlock(provider, jsonProvider));
    app.get(GetChainHead.ROUTE, new GetChainHead(provider, jsonProvider));
    app.get(GetHead.ROUTE, new GetHead(provider, jsonProvider));
    app.get(GetCommittees.ROUTE, new GetCommittees(provider, jsonProvider));
    app.get(GetState.ROUTE, new GetState(provider, jsonProvider));
    app.get(GetStateRoot.ROUTE, new GetStateRoot(provider, jsonProvider));

    app.post(PostValidators.ROUTE, new PostValidators(provider, jsonProvider));
  }

  private void addValidatorHandlers(DataProvider dataProvider) {
    ChainDataProvider provider = dataProvider.getChainDataProvider();
    ValidatorDataProvider validatorDataProvider = dataProvider.getValidatorDataProvider();
    app.get(GetAttestation.ROUTE, new GetAttestation(validatorDataProvider, jsonProvider));
    app.get(GetValidators.ROUTE, new GetValidators(provider, jsonProvider));
    app.get(GetNewBlock.ROUTE, new GetNewBlock(dataProvider, jsonProvider));

    app.post(PostAttestation.ROUTE, new PostAttestation(dataProvider, jsonProvider));
    app.post(PostDuties.ROUTE, new PostDuties(validatorDataProvider, jsonProvider));
  }

  private void addNetworkHandlers(NetworkDataProvider networkDataProvider) {
    app.get(
        GetEthereumNameRecord.ROUTE, new GetEthereumNameRecord(networkDataProvider, jsonProvider));
    app.get(GetListenAddresses.ROUTE, new GetListenAddresses(networkDataProvider, jsonProvider));
    app.get(GetPeerId.ROUTE, new GetPeerId(networkDataProvider, jsonProvider));
    app.get(GetPeers.ROUTE, new GetPeers(networkDataProvider, jsonProvider));
    app.get(GetPeerCount.ROUTE, new GetPeerCount(networkDataProvider, jsonProvider));
    app.get(GetListenPort.ROUTE, new GetListenPort(networkDataProvider, jsonProvider));
  }

  public void stop() {
    app.stop();
  }
}
