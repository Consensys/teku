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

package tech.pegasys.teku.validator.remote.eventsource;

import static java.util.Collections.emptyMap;

import com.google.common.base.Preconditions;
import com.launchdarkly.eventsource.ConnectionErrorHandler.Action;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.ReadyState;
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
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.remote.BeaconNodeReadinessManager;
import tech.pegasys.teku.validator.remote.RemoteValidatorApiChannel;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

public class EventSourceBeaconChainEventAdapter implements BeaconChainEventAdapter {

  private static final Logger LOG = LogManager.getLogger();

  private static final Duration MAX_RECONNECT_TIME = Duration.ofSeconds(12);

  private final CountDownLatch runningLatch = new CountDownLatch(1);

  private volatile Cancellable beaconNodeEventStreamReadinessCheckTask;

  private final AtomicReference<RemoteValidatorApiChannel> currentBeaconNodeUsedForEventStreaming =
      new AtomicReference<>();

  private volatile EventSource primaryEventSource;
  private volatile Optional<EventSource> maybeFailoverEventSource = Optional.empty();

  private final BeaconNodeReadinessManager beaconNodeReadinessManager;
  private final RemoteValidatorApiChannel primaryBeaconNodeApi;
  private final List<RemoteValidatorApiChannel> failoverBeaconNodeApis;
  private final OkHttpClient okHttpClient;
  private final ValidatorLogger validatorLogger;
  private final AsyncRunner asyncRunner;
  private final BeaconChainEventAdapter timeBasedEventAdapter;
  private final EventSourceHandler eventSourceHandler;
  private final Duration beaconNodeEventStreamReadinessCheckPeriod;

  public EventSourceBeaconChainEventAdapter(
      final BeaconNodeReadinessManager beaconNodeReadinessManager,
      final RemoteValidatorApiChannel primaryBeaconNodeApi,
      final List<RemoteValidatorApiChannel> failoverBeaconNodeApis,
      final OkHttpClient okHttpClient,
      final ValidatorLogger validatorLogger,
      final BeaconChainEventAdapter timeBasedEventAdapter,
      final ValidatorTimingChannel validatorTimingChannel,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final boolean generateEarlyAttestations,
      final Duration beaconNodeEventStreamReadinessCheckPeriod) {
    this.beaconNodeReadinessManager = beaconNodeReadinessManager;
    this.primaryBeaconNodeApi = primaryBeaconNodeApi;
    this.failoverBeaconNodeApis = failoverBeaconNodeApis;
    this.okHttpClient = okHttpClient;
    this.validatorLogger = validatorLogger;
    this.asyncRunner = asyncRunner;
    this.timeBasedEventAdapter = timeBasedEventAdapter;
    this.eventSourceHandler =
        new EventSourceHandler(validatorTimingChannel, metricsSystem, generateEarlyAttestations);
    this.beaconNodeEventStreamReadinessCheckPeriod = beaconNodeEventStreamReadinessCheckPeriod;
    this.primaryEventSource = createEventSource(primaryBeaconNodeApi);
  }

  @Override
  public SafeFuture<Void> start() {
    // EventSource uses a daemon thread which allows the process to exit because all threads are
    // daemons, but while we're subscribed to events we should just wait for the next event, not
    // exit.  So create a non-daemon thread that lives until the adapter is stopped.
    primaryEventSource.start();
    currentBeaconNodeUsedForEventStreaming.set(primaryBeaconNodeApi);
    new Thread(this::waitForExit).start();
    return timeBasedEventAdapter
        .start()
        .thenRun(this::startBeaconNodeEventStreamReadinessCheckTask);
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.of(
            () -> {
              Optional.ofNullable(beaconNodeEventStreamReadinessCheckTask)
                  .ifPresent(Cancellable::cancel);
              closeCurrentEventStream();
              return timeBasedEventAdapter.stop();
            })
        .thenRun(runningLatch::countDown);
  }

  private EventSource createEventSource(final RemoteValidatorApiChannel beaconNodeApi) {
    final HttpUrl eventSourceUrl = createHeadEventSourceUrl(beaconNodeApi.getEndpoint());
    return new EventSource.Builder(eventSourceHandler, eventSourceUrl)
        .maxReconnectTime(MAX_RECONNECT_TIME)
        .connectionErrorHandler(
            __ -> {
              switchToFailoverEventStreamIfNeeded();
              return Action.PROCEED;
            })
        .client(okHttpClient)
        .build();
  }

