/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.rpc.core;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;

public class Eth2RpcResponseHandler<TResponse, TExpectedResult>
    implements RpcResponseHandler<TResponse> {
  private final RpcResponseListener<TResponse> listener;
  private final SafeFuture<Void> completed;
  private final SafeFuture<TExpectedResult> expectedResult;

  protected Eth2RpcResponseHandler(
      final RpcResponseListener<TResponse> listener,
      final SafeFuture<Void> completed,
      final SafeFuture<TExpectedResult> expectedResult) {
    this.listener = listener;
    this.completed = completed;
    this.expectedResult = expectedResult;
  }

  public static <T> Eth2RpcResponseHandler<T, Void> expectMultipleResponses(
      final RpcResponseListener<T> listener) {
    final SafeFuture<Void> completed = new SafeFuture<>();
    return new Eth2RpcResponseHandler<>(listener, completed, completed);
  }

  public static <TResponse> Eth2RpcResponseHandler<TResponse, Void> expectNoResponse() {
    return expectMultipleResponses(
        response -> {
          throw new InvalidRpcResponseException("Received response when none expected");
        });
  }

  public static <T> Eth2RpcResponseHandler<T, Optional<T>> expectOptionalResponse() {
    final AtomicReference<T> firstResponse = new AtomicReference<>();
    final RpcResponseListener<T> listener = createSingleResponseListener(firstResponse);

    final SafeFuture<Void> completed = new SafeFuture<>();
    final SafeFuture<Optional<T>> resultFuture =
        completed.thenApply(__ -> Optional.ofNullable(firstResponse.get()));

    return new Eth2RpcResponseHandler<>(listener, completed, resultFuture);
  }

  public static <T> Eth2RpcResponseHandler<T, T> expectSingleResponse() {
    final AtomicReference<T> firstResponse = new AtomicReference<>();
    final RpcResponseListener<T> responseHandler = createSingleResponseListener(firstResponse);

    final SafeFuture<Void> completed = new SafeFuture<>();
    final SafeFuture<T> resultFuture =
        completed.thenApply(
            __ -> {
              final T result = firstResponse.get();
              if (result == null) {
                throw new InvalidRpcResponseException(
                    "No response received when single response expected");
              }
              return result;
            });

    return new Eth2RpcResponseHandler<>(responseHandler, completed, resultFuture);
  }

  private static <T> RpcResponseListener<T> createSingleResponseListener(
      AtomicReference<T> firstResponse) {
    return response -> {
      if (!firstResponse.compareAndSet(null, response)) {
        throw new InvalidRpcResponseException(
            "Received multiple responses when single response expected");
      }
      return SafeFuture.COMPLETE;
    };
  }

  public SafeFuture<Void> getCompletedFuture() {
    return completed;
  }

  public SafeFuture<TExpectedResult> getResult() {
    return completed.thenCompose(__ -> expectedResult);
  }

  @Override
  public void onCompleted(Optional<? extends Throwable> error) {
    error.ifPresentOrElse(completed::completeExceptionally, () -> completed.complete(null));
  }

  @Override
  public SafeFuture<?> onResponse(final TResponse response) {
    return listener.onResponse(response);
  }

  public static class InvalidRpcResponseException extends RuntimeException {

    public InvalidRpcResponseException(final String errorMessage) {
      super(errorMessage);
    }
  }
}
