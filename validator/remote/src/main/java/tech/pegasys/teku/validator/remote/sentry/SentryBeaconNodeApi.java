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

import static tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi.convertToOkHttpUrls;
import static tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi.createOkHttpClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.beaconnode.TimeBasedEventAdapter;
import tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel;
import tech.pegasys.teku.validator.remote.FailoverValidatorApiHandler;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeEndpoints;
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
      final AsyncRunner asyncRunner,
      final SentryNodesConfig sentryNodesConfig,
      final Spec spec,
      final boolean generateEarlyAttestations,
      final boolean preferSszBlockEncoding,
      final boolean failoverSendSubnetSubscriptions,
      final Duration primaryBeaconNodeEventStreamReconnectAttemptPeriod) {
    final BeaconNodesSentryConfig beaconNodesSentryConfig =
        sentryNodesConfig.getBeaconNodesSentryConfig();
    final OkHttpClient sentryNodesHttpClient =
        createOkHttpClientForSentryNodes(beaconNodesSentryConfig);

    final BeaconNodeRoleConfig dutiesProviderNodeConfig =
        beaconNodesSentryConfig.getDutiesProviderNodeConfig();
    final RemoteBeaconNodeEndpoints dutiesProviderHttpClient =
        new RemoteBeaconNodeEndpoints(dutiesProviderNodeConfig.getEndpointsAsURIs());

    final RemoteValidatorApiChannel dutiesProviderPrimaryValidatorApiChannel =
        createPrimaryValidatorApiChannel(
            dutiesProviderHttpClient,
            sentryNodesHttpClient,
            spec,
            preferSszBlockEncoding,
            asyncRunner);
    final List<RemoteValidatorApiChannel> dutiesProviderFailoverValidatorApiChannel =
        createFailoverValidatorApiChannel(
            dutiesProviderHttpClient,
            sentryNodesHttpClient,
            spec,
            preferSszBlockEncoding,
            asyncRunner);

    final ValidatorApiChannel dutiesProviderValidatorApi =
        new MetricRecordingValidatorApiChannel(
            serviceConfig.getMetricsSystem(),
            new FailoverValidatorApiHandler(
                dutiesProviderPrimaryValidatorApiChannel,
                dutiesProviderFailoverValidatorApiChannel,
                failoverSendSubnetSubscriptions,
                serviceConfig.getMetricsSystem()));

    final Optional<ValidatorApiChannel> blockHandlerValidatorApi =
        beaconNodesSentryConfig
            .getBlockHandlerNodeConfig()
            .map(
                c ->
                    createRemoteValidatorApiForRole(
                        c.getEndpointsAsURIs(),
                        sentryNodesHttpClient,
                        spec,
                        preferSszBlockEncoding,
                        asyncRunner,
                        serviceConfig.getMetricsSystem(),
                        failoverSendSubnetSubscriptions));

    final Optional<ValidatorApiChannel> attestationPublisherValidatorApi =
        beaconNodesSentryConfig
            .getAttestationPublisherConfig()
            .map(
                c ->
                    createRemoteValidatorApiForRole(
                        c.getEndpointsAsURIs(),
                        sentryNodesHttpClient,
                        spec,
                        preferSszBlockEncoding,
                        asyncRunner,
                        serviceConfig.getMetricsSystem(),
                        failoverSendSubnetSubscriptions));

    final ValidatorApiChannel sentryValidatorApi =
        new SentryValidatorApiChannel(
            dutiesProviderValidatorApi, blockHandlerValidatorApi, attestationPublisherValidatorApi);

    final ValidatorTimingChannel validatorTimingChannel =
        serviceConfig.getEventChannels().getPublisher(ValidatorTimingChannel.class);

    // Event adapter must listen only to duties provider events
    final BeaconChainEventAdapter beaconChainEventAdapter =
        new EventSourceBeaconChainEventAdapter(
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
            asyncRunner,
            serviceConfig.getMetricsSystem(),
            generateEarlyAttestations,
            primaryBeaconNodeEventStreamReconnectAttemptPeriod);

    return new SentryBeaconNodeApi(beaconChainEventAdapter, sentryValidatorApi);
  }

  private static OkHttpClient createOkHttpClientForSentryNodes(
      final BeaconNodesSentryConfig beaconNodesSentryConfig) {
    final List<HttpUrl> allBeaconNodeEndpoints =
        new ArrayList<>(
            convertToOkHttpUrls(
                beaconNodesSentryConfig.getDutiesProviderNodeConfig().getEndpointsAsURIs()));
    beaconNodesSentryConfig
        .getBlockHandlerNodeConfig()
        .ifPresent(c -> allBeaconNodeEndpoints.addAll(convertToOkHttpUrls(c.getEndpointsAsURIs())));
    beaconNodesSentryConfig
        .getAttestationPublisherConfig()
        .ifPresent(c -> allBeaconNodeEndpoints.addAll(convertToOkHttpUrls(c.getEndpointsAsURIs())));

    return createOkHttpClient(allBeaconNodeEndpoints);
  }

  private static RemoteValidatorApiChannel createPrimaryValidatorApiChannel(
      final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints,
      final OkHttpClient httpClient,
      final Spec spec,
      final boolean preferSszBlockEncoding,
      final AsyncRunner asyncRunner) {
    return RemoteValidatorApiHandler.create(
        remoteBeaconNodeEndpoints.getPrimaryEndpoint(),
        httpClient,
        spec,
        preferSszBlockEncoding,
        asyncRunner);
  }

  private static List<RemoteValidatorApiChannel> createFailoverValidatorApiChannel(
      final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints,
      final OkHttpClient httpClient,
      final Spec spec,
      final boolean preferSszBlockEncoding,
      final AsyncRunner asyncRunner) {
    final List<RemoteValidatorApiChannel> failoverValidatorApis =
        remoteBeaconNodeEndpoints.getFailoverEndpoints().stream()
            .map(
                endpoint ->
                    RemoteValidatorApiHandler.create(
                        endpoint, httpClient, spec, preferSszBlockEncoding, asyncRunner))
            .collect(Collectors.toList());

    if (!remoteBeaconNodeEndpoints.getFailoverEndpoints().isEmpty()) {
      LOG.info(
          "Will use {} as failover Beacon Node endpoints",
          remoteBeaconNodeEndpoints.getFailoverEndpoints());
    }

    return failoverValidatorApis;
  }

  private static ValidatorApiChannel createRemoteValidatorApiForRole(
      final List<URI> endpoints,
      final OkHttpClient httpClient,
      final Spec spec,
      final boolean preferSszBlockEncoding,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final boolean failoverSendSubnetSubscriptions) {
    final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints =
        new RemoteBeaconNodeEndpoints(endpoints);
    final RemoteValidatorApiChannel primaryValidatorApi =
        createPrimaryValidatorApiChannel(
            remoteBeaconNodeEndpoints, httpClient, spec, preferSszBlockEncoding, asyncRunner);
    final List<RemoteValidatorApiChannel> failoverValidatorApis =
        createFailoverValidatorApiChannel(
            remoteBeaconNodeEndpoints, httpClient, spec, preferSszBlockEncoding, asyncRunner);

    return new MetricRecordingValidatorApiChannel(
        metricsSystem,
        new FailoverValidatorApiHandler(
            primaryValidatorApi,
            failoverValidatorApis,
            failoverSendSubnetSubscriptions,
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
