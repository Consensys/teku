/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.storage.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.storage.api.SidecarUpdateChannel;

public class BatchingSidecarUpdateChannel implements SidecarUpdateChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final int DEFAULT_BATCH_SIZE = 100;
  private static final Duration DEFAULT_MAX_DELAY = Duration.ofMillis(100);

  private record PendingItem(DataColumnSidecar sidecar, SafeFuture<Void> future) {}

  private final SidecarUpdateChannel delegate;
  private final AsyncRunner asyncRunner;
  private final int maxBatchSize;
  private final Duration maxDelay;

  private final Object lock = new Object();
  private final List<PendingItem> pendingItems = new ArrayList<>();
  private boolean flushScheduled = false;

  public BatchingSidecarUpdateChannel(
      final SidecarUpdateChannel delegate, final AsyncRunner asyncRunner) {
    this(delegate, asyncRunner, DEFAULT_BATCH_SIZE, DEFAULT_MAX_DELAY);
  }

  public BatchingSidecarUpdateChannel(
      final SidecarUpdateChannel delegate,
      final AsyncRunner asyncRunner,
      final int maxBatchSize,
      final Duration maxDelay) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.maxBatchSize = maxBatchSize;
    this.maxDelay = maxDelay;
  }

  @Override
  public SafeFuture<Void> onFirstCustodyIncompleteSlot(final UInt64 slot) {
    return delegate.onFirstCustodyIncompleteSlot(slot);
  }

  @Override
  public SafeFuture<Void> onEarliestAvailableDataColumnSlot(final UInt64 slot) {
    return delegate.onEarliestAvailableDataColumnSlot(slot);
  }

  @Override
  public SafeFuture<Void> onNewSidecar(final DataColumnSidecar sidecar) {
    final SafeFuture<Void> result = new SafeFuture<>();
    boolean shouldFlush = false;
    synchronized (lock) {
      pendingItems.add(new PendingItem(sidecar, result));
      if (pendingItems.size() >= maxBatchSize) {
        shouldFlush = true;
      } else if (!flushScheduled) {
        flushScheduled = true;
        asyncRunner.runAfterDelay(this::flushBatch, maxDelay).finishStackTrace();
      }
    }
    if (shouldFlush) {
      flushBatch();
    }
    return result;
  }

  @Override
  public SafeFuture<Void> onNewSidecars(final List<DataColumnSidecar> sidecars) {
    return delegate.onNewSidecars(sidecars);
  }

  private void flushBatch() {
    final List<DataColumnSidecar> batch;
    final List<SafeFuture<Void>> futures;

    synchronized (lock) {
      if (pendingItems.isEmpty()) {
        flushScheduled = false;
        return;
      }

      batch = new ArrayList<>(pendingItems.size());
      futures = new ArrayList<>(pendingItems.size());

      pendingItems.removeIf(
          item -> {
            batch.add(item.sidecar);
            futures.add(item.future);
            return true;
          });

      flushScheduled = false;
    }

    LOG.debug("Executing a batch of size {}", batch.size());

    delegate
        .onNewSidecars(batch)
        .finish(
            () -> futures.forEach(f -> f.complete(null)),
            error -> futures.forEach(f -> f.completeExceptionally(error)));
  }
}
