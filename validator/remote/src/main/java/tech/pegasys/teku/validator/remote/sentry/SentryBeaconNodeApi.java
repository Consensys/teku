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

package tech.pegasys.teku.validator.remote.sentry;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.beaconnode.TimeBasedEventAdapter;
import tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel;
import tech.pegasys.teku.validator.remote.BeaconNodeReadinessManager;
import tech.pegasys.teku.validator.remote.FailoverValidatorApiHandler;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeEndpoints;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeSyncingChannel;
import tech.pegasys.teku.validator.remote.RemoteValidatorApiChannel;
import tech.pegasys.teku.validator.remote.RemoteValidatorApiHandler;
import tech.pegasys.teku.validator.remote.eventsource.EventSourceBeaconChainEventAdapter;

public class SentryBeaconNodeApi implements BeaconNodeApi {

  private static final Logger LOG = LogManager.getLogger();

  private final BeaconChainEventAdapter beaconChainEventAdapter;
  private final ValidatorApiChannel validatorApiChannel;

  private SentryBeaconNodeApi(
      final BeaconChainEventAdapter beaconChainEventAdapter,
      final ValidatorApiChannel validatorApiChannel) {
    this.beaconChainEventAdapter = beaconChainEventAdapter;
    this.validatorApiChannel = validatorApiChannel;
  }

  public static BeaconNodeApi create(
      final ServiceConfig serviceConfig,
      final ValidatorConfig validatorConfig,
      final AsyncRunner asyncRunner,
      final Spec spec,
      final SentryNodesConfig sentryNodesConfig) {
    final BeaconNodesSentryConfig beaconNodesSentryConfig =
        sentryNodesConfig.getBeaconNodesSentryConfig();
    final OkHttpClient sentryNodesHttpClient =
        beaconNodesSentryConfig.createOkHttpClientForSentryNodes();

    final BeaconNodeRoleConfig dutiesProviderNodeConfig =
        beaconNodesSentryConfig.getDutiesProviderNodeConfig();
    final RemoteBeaconNodeEndpoints dutiesProviderHttpClient =
        new RemoteBeaconNodeEndpoints(dutiesProviderNodeConfig.getEndpointsAsURIs());

    final RemoteValidatorApiChannel dutiesProviderPrimaryValidatorApiChannel =
        createPrimaryValidatorApiChannel(
            validatorConfig, dutiesProviderHttpClient, sentryNodesHttpClient, spec, asyncRunner);
    final List<RemoteValidatorApiChannel> dutiesProviderFailoverValidatorApiChannel =
        createFailoverValidatorApiChannel(
            validatorConfig, dutiesProviderHttpClient, sentryNodesHttpClient, spec, asyncRunner);

    final EventChannels eventChannels = serviceConfig.getEventChannels();
    final MetricsSystem metricsSystem = serviceConfig.getMetricsSystem();

    final RemoteBeaconNodeSyncingChannel remoteBeaconNodeSyncingChannel =
        eventChannels.getPublisher(RemoteBeaconNodeSyncingChannel.class);

    final ValidatorTimingChannel validatorTimingChannel =
        eventChannels.getPublisher(ValidatorTimingChannel.class);

    final BeaconNodeReadinessManager beaconNodeReadinessManager =
        new BeaconNodeReadinessManager(
            dutiesProviderPrimaryValidatorApiChannel,
            dutiesProviderFailoverValidatorApiChannel,
            remoteBeaconNodeSyncingChannel);

    eventChannels.subscribe(ValidatorTimingChannel.class, beaconNodeReadinessManager);

    final ValidatorApiChannel dutiesProviderValidatorApi =
        new MetricRecordingValidatorApiChannel(
            serviceConfig.getMetricsSystem(),
            new FailoverValidatorApiHandler(
                beaconNodeReadinessManager,
                dutiesProviderPrimaryValidatorApiChannel,
                dutiesProviderFailoverValidatorApiChannel,
                validatorConfig.isFailoversSendSubnetSubscriptionsEnabled(),
                serviceConfig.getMetricsSystem()));

    final Optional<ValidatorApiChannel> blockHandlerValidatorApi =
        beaconNodesSentryConfig
            .getBlockHandlerNodeConfig()
            .map(
                c ->
                    createRemoteValidatorApiForRole(
                        validatorConfig,
                        beaconNodeReadinessManager,
                        c.getEndpointsAsURIs(),
                        sentryNodesHttpClient,
                        spec,
                        asyncRunner,
                        metricsSystem));

    final Optional<ValidatorApiChannel> attestationPublisherValidatorApi =
        beaconNodesSentryConfig
            .getAttestationPublisherConfig()
            .map(
                c ->
                    createRemoteValidatorApiForRole(
                        validatorConfig,
                        beaconNodeReadinessManager,
                        c.getEndpointsAsURIs(),
                        sentryNodesHttpClient,
                        spec,
                        asyncRunner,
                        metricsSystem));

    final ValidatorApiChannel sentryValidatorApi =
        new SentryValidatorApiChannel(
            dutiesProviderValidatorApi, blockHandlerValidatorApi, attestationPublisherValidatorApi);

    // Event adapter must listen only to duties provider events
    final EventSourceBeaconChainEventAdapter beaconChainEventAdapter =
        new EventSourceBeaconChainEventAdapter(
            beaconNodeReadinessManager,
            dutiesProviderPrimaryValidatorApiChannel,
            dutiesProviderFailoverValidatorApiChannel,
            sentryNodesHttpClient,
            ValidatorLogger.VALIDATOR_LOGGER,
            new TimeBasedEventAdapter(
                new GenesisDataProvider(asyncRunner, dutiesProviderValidatorApi),
                new RepeatingTaskScheduler(asyncRunner, serviceConfig.getTimeProvider()),
                serviceConfig.getTimeProvider(),
                validatorTimingChannel,
                spec),
            validatorTimingChannel,
            serviceConfig.getMetricsSystem(),
            validatorConfig.generateEarlyAttestations());

    eventChannels.subscribe(RemoteBeaconNodeSyncingChannel.class, beaconChainEventAdapter);

    return new SentryBeaconNodeApi(beaconChainEventAdapter, sentryValidatorApi);
  }

