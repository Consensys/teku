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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class ResponseStreamImpl<O extends SimpleOffsetSerializable> implements ResponseStream<O> {

  private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();
  private ResponseListener<O> responseListener;

  @Override
  public CompletableFuture<O> expectSingleResponse() {
    final AtomicReference<O> firstResponse = new AtomicReference<>();
    expectMultipleResponses(
        response -> {
          if (!firstResponse.compareAndSet(null, response)) {
            completionFuture.completeExceptionally(
                new IllegalStateException(
                    "Received multiple responses when single response expected"));
          }
        });
    return completionFuture.thenApply(
        done -> {
          checkNotNull(firstResponse.get(), "No response received when single response expected");
          return firstResponse.get();
        });
  }

  @Override
  public CompletableFuture<Void> expectNoResponse() {
    expectMultipleResponses(
        data ->
            completionFuture.completeExceptionally(
                new IllegalStateException("Received response when none expected")));
    return completionFuture;
  }

  @Override
  public CompletableFuture<Void> expectMultipleResponses(final ResponseListener<O> listener) {
    Preconditions.checkArgument(
        responseListener == null, "Multiple calls to 'expect' methods not allowed");
    responseListener = listener;
    return completionFuture;
  }

  public void respond(final O data) {
    checkNotNull(responseListener, "Must call an 'expect' method");
    responseListener.onResponse(data);
  }

  public void completeSuccessfully() {
    completionFuture.complete(null);
  }

  public void completeWithError(final Throwable error) {
    completionFuture.completeExceptionally(error);
  }
}
