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

import static tech.pegasys.teku.infrastructure.http.HostAllowlistUtils.isHostAuthorized;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import com.google.common.base.Throwables;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.plugin.openapi.OpenApiOptions;
import io.javalin.plugin.openapi.OpenApiPlugin;
import io.javalin.plugin.openapi.jackson.JacksonModelConverterFactory;
import io.javalin.plugin.openapi.ui.SwaggerOptions;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import java.net.BindException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin.Liveness;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin.PutLogLevel;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin.Readiness;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetAllBlocksAtSlot;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetSszState;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetStateByBlockRoot;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.node.GetPeersScore;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetAttesterSlashings;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeader;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeaders;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockRoot;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetGenesis;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetProposerSlashings;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateCommittees;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFinalityCheckpoints;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFork;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateRoot;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateSyncCommittees;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidator;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidatorBalances;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidators;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetVoluntaryExits;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostAttestation;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostAttesterSlashing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostProposerSlashing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostSyncCommittees;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostVoluntaryExit;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetDepositContract;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetForkSchedule;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetSpec;
import tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetChainHeads;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.GetEvents;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetHealth;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetIdentity;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeerById;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeerCount;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAggregateAttestation;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAttestationData;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetProposerDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetSyncCommitteeContribution;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAggregateAndProofs;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAttesterDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostContributionAndProofs;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostPrepareBeaconProposer;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSubscribeToBeaconCommitteeSubnet;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSyncCommitteeSubscriptions;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSyncDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostValidatorLiveness;
import tech.pegasys.teku.beaconrestapi.handlers.v2.debug.GetState;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.validator.api.NodeSyncingException;

public class BeaconRestApi {

  private Server jettyServer;
  private final Javalin app;
  private final JsonProvider jsonProvider = new JsonProvider();
  private static final Logger LOG = LogManager.getLogger();

  private void initialize(
      final DataProvider dataProvider,
      final BeaconRestApiConfig configuration,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner) {
    if (app._conf != null) {
      // the beaconRestApi test mocks the app object, and will skip this
      app._conf.server(
          () -> {
            jettyServer = new Server(configuration.getRestApiPort());
            LOG.debug("Setting Max URL length to {}", configuration.getMaxUrlLength());
            for (Connector c : jettyServer.getConnectors()) {
              final HttpConfiguration httpConfiguration =
                  c.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();

              if (httpConfiguration != null) {
                httpConfiguration.setRequestHeaderSize(configuration.getMaxUrlLength());
              }
            }
            return jettyServer;
          });
    }
    app.jettyServer().setServerHost(configuration.getRestApiInterface());
    app.jettyServer().setServerPort(configuration.getRestApiPort());

    addHostAllowlistHandler(configuration);

    addExceptionHandlers();
    addStandardApiHandlers(dataProvider, eventChannels, asyncRunner, configuration);
    addTekuSpecificHandlers(dataProvider);
  }

  private void addStandardApiHandlers(
      final DataProvider dataProvider,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner,
      final BeaconRestApiConfig configuration) {
    addBeaconHandlers(dataProvider);
    addEventHandler(dataProvider, eventChannels, asyncRunner, configuration);
    addNodeHandlers(dataProvider);
    addValidatorHandlers(dataProvider);
    addConfigHandlers(dataProvider, configuration.getEth1DepositContractAddress());
    addDebugHandlers(dataProvider);
  }

  private void addConfigHandlers(
      final DataProvider dataProvider, final Eth1Address depositAddress) {
    app.get(
        GetDepositContract.ROUTE,
        new GetDepositContract(depositAddress, jsonProvider, dataProvider.getConfigProvider()));
    app.get(GetForkSchedule.ROUTE, new GetForkSchedule(dataProvider, jsonProvider));
    app.get(GetSpec.ROUTE, new GetSpec(dataProvider, jsonProvider));
  }