  private static RemoteValidatorApiChannel createPrimaryValidatorApiChannel(
      final ValidatorConfig validatorConfig,
      final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints,
      final OkHttpClient httpClient,
      final Spec spec,
      final AsyncRunner asyncRunner) {
    return RemoteValidatorApiHandler.create(
        remoteBeaconNodeEndpoints.getPrimaryEndpoint(),
        httpClient,
        spec,
        validatorConfig.isValidatorClientUseSszBlocksEnabled(),
        asyncRunner);
  }

  private static List<RemoteValidatorApiChannel> createFailoverValidatorApiChannel(
      final ValidatorConfig validatorConfig,
      final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints,
      final OkHttpClient httpClient,
      final Spec spec,
      final AsyncRunner asyncRunner) {
    final List<RemoteValidatorApiChannel> failoverValidatorApis =
        remoteBeaconNodeEndpoints.getFailoverEndpoints().stream()
            .map(
                endpoint ->
                    RemoteValidatorApiHandler.create(
                        endpoint,
                        httpClient,
                        spec,
                        validatorConfig.isValidatorClientUseSszBlocksEnabled(),
                        asyncRunner))
            .collect(Collectors.toList());

    if (!remoteBeaconNodeEndpoints.getFailoverEndpoints().isEmpty()) {
      LOG.info(
          "Will use {} as failover Beacon Node endpoints",
          remoteBeaconNodeEndpoints.getFailoverEndpoints());
    }

    return failoverValidatorApis;
  }

  private static ValidatorApiChannel createRemoteValidatorApiForRole(
      final ValidatorConfig validatorConfig,
      final BeaconNodeReadinessManager beaconNodeReadinessManager,
      final List<URI> endpoints,
      final OkHttpClient httpClient,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem) {
    final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints =
        new RemoteBeaconNodeEndpoints(endpoints);
    final RemoteValidatorApiChannel primaryValidatorApi =
        createPrimaryValidatorApiChannel(
            validatorConfig, remoteBeaconNodeEndpoints, httpClient, spec, asyncRunner);
    final List<RemoteValidatorApiChannel> failoverValidatorApis =
        createFailoverValidatorApiChannel(
            validatorConfig, remoteBeaconNodeEndpoints, httpClient, spec, asyncRunner);

    return new MetricRecordingValidatorApiChannel(
        metricsSystem,
        new FailoverValidatorApiHandler(
            beaconNodeReadinessManager,
            primaryValidatorApi,
            failoverValidatorApis,
            validatorConfig.isFailoversSendSubnetSubscriptionsEnabled(),
            metricsSystem));
  }

  @Override
  public SafeFuture<Void> subscribeToEvents() {
    return beaconChainEventAdapter.start();
  }

  @Override
  public SafeFuture<Void> unsubscribeFromEvents() {
    return beaconChainEventAdapter.stop();
  }

  @Override
  public ValidatorApiChannel getValidatorApi() {
    return validatorApiChannel;
  }
}
