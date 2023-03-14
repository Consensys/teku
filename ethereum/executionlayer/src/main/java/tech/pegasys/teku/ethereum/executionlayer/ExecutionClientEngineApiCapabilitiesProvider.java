/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.ethereum.executionlayer;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;

public class ExecutionClientEngineApiCapabilitiesProvider
    implements EngineApiCapabilitiesProvider, ExecutionClientEventsChannel {

  private static final Logger LOG = LogManager.getLogger();
  public static final long EXCHANGE_CAPABILITIES_FETCH_INTERVAL_IN_SECONDS = 60 * 15;
  public static final int EXCHANGE_CAPABILITIES_ATTEMPTS_BEFORE_LOG_WARN = 3;

  private final AsyncRunner asyncRunner;
  private final ExecutionEngineClient executionEngineClient;
  private final EngineApiCapabilitiesProvider localEngineApiCapabilitiesProvider;
  private final Set<String> remoteSupportedMethodNames =
      Collections.synchronizedSet(new HashSet<>());
  private final AtomicBoolean isExecutionClientAvailable = new AtomicBoolean(false);
  private final AtomicInteger failedFetchCounter = new AtomicInteger(0);
  private Cancellable fetchTask;

  public ExecutionClientEngineApiCapabilitiesProvider(
      final AsyncRunner asyncRunner,
      final ExecutionEngineClient executionEngineClient,
      final EngineApiCapabilitiesProvider localEngineApiCapabilitiesProvider,
      final EventChannels eventChannels) {
    this.asyncRunner = asyncRunner;
    this.executionEngineClient = executionEngineClient;
    this.localEngineApiCapabilitiesProvider = localEngineApiCapabilitiesProvider;

    eventChannels.subscribe(ExecutionClientEventsChannel.class, this);
  }

  private Cancellable runFetchTask() {
    LOG.trace("Exchange Capabilities Task - Starting...");

    return asyncRunner.runWithFixedDelay(
        this::fetchRemoteCapabilities,
        Duration.ZERO,
        Duration.ofSeconds(EXCHANGE_CAPABILITIES_FETCH_INTERVAL_IN_SECONDS),
        this::handleFetchCapabilitiesUnexpectedError);
  }

  private void fetchRemoteCapabilities() {
    if (!isExecutionClientAvailable.get()) {
      return;
    }

    final SafeFuture<Response<List<String>>> exchangeCapabilitiesCall =
        executionEngineClient.exchangeCapabilities(
            new ArrayList<>(localEngineApiCapabilitiesProvider.supportedMethodsVersionedNames()));

    exchangeCapabilitiesCall
        .thenAccept(
            response -> {
              if (response.isSuccess()) {
                handleSuccessfulResponse(response);
              } else {
                handleFailedResponse(response.getErrorMessage());
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private void handleSuccessfulResponse(final Response<List<String>> response) {
    LOG.trace("Handling successful response (response = {})", response.getPayload());

    final List<String> remoteCapabilities =
        response.getPayload() != null ? response.getPayload() : List.of();
    remoteSupportedMethodNames.addAll(remoteCapabilities);
    remoteSupportedMethodNames.removeIf(m -> !remoteCapabilities.contains(m));

    failedFetchCounter.set(0);
  }

  private void handleFailedResponse(final String errorResponse) {
    final int failedAttempts = failedFetchCounter.incrementAndGet();

    final StringBuilder sb = new StringBuilder();
    sb.append(
        String.format(
            "Error fetching remote capabilities from Execution Client (failed attempts = %d)",
            failedAttempts));
    if (StringUtils.isNotBlank(errorResponse)) {
      sb.append(". ").append(errorResponse);
    }

    if (failedAttempts >= EXCHANGE_CAPABILITIES_ATTEMPTS_BEFORE_LOG_WARN) {
      LOG.warn(sb.toString());
    } else {
      LOG.trace(sb.toString());
    }

    if (remoteSupportedMethodNames.isEmpty()) {
      LOG.error(
          "Unable to fetch remote capabilities from Execution Client. Check if your Execution Client "
              + "supports engine_exchangeCapabilities method.");
    }
  }

  private void handleFetchCapabilitiesUnexpectedError(final Throwable th) {
    LOG.error("Unexpected failure fetching remote capabilities from Execution Client", th);
  }

  @Override
  public boolean isAvailable() {
    return !remoteSupportedMethodNames.isEmpty();
  }

  @Override
  public Collection<EngineJsonRpcMethod<?>> supportedMethods() {
    return localEngineApiCapabilitiesProvider.supportedMethods().stream()
        .filter(m -> remoteSupportedMethodNames.contains(m.getVersionedName()))
        .collect(Collectors.toList());
  }

  @Override
  public void onAvailabilityUpdated(final boolean isAvailable) {
    if (isExecutionClientAvailable.compareAndSet(!isAvailable, isAvailable)) {
      if (fetchTask != null) {
        fetchTask.cancel();
      }

      if (isAvailable) {
        fetchTask = runFetchTask();
      }
    }
  }

  @VisibleForTesting
  int getFailedFetchCount() {
    return failedFetchCounter.get();
  }
}
