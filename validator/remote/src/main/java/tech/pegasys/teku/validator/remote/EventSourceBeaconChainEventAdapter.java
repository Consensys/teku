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

import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import com.google.common.base.Preconditions;
import com.launchdarkly.eventsource.ConnectionErrorHandler.Action;
import com.launchdarkly.eventsource.EventSource;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

public class EventSourceBeaconChainEventAdapter implements BeaconChainEventAdapter {

  private static final Logger LOG = LogManager.getLogger();

  private static final Duration MAX_RECONNECT_TIME = Duration.ofSeconds(12);
  private static final Duration PING_PRIMARY_NODE_DURING_FAILOVER_PERIOD = Duration.ofMinutes(1);

  private final AtomicReference<Cancellable> scheduledPingingTask = new AtomicReference<>();
  private final CountDownLatch runningLatch = new CountDownLatch(1);

  private final BeaconChainEventAdapter timeBasedEventAdapter;
  private final HttpUrl primaryApiEndpoint;
  private final List<HttpUrl> failoverApiEndpoints;
  private final OkHttpClient okHttpClient;
  private final EventSourceHandler eventSourceHandler;
  private final AsyncRunner asyncRunner;

  private volatile EventSource primaryEventSource;
  private volatile Optional<EventSource> maybeFailoverEventSource = Optional.empty();

  public EventSourceBeaconChainEventAdapter(
      final HttpUrl primaryApiEndpoint,
      final List<HttpUrl> failoverApiEndpoints,
      final OkHttpClient okHttpClient,
      final BeaconChainEventAdapter timeBasedEventAdapter,
      final ValidatorTimingChannel validatorTimingChannel,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final boolean generateEarlyAttestations) {
    this.timeBasedEventAdapter = timeBasedEventAdapter;
    this.primaryApiEndpoint = primaryApiEndpoint;
    this.failoverApiEndpoints = failoverApiEndpoints;
    this.okHttpClient = okHttpClient;
    this.asyncRunner = asyncRunner;
    eventSourceHandler =
        new EventSourceHandler(validatorTimingChannel, metricsSystem, generateEarlyAttestations);
    primaryEventSource = createPrimaryEventSource();
  }

  @Override
  public SafeFuture<Void> start() {
    // EventSource uses a daemon thread which allows the process to exit because all threads are
    // daemons, but while we're subscribed to events we should just wait for the next event, not
    // exit.  So create a non-daemon thread that lives until the adapter is stopped.
    primaryEventSource.start();
    new Thread(this::waitForExit).start();
    return timeBasedEventAdapter.start();
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.fromRunnable(
            () -> {
              primaryEventSource.close();
              closeFailoverEventSource();
              cancelScheduledPingingTask();
            })
        .thenCompose(__ -> timeBasedEventAdapter.stop())
        .thenRun(runningLatch::countDown);
  }

  private EventSource createPrimaryEventSource() {
    return defaultEventSourceBuilder(primaryApiEndpoint)
        .connectionErrorHandler(
            throwable -> {
              if (failoverApiEndpoints.isEmpty()) {
                return Action.PROCEED;
              }
              if (!PingUtils.hostIsReachable(primaryApiEndpoint)) {
                // connect to a failover Beacon node in case the primary Beacon node is not
                // reachable (offline)
                final Optional<HttpUrl> maybeFailover = findAvailableFailover();
                final boolean failoverIsAvailable = maybeFailover.isPresent();
                VALIDATOR_LOGGER.beaconNodeIsNotReachableForEventStreaming(
                    primaryApiEndpoint.uri(), failoverIsAvailable);
                if (failoverIsAvailable) {
                  startFailoverEventSource(maybeFailover.get());
                  return Action.SHUTDOWN;
                }
              }
              return Action.PROCEED;
            })
        .build();
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void startFailoverEventSource(final HttpUrl failoverApiEndpoint) {
    asyncRunner.runAfterDelay(
        () -> {
          final EventSource failoverEventSource =
              defaultEventSourceBuilder(failoverApiEndpoint).build();
          LOG.info(
              "Connecting to a failover Beacon Node {} for event streaming.", failoverApiEndpoint);
          failoverEventSource.start();
          maybeFailoverEventSource = Optional.of(failoverEventSource);
          schedulePingingOfPrimaryBeaconNode();
        },
        // Give some time for the primary Beacon node event stream to shut down
        Duration.ofSeconds(1));
  }

  private EventSource.Builder defaultEventSourceBuilder(final HttpUrl apiEndpoint) {
    return new EventSource.Builder(eventSourceHandler, createHeadEventSourceUrl(apiEndpoint))
        .maxReconnectTime(MAX_RECONNECT_TIME)
        .client(okHttpClient);
  }

  private void schedulePingingOfPrimaryBeaconNode() {
    final Cancellable cancellable =
        asyncRunner.runWithFixedDelay(
            () -> {
              final boolean primaryBeaconNodeIsReachable =
                  PingUtils.hostIsReachable(primaryApiEndpoint);
              if (primaryBeaconNodeIsReachable) {
                // reconnect again to the primary Beacon node event stream
                VALIDATOR_LOGGER.primaryBeaconNodeIsBackOnlineForEventStreaming(
                    primaryApiEndpoint.uri());
                primaryEventSource = createPrimaryEventSource();
                closeFailoverEventSource();
                primaryEventSource.start();
                cancelScheduledPingingTask();
              }
            },
            PING_PRIMARY_NODE_DURING_FAILOVER_PERIOD,
            throwable ->
                LOG.error(
                    "Error occurred while pinging the primary Beacon Node during a period of failover.",
                    throwable));
    scheduledPingingTask.set(cancellable);
  }

  private HttpUrl createHeadEventSourceUrl(final HttpUrl apiEndpoint) {
    final HttpUrl eventSourceUrl =
        apiEndpoint.resolve(
            ValidatorApiMethod.EVENTS.getPath(emptyMap()) + "?topics=" + EventType.head);
    return Preconditions.checkNotNull(eventSourceUrl);
  }

  private Optional<HttpUrl> findAvailableFailover() {
    return failoverApiEndpoints.stream().filter(PingUtils::hostIsReachable).findFirst();
  }

  private void cancelScheduledPingingTask() {
    Optional.ofNullable(scheduledPingingTask.getAndSet(null)).ifPresent(Cancellable::cancel);
  }

  private void closeFailoverEventSource() {
    maybeFailoverEventSource.ifPresent(EventSource::close);
    maybeFailoverEventSource = Optional.empty();
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
