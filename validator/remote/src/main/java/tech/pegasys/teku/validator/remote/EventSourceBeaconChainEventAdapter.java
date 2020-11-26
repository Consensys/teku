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

import static java.util.Collections.emptyMap;

import com.launchdarkly.eventsource.EventSource;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

public class EventSourceBeaconChainEventAdapter implements BeaconChainEventAdapter {
  private static final Logger LOG = LogManager.getLogger();
  private final CountDownLatch runningLatch = new CountDownLatch(1);
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
            ValidatorApiMethod.EVENTS.getPath(emptyMap())
                + "?topics="
                + EventType.head
                + ","
                + EventType.chain_reorg);
    this.eventSource =
        new EventSource.Builder(new EventSourceHandler(validatorTimingChannel), eventSourceUrl)
            .maxReconnectTime(Duration.ofSeconds(Constants.SECONDS_PER_SLOT))
            .client(okHttpClient)
            .requestTransformer(request -> applyBasicAuthentication(eventSourceUrl, request))
            .build();
  }

  private Request applyBasicAuthentication(final HttpUrl eventSourceUrl, final Request request) {
    if (!eventSourceUrl.username().isEmpty()) {
      return request
          .newBuilder()
          .header(
              "Authorization",
              Credentials.basic(eventSourceUrl.encodedUsername(), eventSourceUrl.encodedPassword()))
          .build();
    } else {
      return request;
    }
  }

  @Override
  public SafeFuture<Void> start() {
    // EventSource uses a daemon thread which allows the process to exit because all threads are
    // daemons, but while we're subscribed to events we should just wait for the next event, not
    // exit.  So create a non-daemon thread that lives until the adapter is stopped.
    eventSource.start();
    new Thread(this::waitForExit).start();
    return timeBasedEventAdapter.start();
  }

  @Override
  public SafeFuture<Void> stop() {
    eventSource.close();
    return timeBasedEventAdapter.stop().thenRun(runningLatch::countDown);
  }

  private void waitForExit() {
    while (true) {
      try {
        runningLatch.await();
      } catch (InterruptedException e) {
        LOG.debug("Interrupted while waiting for shutdown");
      }
    }
  }
}
