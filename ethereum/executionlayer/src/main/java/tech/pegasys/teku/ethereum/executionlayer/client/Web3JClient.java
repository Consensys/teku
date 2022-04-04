/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionlayer.client;

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public abstract class Web3JClient {
  private static final int ERROR_REPEAT_DELAY_MILLIS = 30 * 1000;
  private static final int NO_ERROR_TIME = -1;
  private final TimeProvider timeProvider;
  private Web3jService web3jService;
  private Web3j eth1Web3j;
  private final AtomicLong lastError = new AtomicLong(NO_ERROR_TIME);
  private final List<
          Function<
              Request<?, ? extends org.web3j.protocol.core.Response<?>>,
              Request<?, ? extends org.web3j.protocol.core.Response<?>>>>
      requestAdapters = new ArrayList<>();
  private boolean initialized = false;

  protected Web3JClient(TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
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

  public void addRequestAdapter(
      Function<
              Request<?, ? extends org.web3j.protocol.core.Response<?>>,
              Request<?, ? extends org.web3j.protocol.core.Response<?>>>
          requestAdapter) {
    requestAdapters.add(requestAdapter);
  }

  @SuppressWarnings("unchecked")
  protected <T> Request<?, ? extends org.web3j.protocol.core.Response<T>> applyRequestAdapters(
      Request<?, ? extends org.web3j.protocol.core.Response<T>> request) {
    return (Request<?, ? extends org.web3j.protocol.core.Response<T>>)
        requestAdapters.stream().reduce(Function.identity(), Function::andThen).apply(request);
  }

  protected <T> SafeFuture<T> doWeb3JRequest(CompletableFuture<T> web3Request) {
    throwIfNotInitialized();
    return SafeFuture.of(web3Request)
        .catchAndRethrow(this::handleError)
        .thenPeek(__ -> handleSuccess());
  }

  protected <T> SafeFuture<Response<T>> doRequest(
      Request<?, ? extends org.web3j.protocol.core.Response<T>> web3jRequest) {
    throwIfNotInitialized();
    CompletableFuture<Response<T>> responseFuture =
        applyRequestAdapters(web3jRequest)
            .sendAsync()
            .handle(
                (response, exception) -> {
                  if (exception != null) {
                    handleError(exception);
                    return new Response<>(exception.getMessage());
                  } else if (response.hasError()) {
                    final String errorMessage =
                        response.getError().getCode() + ": " + response.getError().getMessage();
                    handleError(new IOException(errorMessage));
                    return new Response<>(errorMessage);
                  } else {
                    handleSuccess();
                    return new Response<>(response.getResult());
                  }
                });
    return SafeFuture.of(responseFuture);
  }

  protected void handleError(Throwable error) {
    final long errorTime = lastError.get();
    if (errorTime == NO_ERROR_TIME
        || timeProvider.getTimeInMillis().longValue() - errorTime > ERROR_REPEAT_DELAY_MILLIS) {
      if (lastError.compareAndSet(errorTime, timeProvider.getTimeInMillis().longValue())) {
        EVENT_LOG.executionClientIsOffline(error);
      }
    }
  }

  protected void handleSuccess() {
    if (lastError.getAndUpdate(x -> NO_ERROR_TIME) != NO_ERROR_TIME) {
      EVENT_LOG.executionClientIsOnline();
    }
  }

  protected synchronized Web3jService getWeb3jService() {
    throwIfNotInitialized();
    return web3jService;
  }

  protected synchronized Web3j getEth1Web3j() {
    throwIfNotInitialized();
    return eth1Web3j;
  }
}
