/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public class DasLongPollCustody implements DataColumnSidecarCustody, SlotEventsChannel {

  public interface GossipWaitTimeoutCalculator {
    /** Returns the duration to wait for a column to be gossiped */
    Duration getGossipWaitTimeout(UInt64 slot);
  }

  private final DataColumnSidecarCustody delegate;
  private final AsyncRunner asyncRunner;
  private final GossipWaitTimeoutCalculator gossipWaitTimeoutCalculator;

  @VisibleForTesting final PendingRequests pendingRequests = new PendingRequests();

  public DasLongPollCustody(
      final DataColumnSidecarCustody delegate,
      final AsyncRunner asyncRunner,
      GossipWaitTimeoutCalculator gossipWaitTimeoutCalculator) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.gossipWaitTimeoutCalculator = gossipWaitTimeoutCalculator;
  }

  @Override
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    return delegate
        .onNewValidatedDataColumnSidecar(dataColumnSidecar, remoteOrigin)
        .thenRun(
            () -> {
              final List<SafeFuture<Optional<DataColumnSidecar>>> pendingRequests =
                  this.pendingRequests.remove(
                      DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar));
              for (SafeFuture<Optional<DataColumnSidecar>> pendingRequest : pendingRequests) {
                pendingRequest.complete(Optional.of(dataColumnSidecar));
              }
            });
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    final SafeFuture<Optional<DataColumnSidecar>> pendingFuture = addPendingRequest(columnId);
    final SafeFuture<Optional<DataColumnSidecar>> existingFuture =
        delegate.getCustodyDataColumnSidecar(columnId);
    return anyNonEmpty(pendingFuture, existingFuture);
  }

  @Override
  public SafeFuture<Boolean> hasCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    final SafeFuture<Optional<Boolean>> pendingFuture =
        addPendingRequest(columnId).thenApply(maybeSidecar -> maybeSidecar.map(__ -> true));
    final SafeFuture<Optional<Boolean>> existingFuture =
        delegate
            .hasCustodyDataColumnSidecar(columnId)
            .thenApply(doesExist -> doesExist ? Optional.of(true) : Optional.empty());
    return anyNonEmpty(pendingFuture, existingFuture)
        .thenApply(maybeResult -> maybeResult.orElse(false));
  }

  @Override
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
    return delegate.retrieveMissingColumns();
  }

  private SafeFuture<Optional<DataColumnSidecar>> addPendingRequest(
      final DataColumnSlotAndIdentifier columnId) {
    final SafeFuture<Optional<DataColumnSidecar>> promise = new SafeFuture<>();
    pendingRequests.add(columnId, promise);
    return promise;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final Duration waitPeriodForCurrentSlot =
        gossipWaitTimeoutCalculator.getGossipWaitTimeout(slot);
    asyncRunner
        .runAfterDelay(
            () -> pendingRequests.setNoWaitSlot(slot.increment()), waitPeriodForCurrentSlot)
        .finishStackTrace();
  }

  private static <T> SafeFuture<Optional<T>> anyNonEmpty(
      final SafeFuture<Optional<T>> future1, final SafeFuture<Optional<T>> future2) {
    return SafeFuture.anyOf(future1, future2)
        .thenCompose(
            __ -> {
              if (future1.isCompletedNormally()) {
                if (future1.getImmediately().isPresent()) {
                  return future1;
                } else {
                  return future2;
                }
              } else if (future2.isCompletedNormally()) {
                if (future2.getImmediately().isPresent()) {
                  return future2;
                } else {
                  return future1;
                }
              } else {
                throw new IllegalStateException("Unexpected: None of futures is complete");
              }
            });
  }

  @VisibleForTesting
  static class PendingRequests {
    final NavigableMap<DataColumnSlotAndIdentifier, List<SafeFuture<Optional<DataColumnSidecar>>>>
        requests = new TreeMap<>();
    private UInt64 noWaitSlot = UInt64.ZERO;

    void add(
        final DataColumnSlotAndIdentifier columnId,
        final SafeFuture<Optional<DataColumnSidecar>> promise) {
      final boolean cancelImmediately;
      synchronized (this) {
        if (columnId.slot().isLessThan(noWaitSlot)) {
          cancelImmediately = true;
        } else {
          clearCancelledPendingRequests();
          requests.computeIfAbsent(columnId, __ -> new ArrayList<>()).add(promise);
          cancelImmediately = false;
        }
      }
      if (cancelImmediately) {
        promise.complete(Optional.empty());
      }
    }

    synchronized List<SafeFuture<Optional<DataColumnSidecar>>> remove(
        final DataColumnSlotAndIdentifier columnId) {
      final List<SafeFuture<Optional<DataColumnSidecar>>> ret = requests.remove(columnId);
      return ret == null ? Collections.emptyList() : ret;
    }

    void setNoWaitSlot(final UInt64 tillSlotExclusive) {
      final List<SafeFuture<Optional<DataColumnSidecar>>> toCancel;
      synchronized (this) {
        this.noWaitSlot = tillSlotExclusive;
        final SortedMap<DataColumnSlotAndIdentifier, List<SafeFuture<Optional<DataColumnSidecar>>>>
            toRemove =
                requests.headMap(
                    DataColumnSlotAndIdentifier.minimalComparableForSlot(tillSlotExclusive));
        toCancel = toRemove.values().stream().flatMap(Collection::stream).toList();
        toRemove.clear();
      }
      toCancel.forEach(future -> future.complete(Optional.empty()));
    }

    private void clearCancelledPendingRequests() {
      requests.values().forEach(promises -> promises.removeIf(CompletableFuture::isDone));
      requests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
  }
}
