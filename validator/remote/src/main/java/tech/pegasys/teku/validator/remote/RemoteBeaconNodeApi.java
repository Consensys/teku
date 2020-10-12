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

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.beaconnode.GenesisTimeProvider;
import tech.pegasys.teku.validator.beaconnode.TimeBasedEventAdapter;
import tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;

public class RemoteBeaconNodeApi implements BeaconNodeApi {

  private final BeaconChainEventAdapter beaconChainEventAdapter;
  private final ValidatorApiChannel validatorApiChannel;

  private RemoteBeaconNodeApi(
      final BeaconChainEventAdapter beaconChainEventAdapter,
      final ValidatorApiChannel validatorApiChannel) {
    this.beaconChainEventAdapter = beaconChainEventAdapter;
    this.validatorApiChannel = validatorApiChannel;
  }

  public static BeaconNodeApi create(
      final ServiceConfig serviceConfig, final AsyncRunner asyncRunner) {

    final OkHttpClient okHttpClient = new OkHttpClient();
    final HttpUrl apiEndpoint = HttpUrl.parse(serviceConfig.getConfig().getBeaconNodeApiEndpoint());
    final OkHttpValidatorRestApiClient apiClient =
        new OkHttpValidatorRestApiClient(apiEndpoint, okHttpClient);

    final ValidatorApiChannel validatorApiChannel =
        new MetricRecordingValidatorApiChannel(
            serviceConfig.getMetricsSystem(),
            new RemoteValidatorApiHandler(apiClient, asyncRunner));

    final ValidatorTimingChannel validatorTimingChannel =
        serviceConfig.getEventChannels().getPublisher(ValidatorTimingChannel.class);
    final BeaconChainEventAdapter beaconChainEventAdapter =
        new EventSourceBeaconChainEventAdapter(
            apiEndpoint,
            okHttpClient,
            new TimeBasedEventAdapter(
                new GenesisTimeProvider(asyncRunner, validatorApiChannel),
                new RepeatingTaskScheduler(asyncRunner, serviceConfig.getTimeProvider()),
                serviceConfig.getTimeProvider(),
                validatorTimingChannel),
            validatorTimingChannel);

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
