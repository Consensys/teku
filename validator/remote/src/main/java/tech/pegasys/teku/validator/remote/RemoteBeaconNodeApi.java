/*
 * Copyright 2020 ConsenSys AG.
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
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.beaconnode.TimeBasedEventAdapter;
import tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpClientAuthLoggingIntercepter;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;
import tech.pegasys.teku.validator.remote.typedef.OkHttpValidatorTypeDefClient;

public class RemoteBeaconNodeApi implements BeaconNodeApi {

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
      final URI beaconNodeApiEndpoint,
      final Spec spec,
      final boolean generateEarlyAttestations,
      final boolean preferSszBlockEncoding) {

    final OkHttpClient.Builder httpClientBuilder =
        new OkHttpClient.Builder().readTimeout(READ_TIMEOUT);
    HttpUrl apiEndpoint = HttpUrl.get(beaconNodeApiEndpoint);
    Preconditions.checkNotNull(
        apiEndpoint,
        String.format(
            "Failed to convert remote api endpoint (%s) to a valid url",
            UrlSanitizer.sanitizePotentialUrl(beaconNodeApiEndpoint.toString())));
    OkHttpClientAuthLoggingIntercepter.addAuthenticator(apiEndpoint, httpClientBuilder);
    // Strip any authentication info from the URL to ensure it doesn't get logged.
    apiEndpoint = apiEndpoint.newBuilder().username("").password("").build();
    final OkHttpClient okHttpClient = httpClientBuilder.build();
    final OkHttpValidatorRestApiClient apiClient =
        new OkHttpValidatorRestApiClient(apiEndpoint, okHttpClient);

    final OkHttpValidatorTypeDefClient typeDefClient =
        new OkHttpValidatorTypeDefClient(okHttpClient, apiEndpoint, spec, preferSszBlockEncoding);
    final ValidatorApiChannel validatorApiChannel =
        new MetricRecordingValidatorApiChannel(
            serviceConfig.getMetricsSystem(),
            new RemoteValidatorApiHandler(spec, apiClient, typeDefClient, asyncRunner));

    final ValidatorTimingChannel validatorTimingChannel =
        serviceConfig.getEventChannels().getPublisher(ValidatorTimingChannel.class);
    final BeaconChainEventAdapter beaconChainEventAdapter =
        new EventSourceBeaconChainEventAdapter(
            apiEndpoint,
            okHttpClient,
            new TimeBasedEventAdapter(
                new GenesisDataProvider(asyncRunner, validatorApiChannel),
                new RepeatingTaskScheduler(asyncRunner, serviceConfig.getTimeProvider()),
                serviceConfig.getTimeProvider(),
                validatorTimingChannel,
                spec),
            validatorTimingChannel,
            serviceConfig.getMetricsSystem(),
            generateEarlyAttestations);

    return new RemoteBeaconNodeApi(beaconChainEventAdapter, validatorApiChannel);
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
