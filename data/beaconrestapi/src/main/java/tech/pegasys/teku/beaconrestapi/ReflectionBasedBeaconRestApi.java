/*
 * Copyright ConsenSys Software Inc., 2022
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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.net.InetSocketAddress;
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
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetDepositSnapshot;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetDeposits;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetEth1Data;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetEth1DataCache;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetEth1VotingSummary;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetProposersData;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetProtoArray;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetStateByBlockRoot;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.node.GetPeersScore;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.validatorInclusion.GetGlobalValidatorInclusion;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.validatorInclusion.GetValidatorInclusion;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetAttesterSlashings;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeader;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeaders;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockRoot;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetFinalizedBlockRoot;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetFinalizedCheckpointState;
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
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlindedBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostProposerSlashing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostSyncCommittees;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostVoluntaryExit;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetDepositContract;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetForkSchedule;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetSpec;
import tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetChainHeadsV1;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.GetEvents;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetHealth;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetIdentity;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeerById;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeerCount;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetSyncing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetVersion;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAggregateAttestation;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAttestationData;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetNewBlindedBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetProposerDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetSyncCommitteeContribution;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAggregateAndProofs;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAttesterDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostContributionAndProofs;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostPrepareBeaconProposer;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostRegisterValidator;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSubscribeToBeaconCommitteeSubnet;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSyncCommitteeSubscriptions;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSyncDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostValidatorLiveness;
import tech.pegasys.teku.beaconrestapi.handlers.v2.debug.GetChainHeadsV2;
import tech.pegasys.teku.beaconrestapi.handlers.v2.debug.GetState;
import tech.pegasys.teku.beaconrestapi.handlers.v2.validator.GetNewBlock;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.ContentTypeNotSupportedException;
import tech.pegasys.teku.infrastructure.restapi.DefaultExceptionHandler;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiDocBuilder;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;
import tech.pegasys.teku.validator.coordinator.MissingDepositsException;

public class ReflectionBasedBeaconRestApi implements BeaconRestApi {

  private Server jettyServer;
  private final Javalin app;
  private final JsonProvider jsonProvider = new JsonProvider();
  private static final Logger LOG = LogManager.getLogger();
  private OpenApiDocBuilder openApiDocBuilder;
  private String migratedOpenApi;
  private SchemaDefinitionCache schemaCache;

  private void initialize(
      final DataProvider dataProvider,
      final Eth1DataProvider eth1DataProvider,
      final BeaconRestApiConfig configuration,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final Spec spec) {
    final Info applicationInfo = createApplicationInfo();
    openApiDocBuilder =
        new OpenApiDocBuilder()
            .title(applicationInfo.getTitle())
            .version(applicationInfo.getVersion())
            .description(applicationInfo.getDescription())
            .license(applicationInfo.getLicense().getName(), applicationInfo.getLicense().getUrl());
    if (app._conf != null) {
      // the beaconRestApi test mocks the app object, and will skip this
      app._conf.server(
          () -> {
            final InetSocketAddress address =
                new InetSocketAddress(
                    configuration.getRestApiInterface(), configuration.getRestApiPort());
            jettyServer = new Server(address);
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
    schemaCache = new SchemaDefinitionCache(spec);

    addHostAllowlistHandler(configuration);

    addExceptionHandlers();
    addStandardApiHandlers(
        dataProvider, spec, eventChannels, asyncRunner, timeProvider, configuration);
    addTekuSpecificHandlers(dataProvider, eth1DataProvider, spec);
    migratedOpenApi = openApiDocBuilder.build();
  }

  public String getMigratedOpenApi() {
    return migratedOpenApi;
  }

  private void addStandardApiHandlers(
      final DataProvider dataProvider,
      final Spec spec,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final BeaconRestApiConfig configuration) {
    addBeaconHandlers(dataProvider, spec);
    addEventHandler(dataProvider, eventChannels, asyncRunner, timeProvider, configuration);
    addNodeHandlers(dataProvider);
    addValidatorHandlers(dataProvider, spec);
    addConfigHandlers(dataProvider, configuration.getEth1DepositContractAddress());
    addDebugHandlers(dataProvider, spec);
  }

  private void addConfigHandlers(
      final DataProvider dataProvider, final Eth1Address depositAddress) {
    addMigratedEndpoint(new GetDepositContract(depositAddress, dataProvider.getConfigProvider()));
    addMigratedEndpoint(new GetForkSchedule(dataProvider));
    addMigratedEndpoint(new GetSpec(dataProvider));
  }

  private void addDebugHandlers(final DataProvider dataProvider, final Spec spec) {
    addMigratedEndpoint(new GetChainHeadsV1(dataProvider));
    addMigratedEndpoint(new GetChainHeadsV2(dataProvider));
    addMigratedEndpoint(
        new tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetState(
            dataProvider, spec, schemaCache));
    addMigratedEndpoint(new GetState(dataProvider, schemaCache));
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
    app.exception(ContentTypeNotSupportedException.class, this::unsupportedContentType);
    app.exception(MissingDepositsException.class, this::missingDeposits);
    app.exception(BadRequestException.class, this::badRequest);
    app.exception(JsonProcessingException.class, this::badRequest);
    app.exception(IllegalArgumentException.class, this::badRequest);
    app.exception(Exception.class, new DefaultExceptionHandler<>());
  }

  private void unsupportedContentType(final Throwable throwable, final Context context) {
    context.status(SC_UNSUPPORTED_MEDIA_TYPE);
    setErrorBody(
        context,
        () ->
            BadRequest.serialize(jsonProvider, SC_UNSUPPORTED_MEDIA_TYPE, throwable.getMessage()));
  }

  private void missingDeposits(final Throwable throwable, final Context context) {
    context.status(SC_INTERNAL_SERVER_ERROR);
    setErrorBody(
        context,
        () -> BadRequest.serialize(jsonProvider, SC_INTERNAL_SERVER_ERROR, throwable.getMessage()));
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

  public ReflectionBasedBeaconRestApi(
      final DataProvider dataProvider,
      final Eth1DataProvider eth1DataProvider,
      final BeaconRestApiConfig configuration,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final Spec spec) {
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
    initialize(
        dataProvider,
        eth1DataProvider,
        configuration,
        eventChannels,
        asyncRunner,
        timeProvider,
        spec);
  }

  ReflectionBasedBeaconRestApi(
      final DataProvider dataProvider,
      final Eth1DataProvider eth1DataProvider,
      final BeaconRestApiConfig configuration,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final Javalin app,
      final Spec spec) {
    this.app = app;
    initialize(
        dataProvider,
        eth1DataProvider,
        configuration,
        eventChannels,
        asyncRunner,
        timeProvider,
        spec);
  }

  @Override
  public SafeFuture<?> start() {
    try {
      app.start();
      return SafeFuture.COMPLETE;
    } catch (RuntimeException ex) {
      if (Throwables.getRootCause(ex) instanceof BindException) {
        return SafeFuture.failedFuture(
            new InvalidConfigurationException(
                String.format(
                    "TCP Port %d is already in use. "
                        + "You may need to stop another process or change the HTTP port for this process.",
                    getListenPort())));
      }
    }
    return SafeFuture.COMPLETE;
  }

  @Override
  public int getListenPort() {
    return app.jettyServer().getServerPort();
  }

  private static OpenApiOptions getOpenApiOptions(
      final JsonProvider jsonProvider, final BeaconRestApiConfig config) {
    final JacksonModelConverterFactory factory =
        new JacksonModelConverterFactory(jsonProvider.getObjectMapper());

    final Info applicationInfo = createApplicationInfo();
    final OpenApiOptions options =
        new OpenApiOptions(applicationInfo).modelConverterFactory(factory);
    if (config.isRestApiDocsEnabled()) {
      options.path("/swagger-docs").swagger(new SwaggerOptions("/swagger-ui"));
    }
    return options;
  }

  private static Info createApplicationInfo() {
    return new Info()
        .title(StringUtils.capitalize(VersionProvider.CLIENT_IDENTITY))
        .version(VersionProvider.IMPLEMENTATION_VERSION)
        .description(
            "A minimal API specification for the beacon node, which enables a validator "
                + "to connect and perform its obligations on the Ethereum beacon chain.")
        .license(
            new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0.html"));
  }

  private void addTekuSpecificHandlers(
      final DataProvider provider, final Eth1DataProvider eth1DataProvider, final Spec spec) {
    addMigratedEndpoint(new PutLogLevel());
    addMigratedEndpoint(new GetStateByBlockRoot(provider, spec));
    addMigratedEndpoint(new Liveness(provider));
    addMigratedEndpoint(new Readiness(provider));
    addMigratedEndpoint(new GetAllBlocksAtSlot(provider, schemaCache));
    addMigratedEndpoint(new GetPeersScore(provider));
    addMigratedEndpoint(new GetProtoArray(provider));
    addMigratedEndpoint(new GetProposersData(provider));
    addMigratedEndpoint(new GetDeposits(eth1DataProvider));
    addMigratedEndpoint(new GetEth1Data(provider, eth1DataProvider));
    addMigratedEndpoint(new GetEth1DataCache(eth1DataProvider));
    addMigratedEndpoint(new GetEth1VotingSummary(provider, eth1DataProvider));
    addMigratedEndpoint(new GetDepositSnapshot(eth1DataProvider));
    addMigratedEndpoint(new GetGlobalValidatorInclusion(provider));
    addMigratedEndpoint(new GetValidatorInclusion(provider));
  }

  private void addNodeHandlers(final DataProvider provider) {
    addMigratedEndpoint(new GetHealth(provider));
    addMigratedEndpoint(new GetIdentity(provider));
    addMigratedEndpoint(new GetPeers(provider));
    addMigratedEndpoint(new GetPeerCount(provider));
    addMigratedEndpoint(new GetPeerById(provider));
    addMigratedEndpoint(new GetSyncing(provider));
    addMigratedEndpoint(new GetVersion());
  }

  private void addMigratedEndpoint(final MigratingEndpointAdapter endpoint) {
    endpoint.addEndpoint(app);
    openApiDocBuilder.endpoint(endpoint);
  }

  private void addValidatorHandlers(final DataProvider dataProvider, final Spec spec) {
    addMigratedEndpoint(new PostAttesterDuties(dataProvider));
    addMigratedEndpoint(new GetProposerDuties(dataProvider));
    addMigratedEndpoint(
        new tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetNewBlock(
            dataProvider, schemaCache));
    addMigratedEndpoint(new GetNewBlock(dataProvider, spec, schemaCache));
    addMigratedEndpoint(new GetNewBlindedBlock(dataProvider, spec, schemaCache));
    addMigratedEndpoint(new GetAttestationData(dataProvider));
    addMigratedEndpoint(new GetAggregateAttestation(dataProvider, spec));
    addMigratedEndpoint(
        new PostAggregateAndProofs(dataProvider, spec.getGenesisSchemaDefinitions()));
    addMigratedEndpoint(new PostSubscribeToBeaconCommitteeSubnet(dataProvider));
    addMigratedEndpoint(new PostSyncDuties(dataProvider));
    addMigratedEndpoint(new GetSyncCommitteeContribution(dataProvider, schemaCache));
    addMigratedEndpoint(new PostSyncCommitteeSubscriptions(dataProvider));
    addMigratedEndpoint(new PostContributionAndProofs(dataProvider, schemaCache));
    addMigratedEndpoint(new PostPrepareBeaconProposer(dataProvider));
    addMigratedEndpoint(new PostRegisterValidator(dataProvider));
  }

  private void addBeaconHandlers(final DataProvider dataProvider, final Spec spec) {
    addMigratedEndpoint(new GetGenesis(dataProvider));
    addMigratedEndpoint(new GetStateRoot(dataProvider));
    addMigratedEndpoint(new GetStateFork(dataProvider));
    addMigratedEndpoint(new GetStateFinalityCheckpoints(dataProvider));
    addMigratedEndpoint(new GetStateValidators(dataProvider));
    addMigratedEndpoint(new GetStateValidator(dataProvider));
    addMigratedEndpoint(new GetStateValidatorBalances(dataProvider));
    addMigratedEndpoint(new GetStateCommittees(dataProvider));
    addMigratedEndpoint(new GetStateSyncCommittees(dataProvider));

    addMigratedEndpoint(new GetBlockHeaders(dataProvider));
    addMigratedEndpoint(new GetBlockHeader(dataProvider));

    addMigratedEndpoint(new PostBlock(dataProvider, spec, schemaCache));
    addMigratedEndpoint(new PostBlindedBlock(dataProvider, spec, schemaCache));

    addMigratedEndpoint(new GetBlock(dataProvider, schemaCache));
    addMigratedEndpoint(
        new tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.GetBlock(dataProvider, schemaCache));
    addMigratedEndpoint(new GetFinalizedCheckpointState(dataProvider, schemaCache));

    addMigratedEndpoint(new GetBlockRoot(dataProvider));
    addMigratedEndpoint(new GetFinalizedBlockRoot(dataProvider));
    addMigratedEndpoint(new GetBlockAttestations(dataProvider, spec));

    addMigratedEndpoint(new GetAttestations(dataProvider, spec));
    addMigratedEndpoint(new PostAttestation(dataProvider, schemaCache));

    addMigratedEndpoint(new GetAttesterSlashings(dataProvider, spec));
    addMigratedEndpoint(new PostAttesterSlashing(dataProvider, spec));
    addMigratedEndpoint(new GetProposerSlashings(dataProvider));
    addMigratedEndpoint(new PostProposerSlashing(dataProvider));
    addMigratedEndpoint(new GetVoluntaryExits(dataProvider));
    addMigratedEndpoint(new PostVoluntaryExit(dataProvider));
    addMigratedEndpoint(new PostSyncCommittees(dataProvider));
    addMigratedEndpoint(new PostValidatorLiveness(dataProvider));
  }

  private void addEventHandler(
      final DataProvider dataProvider,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final BeaconRestApiConfig configuration) {
    addMigratedEndpoint(
        new GetEvents(
            dataProvider,
            eventChannels,
            asyncRunner,
            timeProvider,
            configuration.getMaxPendingEvents()));
  }

  @Override
  public SafeFuture<?> stop() {
    try {
      if (jettyServer != null) {
        jettyServer.stop();
      }
    } catch (Exception ex) {
      LOG.error(ex);
      return SafeFuture.failedFuture(ex);
    }
    app.stop();
    return SafeFuture.COMPLETE;
  }
}
