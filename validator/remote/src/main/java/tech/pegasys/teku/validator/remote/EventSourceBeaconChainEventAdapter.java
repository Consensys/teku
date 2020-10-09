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

import com.launchdarkly.eventsource.EventSource;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

public class EventSourceBeaconChainEventAdapter implements BeaconChainEventAdapter {

  private final BeaconChainEventAdapter timeBasedEventAdapter;
  private final EventSource eventSource;

  public EventSourceBeaconChainEventAdapter(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final BeaconChainEventAdapter timeBasedEventAdapter,
      final ValidatorTimingChannel validatorTimingChannel) {
    this.timeBasedEventAdapter = timeBasedEventAdapter;
    final HttpUrl eventSourceUrl =
        baseEndpoint.resolve(
            ValidatorApiMethod.EVENTS.getPath()
                + "?topics="
                + EventTypes.HEAD
                + ","
                + EventTypes.CHAIN_REORG);
    this.eventSource =
        new EventSource.Builder(new EventSourceHandler(validatorTimingChannel), eventSourceUrl)
            .client(okHttpClient)
            .build();
  }

  @Override
  public SafeFuture<Void> start() {
    eventSource.start();
    return timeBasedEventAdapter.start();
  }

  @Override
  public SafeFuture<Void> stop() {
    eventSource.close();
    return timeBasedEventAdapter.stop();
  }
}
