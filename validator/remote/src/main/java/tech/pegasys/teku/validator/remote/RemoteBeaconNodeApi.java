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
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
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
import tech.pegasys.teku.validator.remote.apiclient.OkHttpClientAuth;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;
import tech.pegasys.teku.validator.remote.eventsource.EventSourceBeaconChainEventAdapter;
import tech.pegasys.teku.validator.remote.typedef.OkHttpValidatorTypeDefClient;

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
      final AsyncRunner asyncRunner,
      final List<URI> beaconNodeApiEndpoints,
      final Spec spec,
      final boolean generateEarlyAttestations,
      final boolean preferSszBlockEncoding,
      final boolean failoversSendSubnetSubscriptions,
      final Duration primaryBeaconNodeEventStreamReconnectAttemptPeriod) {
    Preconditions.checkArgument(
        !beaconNodeApiEndpoints.isEmpty(),
        "One or more Beacon Node endpoints should be defined for enabling remote connectivity from VC to BN.");
    List<HttpUrl> endpoints = convertToOkHttpUrls(beaconNodeApiEndpoints);
    final OkHttpClient okHttpClient = createOkHttpClient(endpoints);
    // Strip any authentication info from the URL(s) to ensure it doesn't get logged.
    endpoints = stripAuthentication(endpoints);

    final HttpUrl primaryEndpoint = endpoints.get(0);
    final List<HttpUrl> failoverEndpoints = endpoints.subList(1, endpoints.size());

    final RemoteValidatorApiChannel primaryValidatorApi =
        createRemoteValidatorApi(
            primaryEndpoint, okHttpClient, spec, preferSszBlockEncoding, asyncRunner);
    final List<RemoteValidatorApiChannel> failoverValidatorApis =
        failoverEndpoints.stream()
            .map(
                endpoint ->
                    createRemoteValidatorApi(
                        endpoint, okHttpClient, spec, preferSszBlockEncoding, asyncRunner))
            .collect(Collectors.toList());

    final MetricsSystem metricsSystem = serviceConfig.getMetricsSystem();

    if (!failoverEndpoints.isEmpty()) {
      LOG.info("Will use {} as failover Beacon Node endpoints", failoverEndpoints);
    }

    final ValidatorApiChannel validatorApi =
        new MetricRecordingValidatorApiChannel(
            metricsSystem,
            new FailoverValidatorApiHandler(
                primaryValidatorApi,
                failoverValidatorApis,
                failoversSendSubnetSubscriptions,
                metricsSystem,
                ValidatorLogger.VALIDATOR_LOGGER));

    final ValidatorTimingChannel validatorTimingChannel =
        serviceConfig.getEventChannels().getPublisher(ValidatorTimingChannel.class);

    final BeaconChainEventAdapter beaconChainEventAdapter =
        new EventSourceBeaconChainEventAdapter(
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
            asyncRunner,
            metricsSystem,
            generateEarlyAttestations,
            primaryBeaconNodeEventStreamReconnectAttemptPeriod);

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

  private static List<HttpUrl> convertToOkHttpUrls(final List<URI> beaconNodeApiEndpoints) {
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

  private static OkHttpClient createOkHttpClient(final List<HttpUrl> endpoints) {
    final OkHttpClient.Builder httpClientBuilder =
        new OkHttpClient.Builder().readTimeout(READ_TIMEOUT);
    if (endpoints.size() > 1) {
      OkHttpClientAuth.addAuthInterceptorForMultipleEndpoints(endpoints, httpClientBuilder);
    } else {
      OkHttpClientAuth.addAuthInterceptor(endpoints.get(0), httpClientBuilder);
    }
    return httpClientBuilder.build();
  }

  private static List<HttpUrl> stripAuthentication(final List<HttpUrl> endpoints) {
    return endpoints.stream()
        .map(endpoint -> endpoint.newBuilder().username("").password("").build())
        .collect(Collectors.toList());
  }

  private static RemoteValidatorApiChannel createRemoteValidatorApi(
      final HttpUrl endpoint,
      final OkHttpClient okHttpClient,
      final Spec spec,
      final boolean preferSszBlockEncoding,
      final AsyncRunner asyncRunner) {
    final OkHttpValidatorRestApiClient apiClient =
        new OkHttpValidatorRestApiClient(endpoint, okHttpClient);
    final OkHttpValidatorTypeDefClient typeDefClient =
        new OkHttpValidatorTypeDefClient(okHttpClient, endpoint, spec, preferSszBlockEncoding);
    return new RemoteValidatorApiHandler(endpoint, spec, apiClient, typeDefClient, asyncRunner);
  }
}
