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

package tech.pegasys.teku.ethereum.executionclient.web3j;

import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getMessageOrSimpleName;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeoutException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.exceptions.ClientConnectionException;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public abstract class Web3JClient {

  private static final int ERROR_REPEAT_DELAY_MILLIS = 30 * 1000;
  private static final int NO_ERROR_TIME = -1;
  private static final long STARTUP_LAST_ERROR_TIME = 0;

  private final EventLogger eventLog;
  private final TimeProvider timeProvider;
  private final ExecutionClientEventsChannel executionClientEventsPublisher;
  private Web3jService web3jService;
  private Web3j eth1Web3j;
  /*
   Non-critical methods won't trigger availability updates when they succeed/fail
  */
  private final Collection<String> nonCriticalMethods = new HashSet<>();

  // Default to the provider having a previous failure at startup so we log when it is first
  // available but uses a very old value to make sure we log if the first request fails
  private long lastErrorTime = STARTUP_LAST_ERROR_TIME;
  private boolean initialized = false;

  protected Web3JClient(
      final EventLogger eventLog,
      final TimeProvider timeProvider,
      final ExecutionClientEventsChannel executionClientEventsPublisher,
      final Collection<String> nonCriticalMethods) {
    this.eventLog = eventLog;
    this.timeProvider = timeProvider;
    this.executionClientEventsPublisher = executionClientEventsPublisher;
    this.nonCriticalMethods.addAll(nonCriticalMethods);
  }

  protected synchronized void initWeb3jService(final Web3jService web3jService) {
    this.web3jService = web3jService;
    this.eth1Web3j = Web3j.build(web3jService);
    this.initialized = true;
  }

  private void throwIfNotInitialized() {
    if (!initialized) {
      throw new RuntimeException("Web3JClient is not initialized");
    }
  }

  public <T> SafeFuture<Response<T>> doRequest(
      Request<?, ? extends org.web3j.protocol.core.Response<T>> web3jRequest,
      final Duration timeout) {
    throwIfNotInitialized();
    return SafeFuture.of(web3jRequest.sendAsync())
        .orTimeout(timeout)
        .handle(
            (response, exception) -> {
              final boolean isCriticalRequest = isCriticalRequest(web3jRequest);
              if (exception != null) {
                final boolean couldBeAuthError = isAuthenticationException(exception);
                handleError(isCriticalRequest, exception, couldBeAuthError);
                return Response.withErrorMessage(getMessageOrSimpleName(exception));
              } else if (response.hasError()) {
                final String errorMessage =
                    response.getError().getCode() + ": " + response.getError().getMessage();
                handleError(isCriticalRequest, new IOException(errorMessage), false);
                return Response.withErrorMessage(errorMessage);
              } else {
                handleSuccess(isCriticalRequest);
                return new Response<>(response.getResult());
              }
            });
  }

  private boolean isCriticalRequest(Request<?, ?> request) {
    return !nonCriticalMethods.contains(request.getMethod());
  }

  protected void handleError(final Throwable error) {
    handleError(true, error, false);
  }

  protected void handleError(final Throwable error, final boolean couldBeAuthError) {
    handleError(true, error, couldBeAuthError);
  }

  protected synchronized void handleError(
      final boolean isCritical, final Throwable error, final boolean couldBeAuthError) {
    if (isCritical && shouldReportError()) {
      logExecutionClientError(error, couldBeAuthError);
      executionClientEventsPublisher.onAvailabilityUpdated(false);
    }
  }

  protected synchronized void handleSuccess(final boolean isCriticalRequest) {
    if (isCriticalRequest) {
      if (lastErrorTime == STARTUP_LAST_ERROR_TIME) {
        eventLog.executionClientIsOnline();
        executionClientEventsPublisher.onAvailabilityUpdated(true);
      } else if (lastErrorTime != NO_ERROR_TIME) {
        eventLog.executionClientRecovered();
        executionClientEventsPublisher.onAvailabilityUpdated(true);
      }
      lastErrorTime = NO_ERROR_TIME;
    }
  }

  public synchronized Web3jService getWeb3jService() {
    throwIfNotInitialized();
    return web3jService;
  }

  public synchronized Web3j getEth1Web3j() {
    throwIfNotInitialized();
    return eth1Web3j;
  }

  public boolean isWebsocketsClient() {
    return false;
  }

  private boolean isAuthenticationException(final Throwable exception) {
    if (!(exception instanceof ClientConnectionException)) {
      return false;
    }
    final String message = exception.getMessage();
    return message.contains("received: 401") || message.contains("received: 403");
  }

  private boolean shouldReportError() {
    final long timeNow = timeProvider.getTimeInMillis().longValue();
    if (lastErrorTime == NO_ERROR_TIME || timeNow - lastErrorTime > ERROR_REPEAT_DELAY_MILLIS) {
      lastErrorTime = timeNow;
      return true;
    }
    return false;
  }

  private void logExecutionClientError(final Throwable error, final boolean couldBeAuthError) {
    if (error instanceof TimeoutException) {
      eventLog.executionClientRequestTimedOut();
    } else {
      eventLog.executionClientRequestFailed(error, couldBeAuthError);
    }
  }
}
