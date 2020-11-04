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

package tech.pegasys.teku.beaconrestapi;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.HostAllowlistUtils.isHostAuthorized;

import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.plugin.openapi.OpenApiOptions;
import io.javalin.plugin.openapi.OpenApiPlugin;
import io.javalin.plugin.openapi.jackson.JacksonModelConverterFactory;
import io.javalin.plugin.openapi.ui.SwaggerOptions;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import java.io.IOException;
import java.net.BindException;
import java.nio.charset.Charset;
import java.util.Optional;
import kotlin.text.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.beaconrestapi.handlers.admin.PutLogLevel;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetBlock;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetChainHead;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetCommittees;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetHead;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetState;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetStateRoot;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetValidators;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.PostValidators;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetEthereumNameRecord;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetListenAddresses;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetListenPort;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetPeerCount;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetPeerId;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetPeers;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetAttestationsInPoolCount;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetFork;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetGenesisTime;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetSyncing;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetVersion;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetAttesterSlashings;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeader;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeaders;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetGenesis;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetProposerSlashings;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateCommittees;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFinalityCheckpoints;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFork;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidator;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidatorBalances;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidators;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetVoluntaryExits;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostAttestationData;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetDepositContract;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetSpec;
import tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetChainHeads;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.GetEvents;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetHealth;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetIdentity;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeerById;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAggregateAttestation;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAttestationData;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAttesterDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetProposerDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAggregateAndProofs;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAttesterDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSubscribeToBeaconCommitteeSubnet;
import tech.pegasys.teku.beaconrestapi.handlers.validator.GetAggregate;
import tech.pegasys.teku.beaconrestapi.handlers.validator.GetAttestation;
import tech.pegasys.teku.beaconrestapi.handlers.validator.GetNewBlock;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostAggregateAndProof;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostAttestation;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostBlock;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostDuties;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostSubscribeToBeaconCommittee;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostSubscribeToPersistentSubnets;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.util.cli.VersionProvider;
import tech.pegasys.teku.util.config.Eth1Address;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.validator.api.NodeSyncingException;

public class BeaconRestApi {

  private Server jettyServer;
  private final Javalin app;
  private final JsonProvider jsonProvider = new JsonProvider();
  private static final Logger LOG = LogManager.getLogger();
  public static final String FILE_NOT_FOUND_HTML = "404.html";

  private void initialize(
      final DataProvider dataProvider,
      final GlobalConfiguration configuration,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner) {
    if (app.config != null) {
      // the beaconRestApi test mocks the app object, and will skip this
      app.config.server(
          () -> {
            jettyServer = new Server(configuration.getRestApiPort());
            return jettyServer;
          });
    }
    app.server().setServerHost(configuration.getRestApiInterface());
    app.server().setServerPort(configuration.getRestApiPort());

    addHostAllowlistHandler(configuration);

    addExceptionHandlers();
    // standard api endpoint inclusion
    addV1BeaconHandlers(dataProvider);
    addEventHandler(dataProvider, eventChannels, asyncRunner);
    addV1NodeHandlers(dataProvider);
    addV1ValidatorHandlers(dataProvider);
    addV1ConfigHandlers(configuration.getEth1DepositContractAddress());
    addV1DebugHandlers(dataProvider);

    // Endpoints from before standard API
    addAdminHandlers();
    addBeaconHandlers(dataProvider);
    addNetworkHandlers(dataProvider.getNetworkDataProvider());
    addNodeHandlers(dataProvider);
    addValidatorHandlers(dataProvider);
    addCustomErrorPages(configuration);
  }

  private void addV1ConfigHandlers(final Eth1Address depositAddress) {
    app.get(GetSpec.ROUTE, new GetSpec(jsonProvider));
    app.get(
        GetDepositContract.ROUTE,
        new GetDepositContract(Optional.ofNullable(depositAddress), jsonProvider));
  }

