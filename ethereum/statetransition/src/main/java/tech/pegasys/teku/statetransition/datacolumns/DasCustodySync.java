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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;

public class DasCustodySync implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();
  public static final int MAX_PENDING_COLUMN_REQUESTS = 10 * 1024;
  public static final int MIN_PENDING_COLUMN_REQUESTS = 2 * 1024;

  private final DataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;
  private final int minPendingColumnRequests;
  private final int maxPendingColumnRequests;
  private final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;

  private final Map<DataColumnSlotAndIdentifier, PendingRequest> pendingRequests =
      new ConcurrentHashMap<>();
  private boolean started = false;
  private boolean coolDownTillNextSlot = false;
  private volatile boolean fillingUp = false;
  private final AtomicLong syncedColumnCount = new AtomicLong();
  private final AtomicReference<UInt64> currentSlot = new AtomicReference<>(ZERO);
  private volatile boolean inSync = false;

  DasCustodySync(
      final DataColumnSidecarCustody custody,
      final DataColumnSidecarRetriever retriever,
      final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator,
      final int minPendingColumnRequests,
      final int maxPendingColumnRequests) {
    this.custody = custody;
    this.retriever = retriever;
    this.minCustodyPeriodSlotCalculator = minCustodyPeriodSlotCalculator;
    this.minPendingColumnRequests = minPendingColumnRequests;
    this.maxPendingColumnRequests = maxPendingColumnRequests;
  }

  public DasCustodySync(
      final DataColumnSidecarCustody custody,
      final DataColumnSidecarRetriever retriever,
      final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator) {
    this(
        custody,
        retriever,
        minCustodyPeriodSlotCalculator,
        MIN_PENDING_COLUMN_REQUESTS,
        MAX_PENDING_COLUMN_REQUESTS);
  }

  public synchronized void onNodeSyncStateChanged(final boolean inSync) {
    this.inSync = inSync;
  }

  private void onRequestComplete(final PendingRequest request, final DataColumnSidecar response) {
    custody
        .onNewValidatedDataColumnSidecar(response, RemoteOrigin.RPC)
        .thenRun(
            () -> {
              synchronized (this) {
                pendingRequests.remove(request.columnId);
                syncedColumnCount.incrementAndGet();
                fillUpIfNeeded();
              }
            })
        .finishStackTrace();
  }

  private boolean wasCancelledImplicitly(final Throwable exception) {
    return exception instanceof CancellationException
        || (exception instanceof CompletionException
            && exception.getCause() instanceof CancellationException);
  }

  private synchronized void onRequestException(
      final PendingRequest request, final Throwable exception) {
    if (wasCancelledImplicitly(exception)) {
      // request was cancelled explicitly here
    } else {
      LOG.warn("Unexpected exception for request " + request, exception);
    }
  }

  private void fillUpIfNeeded() {
    if (started
        && pendingRequests.size() <= minPendingColumnRequests
        && !coolDownTillNextSlot
        && !fillingUp
        && inSync) {
      fillUp();
    }
  }

  @VisibleForTesting
  void fillUp() {
    fillingUp = true;
    final SafeFuture<Set<DataColumnSlotAndIdentifier>> missingColumnsFuture =
        custody.retrieveMissingColumns().limit(maxPendingColumnRequests).collect(new HashSet<>());

    missingColumnsFuture
        .thenAccept(
            missingColumns -> {
              if (missingColumns.size() < maxPendingColumnRequests) {
                // we've got all missing columns no need to poll until the next slot
                coolDownTillNextSlot = true;
              }

              final Set<DataColumnSlotAndIdentifier> missingColumnsToRequest =
                  missingColumns.stream()
                      .filter(columnSlotId -> !pendingRequests.containsKey(columnSlotId))
                      .collect(Collectors.toSet());

              final Collection<PendingRequest> pendingRequestsToCancel =
                  removeNotMissingPendingRequests(missingColumns);
              pendingRequestsToCancel.forEach(
                  pendingRequest -> pendingRequest.columnPromise().cancel(true));

              for (final DataColumnSlotAndIdentifier missingColumn : missingColumnsToRequest) {
                if (missingColumn
                    .slot()
                    .isGreaterThan(
                        minCustodyPeriodSlotCalculator
                            .getMinCustodyPeriodSlot(currentSlot.get())
                            .orElseThrow())) {
                  addPendingRequest(missingColumn);
                } else {
                  LOG.debug(
                      "Skipping column from slot {} as it is outside of the retention period now.",
                      missingColumn.getSlotAndBlockRoot().getSlot());
                }
              }

              if (LOG.isTraceEnabled()) {
                final Set<UInt64> missingSlots =
                    missingColumnsToRequest.stream()
                        .map(DataColumnSlotAndIdentifier::slot)
                        .collect(Collectors.toSet());
                LOG.trace(
                    "DataCustodySync.fillUp: synced={} pending={}, missingColumns={}({})",
                    syncedColumnCount,
                    pendingRequests.size(),
                    missingColumnsToRequest.size(),
                    missingSlots);
              }
            })
        .whenComplete((__, ___) -> fillingUp = false)
        .finishStackTrace();
  }

  private synchronized void addPendingRequest(final DataColumnSlotAndIdentifier missingColumn) {
    if (pendingRequests.containsKey(missingColumn)) {
      return;
    }
    final SafeFuture<DataColumnSidecar> future = retriever.retrieve(missingColumn);
    final PendingRequest request = new PendingRequest(missingColumn, future);
    pendingRequests.put(missingColumn, request);
    future.finish(
        response -> onRequestComplete(request, response), err -> onRequestException(request, err));
  }

  private synchronized Collection<PendingRequest> removeNotMissingPendingRequests(
      final Set<DataColumnSlotAndIdentifier> missingColumns) {
    return removeIf(pendingRequests, id -> !missingColumns.contains(id));
  }

  public void start() {
    started = true;
    fillUp();
  }

  public synchronized void stop() {
    started = false;
    for (final PendingRequest request : pendingRequests.values()) {
      request.columnPromise.cancel(true);
    }
    pendingRequests.clear();
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot.set(slot);
    coolDownTillNextSlot = false;
    fillUpIfNeeded();
  }

  private record PendingRequest(
      DataColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> columnPromise) {}

  private static <K, V> List<V> removeIf(final Map<K, V> map, final Predicate<K> removeFilter) {
    final ArrayList<V> removedValues = new ArrayList<>();
    final Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<K, V> entry = iterator.next();
      if (removeFilter.test(entry.getKey())) {
        removedValues.add(entry.getValue());
        iterator.remove();
      }
    }
    return removedValues;
  }
}
