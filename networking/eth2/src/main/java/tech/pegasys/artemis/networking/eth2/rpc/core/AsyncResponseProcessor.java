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

package tech.pegasys.artemis.networking.eth2.rpc.core;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;

class AsyncResponseProcessor<TResponse> {
  private static final Logger LOG = LogManager.getLogger();

  private final AtomicInteger responseCount = new AtomicInteger(0);
  private final Queue<TResponse> queuedResponses = new ConcurrentLinkedQueue<>();
  private AtomicBoolean isProcessing = new AtomicBoolean(false);
  private final AtomicBoolean cancelled = new AtomicBoolean(false);

  private final AtomicBoolean allResponsesDelivered = new AtomicBoolean(false);
  private final SafeFuture<Void> finishedProcessing = new SafeFuture<>();

  private final AsyncRunner asyncRunner;
  private final ResponseStreamImpl<TResponse> responseStream;
  private final Consumer<Throwable> onError;

  public AsyncResponseProcessor(
      final AsyncRunner asyncRunner,
      final ResponseStreamImpl<TResponse> responseStream,
      final Consumer<Throwable> onError) {
    this.asyncRunner = asyncRunner;
    this.responseStream = responseStream;
    this.onError = onError;
  }

  public void processResponse(TResponse response) {
    if (allResponsesDelivered.get()) {
      throw new IllegalStateException(
          "New response submitted after closing "
              + this.getClass().getSimpleName()
              + " for new responses: "
              + response);
    }
    if (cancelled.get()) {
      LOG.trace("Request cancelled, dropping response: {}", response);
      return;
    }
    LOG.trace("Queue response for processing: {}", response);
    responseCount.incrementAndGet();
    queuedResponses.add(response);
    checkQueue();
  }

  public int getResponseCount() {
    return responseCount.get();
  }

  /** Stop processing and clear any pending requests */
  private void cancel() {
    cancelled.set(true);
    queuedResponses.clear();
  }

  /**
   * Updates this processor to no longer accept new responses, and returns a future that completes
   * when existing responses are all processed.
   *
   * @return A future that completes when all currently delivered responses are finished processing.
   */
  public SafeFuture<Void> finishProcessing() {
    allResponsesDelivered.set(true);
    checkQueue();

    return finishedProcessing;
  }

  private synchronized void checkQueue() {
    if (!cancelled.get() && !isProcessing.get() && !queuedResponses.isEmpty()) {
      processNextResponse();
    } else if (allResponsesDelivered.get() && !isProcessing.get() && queuedResponses.isEmpty()) {
      finishedProcessing.complete(null);
    }
  }

  private void processNextResponse() {
    isProcessing.set(true);
    final TResponse response = queuedResponses.poll();
    LOG.trace("Process response: {}", response);
    asyncRunner
        .runAsync(
            () -> {
              LOG.trace("Send response to response stream: {}", response);
              responseStream.respond(response);
            })
        .exceptionally(
            (err) -> {
              LOG.trace("Failed to process response: " + response, err);
              cancel();
              onError.accept(err);
              return null;
            })
        .always(
            () -> {
              LOG.trace("Finish processing: {}", response);
              isProcessing.set(false);
              checkQueue();
            });
  }
}