  private void addV1DebugHandlers(final DataProvider dataProvider) {
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetState.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetState(dataProvider, jsonProvider));
    app.get(GetChainHeads.ROUTE, new GetChainHeads(dataProvider, jsonProvider));
  }

  private void addHostAllowlistHandler(final GlobalConfiguration configuration) {
    app.before(
        (ctx) -> {
          String header = ctx.host();
          if (!isHostAuthorized(configuration.getRestApiHostAllowlist(), header)) {
            LOG.debug("Host not authorized " + header);
            throw new ForbiddenResponse("Host not authorized");
          }
        });
  }

  private void addCustomErrorPages(final GlobalConfiguration configuration) {
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
    app.exception(ChainDataUnavailableException.class, (e, ctx) -> ctx.status(SC_NO_CONTENT));
    app.exception(
        NodeSyncingException.class,
        (e, ctx) -> {
          ctx.status(SC_SERVICE_UNAVAILABLE);
          setErrorBody(ctx, () -> BadRequest.serviceUnavailable(jsonProvider));
        });
    app.exception(
        BadRequestException.class,
        (e, ctx) -> {
          ctx.status(SC_BAD_REQUEST);
          setErrorBody(ctx, () -> BadRequest.badRequest(jsonProvider, e.getMessage()));
        });
    // Add catch-all handler
    app.exception(
        Exception.class,
        (e, ctx) -> {
          LOG.error("Failed to process request to URL {}", ctx.url(), e);
          ctx.status(SC_INTERNAL_SERVER_ERROR);
          setErrorBody(
              ctx, () -> BadRequest.internalError(jsonProvider, "An unexpected error occurred"));
        });
  }

  private void setErrorBody(final Context ctx, final ExceptionThrowingSupplier<String> body) {
    try {
      ctx.result(body.get());
    } catch (final Throwable e) {
      LOG.error("Failed to serialize internal server error response", e);
    }
  }

  public BeaconRestApi(
      final DataProvider dataProvider,
      final GlobalConfiguration configuration,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner) {
    this.app =
        Javalin.create(
            config -> {
              config.registerPlugin(
                  new OpenApiPlugin(getOpenApiOptions(jsonProvider, configuration)));
              config.defaultContentType = "application/json";
              config.logIfServerNotStarted = false;
              config.showJavalinBanner = false;
            });
    initialize(dataProvider, configuration, eventChannels, asyncRunner);
  }

  BeaconRestApi(
      final DataProvider dataProvider,
      final GlobalConfiguration configuration,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner,
      final Javalin app) {
    this.app = app;
    initialize(dataProvider, configuration, eventChannels, asyncRunner);
  }

  public void start() {
    try {
      app.start();
    } catch (RuntimeException ex) {
      if (Throwables.getRootCause(ex) instanceof BindException) {
        throw new InvalidConfigurationException(
            String.format(
                "TCP Port %d is already in use. "
                    + "You may need to stop another process or change the HTTP port for this process.",
                getListenPort()));
      }
    }
  }

  public int getListenPort() {
    return app.server().getServerPort();
  }

  private static OpenApiOptions getOpenApiOptions(
      final JsonProvider jsonProvider, final GlobalConfiguration config) {
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

  private void addV1NodeHandlers(final DataProvider provider) {
    app.get(GetHealth.ROUTE, new GetHealth(provider));
    app.get(GetIdentity.ROUTE, new GetIdentity(provider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers(provider, jsonProvider));
    app.get(GetPeerById.ROUTE, new GetPeerById(provider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetSyncing.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetSyncing(provider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetVersion.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetVersion(jsonProvider));
  }

  private void addV1ValidatorHandlers(final DataProvider dataProvider) {
    app.post(PostAttesterDuties.ROUTE, new PostAttesterDuties(dataProvider, jsonProvider));
    app.post(PostAttestationData.ROUTE, new PostAttestationData(dataProvider, jsonProvider));
    app.post(PostAggregateAndProofs.ROUTE, new PostAggregateAndProofs(dataProvider, jsonProvider));
    app.post(
        PostSubscribeToBeaconCommitteeSubnet.ROUTE,
        new PostSubscribeToBeaconCommitteeSubnet(dataProvider, jsonProvider));

    app.get(GetAttesterDuties.ROUTE, new GetAttesterDuties(dataProvider, jsonProvider));
    app.get(GetProposerDuties.ROUTE, new GetProposerDuties(dataProvider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetNewBlock.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetNewBlock(
            dataProvider, jsonProvider));
    app.get(GetAttestationData.ROUTE, new GetAttestationData(dataProvider, jsonProvider));
    app.get(GetAggregateAttestation.ROUTE, new GetAggregateAttestation(dataProvider, jsonProvider));
  }

  private void addV1BeaconHandlers(final DataProvider dataProvider) {
    // Block header
    app.get(GetBlockHeader.ROUTE, new GetBlockHeader(dataProvider, jsonProvider));
    app.get(GetBlockHeaders.ROUTE, new GetBlockHeaders(dataProvider, jsonProvider));

    app.get(GetGenesis.ROUTE, new GetGenesis(dataProvider, jsonProvider));

    // State
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateRoot.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateRoot(
            dataProvider, jsonProvider));
    app.get(GetStateFork.ROUTE, new GetStateFork(dataProvider, jsonProvider));
    app.get(
        GetStateFinalityCheckpoints.ROUTE,
        new GetStateFinalityCheckpoints(dataProvider, jsonProvider));
    app.get(GetStateValidator.ROUTE, new GetStateValidator(dataProvider, jsonProvider));
    app.get(GetStateValidators.ROUTE, new GetStateValidators(dataProvider, jsonProvider));
    app.get(
        GetStateValidatorBalances.ROUTE, new GetStateValidatorBalances(dataProvider, jsonProvider));
    app.get(GetStateCommittees.ROUTE, new GetStateCommittees(dataProvider, jsonProvider));
    app.post(
        tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlock.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlock(
            dataProvider, jsonProvider));

    // Pool
    app.get(GetAttestations.ROUTE, new GetAttestations(dataProvider, jsonProvider));
    app.get(GetAttesterSlashings.ROUTE, new GetAttesterSlashings(dataProvider, jsonProvider));
    app.get(GetProposerSlashings.ROUTE, new GetProposerSlashings(dataProvider, jsonProvider));
    app.get(GetVoluntaryExits.ROUTE, new GetVoluntaryExits(dataProvider, jsonProvider));
  }

  private void addEventHandler(
      final DataProvider dataProvider,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner) {
    app.get(GetEvents.ROUTE, new GetEvents(dataProvider, jsonProvider, eventChannels, asyncRunner));
  }

  private void addNodeHandlers(final DataProvider provider) {
    app.get(GetFork.ROUTE, new GetFork(provider.getChainDataProvider(), jsonProvider));
    app.get(
        GetGenesisTime.ROUTE, new GetGenesisTime(provider.getChainDataProvider(), jsonProvider));
    app.get(GetSyncing.ROUTE, new GetSyncing(provider.getSyncDataProvider(), jsonProvider));
    app.get(GetVersion.ROUTE, new GetVersion(jsonProvider));
    app.get(
        GetAttestationsInPoolCount.ROUTE,
        new GetAttestationsInPoolCount(provider.getNodeDataProvider(), jsonProvider));
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
    final ChainDataProvider provider = dataProvider.getChainDataProvider();
    final ValidatorDataProvider validatorDataProvider = dataProvider.getValidatorDataProvider();
    app.get(GetAttestation.ROUTE, new GetAttestation(validatorDataProvider, jsonProvider));
    app.get(GetValidators.ROUTE, new GetValidators(provider, jsonProvider));
    app.get(GetNewBlock.ROUTE, new GetNewBlock(dataProvider, jsonProvider));
    app.get(GetAggregate.ROUTE, new GetAggregate(validatorDataProvider, jsonProvider));

    app.post(PostAttestation.ROUTE, new PostAttestation(dataProvider, jsonProvider));
    app.post(
        PostBlock.ROUTE,
        new PostBlock(validatorDataProvider, dataProvider.getSyncDataProvider(), jsonProvider));
    app.post(PostDuties.ROUTE, new PostDuties(validatorDataProvider, jsonProvider));
    app.post(
        PostAggregateAndProof.ROUTE,
        new PostAggregateAndProof(validatorDataProvider, jsonProvider));
    app.post(
        PostSubscribeToBeaconCommittee.ROUTE,
        new PostSubscribeToBeaconCommittee(validatorDataProvider, jsonProvider));
    app.post(
        PostSubscribeToPersistentSubnets.ROUTE,
        new PostSubscribeToPersistentSubnets(validatorDataProvider, jsonProvider));
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
    try {
      if (jettyServer != null) {
        jettyServer.stop();
      }
    } catch (Exception ex) {
      LOG.error(ex);
    }
    app.stop();
  }
}