  private void addDebugHandlers(final DataProvider dataProvider) {
    app.get(GetChainHeads.ROUTE, new GetChainHeads(dataProvider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetState.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetState(dataProvider, jsonProvider));
    app.get(GetState.ROUTE, new GetState(dataProvider, jsonProvider));
  }

  private void addHostAllowlistHandler(final BeaconRestApiConfig configuration) {
    app.before(
        (ctx) -> {
          String header = ctx.host();
          if (!isHostAuthorized(configuration.getRestApiHostAllowlist(), header)) {
            LOG.debug("Host not authorized " + header);
            throw new ForbiddenResponse("Host not authorized");
          }
        });
  }

  private void addExceptionHandlers() {
    app.exception(ChainDataUnavailableException.class, (e, ctx) -> ctx.status(SC_NO_CONTENT));
    app.exception(NodeSyncingException.class, this::serviceUnavailable);
    app.exception(ServiceUnavailableException.class, this::serviceUnavailable);
    app.exception(BadRequestException.class, this::badRequest);
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

  private void serviceUnavailable(final Throwable throwable, final Context context) {
    context.status(SC_SERVICE_UNAVAILABLE);
    setErrorBody(context, () -> BadRequest.serviceUnavailable(jsonProvider));
  }

  private void badRequest(final Throwable throwable, final Context context) {
    context.status(SC_BAD_REQUEST);
    setErrorBody(context, () -> BadRequest.badRequest(jsonProvider, throwable.getMessage()));
  }

  private void setErrorBody(final Context ctx, final ExceptionThrowingSupplier<String> body) {
    try {
      ctx.json(body.get());
    } catch (final Throwable e) {
      LOG.error("Failed to serialize internal server error response", e);
    }
  }

  public BeaconRestApi(
      final DataProvider dataProvider,
      final BeaconRestApiConfig configuration,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner) {
    this.app =
        Javalin.create(
            config -> {
              config.registerPlugin(
                  new OpenApiPlugin(getOpenApiOptions(jsonProvider, configuration)));
              config.defaultContentType = "application/json";
              config.showJavalinBanner = false;
              if (configuration.getRestApiCorsAllowedOrigins() != null
                  && !configuration.getRestApiCorsAllowedOrigins().isEmpty()) {
                if (configuration.getRestApiCorsAllowedOrigins().contains("*")) {
                  config.enableCorsForAllOrigins();
                } else {
                  config.enableCorsForOrigin(
                      configuration.getRestApiCorsAllowedOrigins().toArray(new String[0]));
                }
              }
            });
    initialize(dataProvider, configuration, eventChannels, asyncRunner);
  }

  BeaconRestApi(
      final DataProvider dataProvider,
      final BeaconRestApiConfig configuration,
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
    return app.jettyServer().getServerPort();
  }

  private static OpenApiOptions getOpenApiOptions(
      final JsonProvider jsonProvider, final BeaconRestApiConfig config) {
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

  private void addTekuSpecificHandlers(final DataProvider provider) {
    app.put(PutLogLevel.ROUTE, new PutLogLevel(jsonProvider));
    app.get(GetSszState.ROUTE, new GetSszState(provider, jsonProvider));
    app.get(GetStateByBlockRoot.ROUTE, new GetStateByBlockRoot(provider, jsonProvider));
    app.get(Liveness.ROUTE, new Liveness());
    app.get(Readiness.ROUTE, new Readiness(provider, jsonProvider));
    app.get(GetAllBlocksAtSlot.ROUTE, new GetAllBlocksAtSlot(provider, jsonProvider));
    app.get(GetPeersScore.ROUTE, new GetPeersScore(provider, jsonProvider));
  }

  private void addNodeHandlers(final DataProvider provider) {
    app.get(GetHealth.ROUTE, new GetHealth(provider));
    app.get(GetIdentity.ROUTE, new GetIdentity(provider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers(provider, jsonProvider));
    app.get(GetPeerCount.ROUTE, new GetPeerCount(provider, jsonProvider));
    app.get(GetPeerById.ROUTE, new GetPeerById(provider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetSyncing.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetSyncing(provider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetVersion.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetVersion(jsonProvider));
  }

  private void addValidatorHandlers(final DataProvider dataProvider) {
    app.post(PostAttesterDuties.ROUTE, new PostAttesterDuties(dataProvider, jsonProvider));
    app.get(GetProposerDuties.ROUTE, new GetProposerDuties(dataProvider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetNewBlock.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetNewBlock(
            dataProvider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v2.validator.GetNewBlock.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v2.validator.GetNewBlock(
            dataProvider, jsonProvider));
    app.get(GetAttestationData.ROUTE, new GetAttestationData(dataProvider, jsonProvider));
    app.get(GetAggregateAttestation.ROUTE, new GetAggregateAttestation(dataProvider, jsonProvider));
    app.post(PostAggregateAndProofs.ROUTE, new PostAggregateAndProofs(dataProvider, jsonProvider));
    app.post(
        PostSubscribeToBeaconCommitteeSubnet.ROUTE,
        new PostSubscribeToBeaconCommitteeSubnet(dataProvider, jsonProvider));
    app.post(PostSyncDuties.ROUTE, new PostSyncDuties(dataProvider, jsonProvider));
    app.get(
        GetSyncCommitteeContribution.ROUTE,
        new GetSyncCommitteeContribution(dataProvider, jsonProvider));
    app.post(
        PostSyncCommitteeSubscriptions.ROUTE,
        new PostSyncCommitteeSubscriptions(dataProvider, jsonProvider));
    app.post(
        PostContributionAndProofs.ROUTE, new PostContributionAndProofs(dataProvider, jsonProvider));
    app.post(
        PostPrepareBeaconProposer.ROUTE, new PostPrepareBeaconProposer(dataProvider, jsonProvider));
  }

  private void addBeaconHandlers(final DataProvider dataProvider) {
    app.get(GetGenesis.ROUTE, new GetGenesis(dataProvider, jsonProvider));
    app.get(GetStateRoot.ROUTE, new GetStateRoot(dataProvider, jsonProvider));
    app.get(GetStateFork.ROUTE, new GetStateFork(dataProvider, jsonProvider));
    app.get(
        GetStateFinalityCheckpoints.ROUTE,
        new GetStateFinalityCheckpoints(dataProvider, jsonProvider));
    app.get(GetStateValidators.ROUTE, new GetStateValidators(dataProvider, jsonProvider));
    app.get(GetStateValidator.ROUTE, new GetStateValidator(dataProvider, jsonProvider));
    app.get(
        GetStateValidatorBalances.ROUTE, new GetStateValidatorBalances(dataProvider, jsonProvider));
    app.get(GetStateCommittees.ROUTE, new GetStateCommittees(dataProvider, jsonProvider));
    app.get(GetStateSyncCommittees.ROUTE, new GetStateSyncCommittees(dataProvider, jsonProvider));

    app.get(GetBlockHeaders.ROUTE, new GetBlockHeaders(dataProvider, jsonProvider));
    app.get(GetBlockHeader.ROUTE, new GetBlockHeader(dataProvider, jsonProvider));

    app.post(PostBlock.ROUTE, new PostBlock(dataProvider, jsonProvider));

    app.get(GetBlock.ROUTE, new GetBlock(dataProvider, jsonProvider));
    app.get(
        tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.GetBlock.ROUTE,
        new tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.GetBlock(
            dataProvider, jsonProvider));

    app.get(GetBlockRoot.ROUTE, new GetBlockRoot(dataProvider, jsonProvider));
    app.get(GetBlockAttestations.ROUTE, new GetBlockAttestations(dataProvider, jsonProvider));

    app.get(GetAttestations.ROUTE, new GetAttestations(dataProvider, jsonProvider));
    app.post(PostAttestation.ROUTE, new PostAttestation(dataProvider, jsonProvider));

    app.get(GetAttesterSlashings.ROUTE, new GetAttesterSlashings(dataProvider, jsonProvider));
    app.post(PostAttesterSlashing.ROUTE, new PostAttesterSlashing(dataProvider, jsonProvider));
    app.get(GetProposerSlashings.ROUTE, new GetProposerSlashings(dataProvider, jsonProvider));
    app.post(PostProposerSlashing.ROUTE, new PostProposerSlashing(dataProvider, jsonProvider));
    app.get(GetVoluntaryExits.ROUTE, new GetVoluntaryExits(dataProvider, jsonProvider));
    app.post(PostVoluntaryExit.ROUTE, new PostVoluntaryExit(dataProvider, jsonProvider));
    app.post(PostSyncCommittees.ROUTE, new PostSyncCommittees(dataProvider, jsonProvider));
    app.post(PostValidatorLiveness.ROUTE, new PostValidatorLiveness(dataProvider, jsonProvider));
  }

  private void addEventHandler(
      final DataProvider dataProvider,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner,
      final BeaconRestApiConfig configuration) {
    app.get(
        GetEvents.ROUTE,
        new GetEvents(
            dataProvider,
            jsonProvider,
            eventChannels,
            asyncRunner,
            configuration.getMaxPendingEvents()));
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
