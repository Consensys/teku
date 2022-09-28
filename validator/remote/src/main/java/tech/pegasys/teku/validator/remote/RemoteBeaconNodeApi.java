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

package tech.pegasys.teku.validator.remote;

import com.google.common.base.Preconditions;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
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
import tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpClientAuth;
import tech.pegasys.teku.validator.remote.eventsource.EventSourceBeaconChainEventAdapter;

public class RemoteBeaconNodeApi implements BeaconNodeApi {

  private static final Logger LOG = LogManager.getLogger();

  /** Time until we timeout the event stream if no events are received. */
  public static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  private final BeaconChainEventAdapter beaconChainEventAdapter;
  private final ValidatorApiChannel validatorApiChannel;

  private RemoteBeaconNodeApi(
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
      final List<URI> beaconNodeApiEndpoints) {
    Preconditions.checkArgument(
        !beaconNodeApiEndpoints.isEmpty(),
        "One or more Beacon Node endpoints should be defined for enabling remote connectivity "
            + "from VC to BN.");

    final OkHttpClient okHttpClient =
        createOkHttpClient(convertToOkHttpUrls(beaconNodeApiEndpoints));
    final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints =
        new RemoteBeaconNodeEndpoints(beaconNodeApiEndpoints);
    final HttpUrl primaryEndpoint = remoteBeaconNodeEndpoints.getPrimaryEndpoint();
    final List<HttpUrl> failoverEndpoints = remoteBeaconNodeEndpoints.getFailoverEndpoints();

    final RemoteValidatorApiChannel primaryValidatorApi =
        RemoteValidatorApiHandler.create(
            primaryEndpoint,
            okHttpClient,
            spec,
            validatorConfig.isValidatorClientUseSszBlocksEnabled(),
            asyncRunner);
    final List<RemoteValidatorApiChannel> failoverValidatorApis =
        failoverEndpoints.stream()
            .map(
                endpoint ->
                    RemoteValidatorApiHandler.create(
                        endpoint,
                        okHttpClient,
                        spec,
                        validatorConfig.isValidatorClientUseSszBlocksEnabled(),
                        asyncRunner))
            .collect(Collectors.toList());

    final EventChannels eventChannels = serviceConfig.getEventChannels();
    final MetricsSystem metricsSystem = serviceConfig.getMetricsSystem();

    if (!failoverEndpoints.isEmpty()) {
      LOG.info("Will use {} as failover Beacon Node endpoints", failoverEndpoints);
    }

    final RemoteBeaconNodeSyncingChannel remoteBeaconNodeSyncingChannel =
        eventChannels.getPublisher(RemoteBeaconNodeSyncingChannel.class);

    final ValidatorTimingChannel validatorTimingChannel =
        eventChannels.getPublisher(ValidatorTimingChannel.class);

    final BeaconNodeReadinessManager beaconNodeReadinessManager =
        new BeaconNodeReadinessManager(
            primaryValidatorApi,
            failoverValidatorApis,
            ValidatorLogger.VALIDATOR_LOGGER,
            remoteBeaconNodeSyncingChannel);

    eventChannels.subscribe(ValidatorTimingChannel.class, beaconNodeReadinessManager);

    final ValidatorApiChannel validatorApi =
        new MetricRecordingValidatorApiChannel(
            metricsSystem,
            new FailoverValidatorApiHandler(
                beaconNodeReadinessManager,
                primaryValidatorApi,
                failoverValidatorApis,
                validatorConfig.isFailoversSendSubnetSubscriptionsEnabled(),
                metricsSystem));

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

    eventChannels.subscribe(RemoteBeaconNodeSyncingChannel.class, beaconChainEventAdapter);

    return new RemoteBeaconNodeApi(beaconChainEventAdapter, validatorApi);
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

  public static List<HttpUrl> convertToOkHttpUrls(final List<URI> beaconNodeApiEndpoints) {
    return beaconNodeApiEndpoints.stream()
        .map(
            endpoint ->
                Preconditions.checkNotNull(
                    HttpUrl.get(endpoint),
                    String.format(
                        "Failed to convert remote api endpoint (%s) to a valid url",
                        UrlSanitizer.sanitizePotentialUrl(endpoint.toString()))))
        .collect(Collectors.toList());
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
