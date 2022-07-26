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
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.remote.RemoteValidatorApiChannel;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

public class EventSourceBeaconChainEventAdapter implements BeaconChainEventAdapter {

  private static final Logger LOG = LogManager.getLogger();

  private static final Duration MAX_RECONNECT_TIME = Duration.ofSeconds(12);

  private final CountDownLatch runningLatch = new CountDownLatch(1);

  private final AtomicReference<Cancellable> primaryBeaconNodeReconnectAttemptTask =
      new AtomicReference<>();

  private volatile EventSource primaryEventSource;
  private volatile Optional<EventSource> maybeFailoverEventSource = Optional.empty();

  private final RemoteValidatorApiChannel primaryBeaconNodeApi;
  private final List<RemoteValidatorApiChannel> failoverBeaconNodeApis;
  private final OkHttpClient okHttpClient;
  private final ValidatorLogger validatorLogger;
  private final AsyncRunner asyncRunner;
  private final BeaconChainEventAdapter timeBasedEventAdapter;
  private final EventSourceHandler eventSourceHandler;
  private final Duration primaryBeaconNodeEventStreamReconnectAttemptPeriod;

  public EventSourceBeaconChainEventAdapter(
      final RemoteValidatorApiChannel primaryBeaconNodeApi,
      final List<RemoteValidatorApiChannel> failoverBeaconNodeApis,
      final OkHttpClient okHttpClient,
      final ValidatorLogger validatorLogger,
      final BeaconChainEventAdapter timeBasedEventAdapter,
      final ValidatorTimingChannel validatorTimingChannel,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final boolean generateEarlyAttestations,
      final Duration primaryBeaconNodeEventStreamReconnectAttemptPeriod) {
    this.primaryBeaconNodeApi = primaryBeaconNodeApi;
    this.failoverBeaconNodeApis = failoverBeaconNodeApis;
    this.okHttpClient = okHttpClient;
    this.validatorLogger = validatorLogger;
    this.asyncRunner = asyncRunner;
    this.timeBasedEventAdapter = timeBasedEventAdapter;
    this.eventSourceHandler =
        new EventSourceHandler(validatorTimingChannel, metricsSystem, generateEarlyAttestations);
    this.primaryBeaconNodeEventStreamReconnectAttemptPeriod =
        primaryBeaconNodeEventStreamReconnectAttemptPeriod;
    this.primaryEventSource = createEventSource(primaryBeaconNodeApi);
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
    return SafeFuture.of(
            () -> {
              cancelScheduledCheckPrimaryBeaconNodeCheckStatusTask();
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
              switchToFailoverEventStreamIfNeeded(beaconNodeApi);
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

  // connect to a failover event stream only if there are failovers configured and there is a ready
  // failover Beacon Node
  private void switchToFailoverEventStreamIfNeeded(
      final RemoteValidatorApiChannel currentBeaconNodeApi) {
    if (failoverBeaconNodeApis.isEmpty()) {
      return;
    }
    final HttpUrl currentEndpoint = currentBeaconNodeApi.getEndpoint();
    checkBeaconNodeIsReadyForEventStreaming(currentBeaconNodeApi)
        .finish(__ -> findReadyFailoverAndSwitch(currentEndpoint));
  }

  private void findReadyFailoverAndSwitch(final HttpUrl endpointToIgnore) {
    final List<SafeFuture<RemoteValidatorApiChannel>> failoverReadinessCheck =
        failoverBeaconNodeApis.stream()
            .filter(api -> !api.getEndpoint().equals(endpointToIgnore))
            .map(this::checkBeaconNodeIsReadyForEventStreaming)
            .collect(Collectors.toList());
    SafeFuture.firstSuccess(failoverReadinessCheck)
        .thenAccept(this::switchToFailoverEventStream)
        .finish(
            __ ->
                LOG.warn(
                    "There are no Beacon Nodes from the configured ones that are ready to be used as an event stream failover"));
  }

  // switchToFailoverEventStreamIfNeeded is async, so there could be multiple quick calls to
  // this method for different failover endpoints because of the ConnectionErrorHandler callback
  private synchronized void switchToFailoverEventStream(
      final RemoteValidatorApiChannel beaconNodeApi) {
    if (alreadyFailedOver(beaconNodeApi)) {
      return;
    }
    closeCurrentEventStream();
    final EventSource failoverEventSource = createEventSource(beaconNodeApi);
    validatorLogger.switchingToFailoverBeaconNodeForEventStreaming();
    failoverEventSource.start();
    maybeFailoverEventSource = Optional.of(failoverEventSource);
    startPrimaryBeaconNodeReconnectAttemptTask();
  }

  private boolean alreadyFailedOver(final RemoteValidatorApiChannel beaconNodeApi) {
    return maybeFailoverEventSource
        .map(
            eventSource -> {
              final boolean isSameEndpoint =
                  eventSource
                      .getHttpUrl()
                      .equals(createHeadEventSourceUrl(beaconNodeApi.getEndpoint()));
              return isSameEndpoint || eventSource.getState().equals(ReadyState.OPEN);
            })
        .orElse(false);
  }

  private void startPrimaryBeaconNodeReconnectAttemptTask() {
    final Cancellable currentCheckStatusTask = primaryBeaconNodeReconnectAttemptTask.get();
    if (currentCheckStatusTask != null && !currentCheckStatusTask.isCancelled()) {
      return;
    }
    final Cancellable checkStatusTask =
        asyncRunner.runWithFixedDelay(
            () ->
                checkBeaconNodeIsReadyForEventStreaming(primaryBeaconNodeApi)
                    .thenRun(this::switchToPrimaryEventStream),
            primaryBeaconNodeEventStreamReconnectAttemptPeriod,
            primaryBeaconNodeEventStreamReconnectAttemptPeriod,
            throwable ->
                LOG.trace(
                    "Couldn't reconnect to the primary Beacon Node event stream.", throwable));
    primaryBeaconNodeReconnectAttemptTask.set(checkStatusTask);
  }

  private void switchToPrimaryEventStream() {
    closeCurrentEventStream();
    primaryEventSource = createEventSource(primaryBeaconNodeApi);
    maybeFailoverEventSource = Optional.empty();
    validatorLogger.primaryBeaconNodeIsBackOnlineForEventStreaming();
    primaryEventSource.start();
    cancelScheduledCheckPrimaryBeaconNodeCheckStatusTask();
  }

  private SafeFuture<RemoteValidatorApiChannel> checkBeaconNodeIsReadyForEventStreaming(
      final RemoteValidatorApiChannel beaconNodeApi) {
    return beaconNodeApi
        .getSyncingStatus()
        .thenCompose(
            syncingStatus -> {
              final HttpUrl beaconNodeApiEndpoint = beaconNodeApi.getEndpoint();
              if (!syncingStatus.isSyncing() || syncingStatus.getIsOptimistic().orElse(false)) {
                LOG.debug("{} is ready for event streaming", beaconNodeApiEndpoint);
                return SafeFuture.completedFuture(beaconNodeApi);
              }
              final String errorMessage =
                  String.format("%s is not ready for event streaming", beaconNodeApiEndpoint);
              LOG.debug(errorMessage);
              return SafeFuture.failedFuture(new RemoteServiceNotAvailableException(errorMessage));
            });
  }

  private void closeCurrentEventStream() {
    primaryEventSource.close();
    maybeFailoverEventSource.ifPresent(EventSource::close);
  }

  private void cancelScheduledCheckPrimaryBeaconNodeCheckStatusTask() {
    Optional.ofNullable(primaryBeaconNodeReconnectAttemptTask.get()).ifPresent(Cancellable::cancel);
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