  private HttpUrl createHeadEventSourceUrl(final HttpUrl endpoint) {
    final HttpUrl eventSourceUrl =
        endpoint.resolve(
            ValidatorApiMethod.EVENTS.getPath(emptyMap()) + "?topics=" + EventType.head);
    return Preconditions.checkNotNull(eventSourceUrl);
  }

  // connect to a failover event stream only if there are failovers configured, the current beacon
  // node is not ready for event streaming and there is a ready failover
  private void switchToFailoverEventStreamIfNeeded() {
    if (failoverBeaconNodeApis.isEmpty()) {
      return;
    }
    if (!beaconNodeReadinessManager.isReady(currentBeaconNodeUsedForEventStreaming.get())) {
      findReadyFailoverAndSwitch();
    }
  }

  private void switchToPrimaryEventStream() {
    closeCurrentEventStream();
    primaryEventSource = createEventSource(primaryBeaconNodeApi);
    maybeFailoverEventSource = Optional.empty();
    validatorLogger.primaryBeaconNodeIsBackOnlineForEventStreaming();
    primaryEventSource.start();
    currentBeaconNodeUsedForEventStreaming.set(primaryBeaconNodeApi);
  }

  private void findReadyFailoverAndSwitch() {
    failoverBeaconNodeApis.stream()
        .filter(beaconNodeApi -> !currentEventStreamHasSameEndpoint(beaconNodeApi))
        .filter(beaconNodeReadinessManager::isReady)
        .findFirst()
        .ifPresentOrElse(
            this::switchToFailoverEventStream,
            () ->
                LOG.warn(
                    "There are no Beacon Nodes from the configured ones that are ready to be used as an event stream failover"));
  }

  // switchToFailoverEventStreamIfNeeded is async, so there could be multiple quick calls to
  // this method for different failover endpoints because of the ConnectionErrorHandler callback and
  // the readiness check task
  private synchronized void switchToFailoverEventStream(
      final RemoteValidatorApiChannel beaconNodeApi) {
    if (alreadyFailedOver(beaconNodeApi)) {
      return;
    }
    closeCurrentEventStream();
    final EventSource failoverEventSource = createEventSource(beaconNodeApi);
    validatorLogger.switchingToFailoverBeaconNodeForEventStreaming(failoverEventSource.getUri());
    maybeFailoverEventSource = Optional.of(failoverEventSource);
    failoverEventSource.start();
    currentBeaconNodeUsedForEventStreaming.set(beaconNodeApi);
  }

  private boolean alreadyFailedOver(final RemoteValidatorApiChannel beaconNodeApi) {
    return maybeFailoverEventSource
        .map(
            eventSource ->
                currentEventStreamHasSameEndpoint(beaconNodeApi)
                    || eventSource.getState().equals(ReadyState.OPEN))
        .orElse(false);
  }

  private boolean currentEventStreamHasSameEndpoint(final RemoteValidatorApiChannel beaconNodeApi) {
    return currentBeaconNodeUsedForEventStreaming
        .get()
        .getEndpoint()
        .equals(beaconNodeApi.getEndpoint());
  }

  private void startBeaconNodeEventStreamReadinessCheckTask() {
    beaconNodeEventStreamReadinessCheckTask =
        asyncRunner.runWithFixedDelay(
            () -> {
              if (beaconNodeReadinessManager.isReady(primaryBeaconNodeApi)) {
                if (maybeFailoverEventSource.isPresent()) {
                  switchToPrimaryEventStream();
                }
              } else {
                switchToFailoverEventStreamIfNeeded();
              }
              return SafeFuture.COMPLETE;
            },
            beaconNodeEventStreamReadinessCheckPeriod,
            beaconNodeEventStreamReadinessCheckPeriod,
            throwable ->
                LOG.warn(
                    "Exception while running beacon node event stream readiness check", throwable));
  }

  private void closeCurrentEventStream() {
    primaryEventSource.close();
    maybeFailoverEventSource.ifPresent(EventSource::close);
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
