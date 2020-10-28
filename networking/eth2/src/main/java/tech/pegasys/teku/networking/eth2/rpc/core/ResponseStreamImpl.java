/*
 * Copyright 2019 ConsenSys AG.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class ResponseStreamImpl<O> implements ResponseStream<O> {

  private final SafeFuture<Void> completionFuture = new SafeFuture<>();
  private final AtomicInteger receivedResponseCount = new AtomicInteger(0);
  private volatile ResponseStreamListener<O> responseListener;

  @Override
  public SafeFuture<Optional<O>> expectOptionalResponse() {
    final AtomicReference<O> firstResponse = new AtomicReference<>();
    return expectMultipleResponses(
            response -> {
              if (!firstResponse.compareAndSet(null, response)) {
                throw new IllegalStateException(
                    "Received multiple responses when single response expected");
              }
              return SafeFuture.COMPLETE;
            })
        .thenApply(done -> Optional.ofNullable(firstResponse.get()));
  }

  @Override
  public SafeFuture<Void> expectNoResponse() {
    return expectMultipleResponses(
        data -> {
          throw new IllegalStateException("Received response when none expected");
        });
  }

  @Override
  public SafeFuture<Void> expectMultipleResponses(final ResponseStreamListener<O> listener) {
    Preconditions.checkArgument(
        responseListener == null, "Multiple calls to 'expect' methods not allowed");
    responseListener = listener;
    return completionFuture;
  }

  public SafeFuture<?> respond(final O data) {
    checkNotNull(responseListener, "Must call an 'expect' method");
    receivedResponseCount.incrementAndGet();
    return responseListener.onResponse(data);
  }

  public int getResponseChunkCount() {
    return receivedResponseCount.get();
  }

  public void completeSuccessfully() {
    completionFuture.complete(null);
  }

  public void completeWithError(final Throwable error) {
    completionFuture.completeExceptionally(error);
  }

  public void subscribeCompleted(RequestCompleteSubscriber subscriber) {
    completionFuture.finish(
        res -> subscriber.onRequestComplete(true), err -> subscriber.onRequestComplete(false));
  }

  public interface RequestCompleteSubscriber {
    void onRequestComplete(boolean successful);
  }
}
