/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.validator.remote;

import com.google.common.base.Preconditions;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
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
import tech.pegasys.teku.validator.remote.apiclient.OkHttpClientAuth;
import tech.pegasys.teku.validator.remote.eventsource.EventSourceBeaconChainEventAdapter;

public class RemoteBeaconNodeApi implements BeaconNodeApi {

  private static final Logger LOG = LogManager.getLogger();

  /** Time until we timeout the event stream if no events are received. */
  public static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  private final BeaconChainEventAdapter beaconChainEventAdapter;
  private final ValidatorApiChannel validatorApiChannel;
  private final BeaconNodeReadinessManager beaconNodeReadinessManager;

  private RemoteBeaconNodeApi(
      final BeaconChainEventAdapter beaconChainEventAdapter,
      final ValidatorApiChannel validatorApiChannel,
      final BeaconNodeReadinessManager beaconNodeReadinessManager) {
    this.beaconChainEventAdapter = beaconChainEventAdapter;
    this.validatorApiChannel = validatorApiChannel;
    this.beaconNodeReadinessManager = beaconNodeReadinessManager;
  }

  public static BeaconNodeApi create(
      final ServiceConfig serviceConfig,
      final ValidatorConfig validatorConfig,
      final AsyncRunner asyncRunner,
      final Spec spec,
      final List<URI> beaconNodeApiEndpoints) {
    Preconditions.checkArgument(
        !beaconNodeApiEndpoints.isEmpty(),
        "One or more Beacon Node endpoints should be defined for enabling remote connectivity "
            + "from VC to BN.");

    final OkHttpClient okHttpClient =
        createOkHttpClient(convertToOkHttpUrls(beaconNodeApiEndpoints));
    final MetricsSystem metricsSystem = serviceConfig.getMetricsSystem();

    final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints =
        new RemoteBeaconNodeEndpoints(beaconNodeApiEndpoints);

    final RemoteValidatorApiChannel primaryValidatorApi =
        createPrimaryValidatorApiChannel(
            validatorConfig,
            remoteBeaconNodeEndpoints,
            okHttpClient,
            spec,
            asyncRunner,
            metricsSystem);
    final List<? extends RemoteValidatorApiChannel> failoverValidatorApis =
        createFailoverValidatorApiChannels(
            validatorConfig,
            remoteBeaconNodeEndpoints,
            okHttpClient,
            spec,
            asyncRunner,
            metricsSystem);

    final EventChannels eventChannels = serviceConfig.getEventChannels();

    final BeaconNodeReadinessChannel beaconNodeReadinessChannel =
        eventChannels.getPublisher(BeaconNodeReadinessChannel.class);

    final ValidatorTimingChannel validatorTimingChannel =
        eventChannels.getPublisher(ValidatorTimingChannel.class);

    final BeaconNodeReadinessManager beaconNodeReadinessManager =
        new BeaconNodeReadinessManager(
            primaryValidatorApi,
            failoverValidatorApis,
            ValidatorLogger.VALIDATOR_LOGGER,
            beaconNodeReadinessChannel);

    eventChannels.subscribe(ValidatorTimingChannel.class, beaconNodeReadinessManager);

    final ValidatorApiChannel validatorApi;

    if (!failoverValidatorApis.isEmpty()) {
      LOG.info(
          "Will use {} as failover Beacon Node endpoints",
          remoteBeaconNodeEndpoints.getFailoverEndpoints());
      validatorApi =
          new FailoverValidatorApiHandler(
              beaconNodeReadinessManager,
              primaryValidatorApi,
              failoverValidatorApis,
              validatorConfig.isFailoversSendSubnetSubscriptionsEnabled(),
              validatorConfig.isFailoversPublishSignedDutiesEnabled());
    } else {
      validatorApi = primaryValidatorApi;
    }

    final EventSourceBeaconChainEventAdapter beaconChainEventAdapter =
        new EventSourceBeaconChainEventAdapter(
            beaconNodeReadinessManager,
            primaryValidatorApi,
            failoverValidatorApis,
            okHttpClient,
            ValidatorLogger.VALIDATOR_LOGGER,
            new TimeBasedEventAdapter(
                new GenesisDataProvider(asyncRunner, validatorApi),
                new RepeatingTaskScheduler(asyncRunner, serviceConfig.getTimeProvider()),
                serviceConfig.getTimeProvider(),
                validatorTimingChannel,
                spec),
            validatorTimingChannel,
            metricsSystem,
            validatorConfig.generateEarlyAttestations());

    eventChannels.subscribe(BeaconNodeReadinessChannel.class, beaconChainEventAdapter);

    return new RemoteBeaconNodeApi(
        beaconChainEventAdapter, validatorApi, beaconNodeReadinessManager);
  }

  @Override
  public SafeFuture<Void> subscribeToEvents() {
    return beaconChainEventAdapter
        .start()
        .thenCompose(__ -> beaconNodeReadinessManager.start())
        .toVoid();
  }

  @Override
  public SafeFuture<Void> unsubscribeFromEvents() {
    return beaconChainEventAdapter
        .stop()
        .thenCompose(__ -> beaconNodeReadinessManager.stop())
        .toVoid();
  }

  @Override
  public ValidatorApiChannel getValidatorApi() {
    return validatorApiChannel;
  }

  public static RemoteValidatorApiChannel createPrimaryValidatorApiChannel(
      final ValidatorConfig validatorConfig,
      final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints,
      final OkHttpClient httpClient,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem) {
    return new RemoteMetricRecordingValidatorApiChannel(
        metricsSystem,
        RemoteValidatorApiHandler.create(
            remoteBeaconNodeEndpoints.getPrimaryEndpoint(),
            httpClient,
            spec,
            validatorConfig.isValidatorClientUseSszBlocksEnabled(),
            asyncRunner));
  }

  public static List<? extends RemoteValidatorApiChannel> createFailoverValidatorApiChannels(
      final ValidatorConfig validatorConfig,
      final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints,
      final OkHttpClient httpClient,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem) {
    return remoteBeaconNodeEndpoints.getFailoverEndpoints().stream()
        .map(
            endpoint ->
                new RemoteMetricRecordingValidatorApiChannel(
                    metricsSystem,
                    RemoteValidatorApiHandler.create(
                        endpoint,
                        httpClient,
                        spec,
                        validatorConfig.isValidatorClientUseSszBlocksEnabled(),
                        asyncRunner)))
        .toList();
  }

  public static List<HttpUrl> convertToOkHttpUrls(final List<URI> beaconNodeApiEndpoints) {
    return beaconNodeApiEndpoints.stream()
        .map(
            endpoint ->
                Preconditions.checkNotNull(
                    HttpUrl.get(endpoint),
                    String.format(
                        "Failed to convert remote api endpoint (%s) to a valid url",
                        UrlSanitizer.sanitizePotentialUrl(endpoint.toString()))))
        .toList();
  }

  public static OkHttpClient createOkHttpClient(final List<HttpUrl> endpoints) {
    final OkHttpClient.Builder httpClientBuilder =
        new OkHttpClient.Builder().readTimeout(READ_TIMEOUT);
    if (endpoints.size() > 1) {
      OkHttpClientAuth.addAuthInterceptorForMultipleEndpoints(endpoints, httpClientBuilder);
    } else {
      OkHttpClientAuth.addAuthInterceptor(endpoints.get(0), httpClientBuilder);
    }
    return httpClientBuilder.build();
  }
}
