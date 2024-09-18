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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;

public class DasCustodySync implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final UpdatableDataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;
  private final int maxPendingColumnRequests;
  private final int minPendingColumnRequests;

  private final Map<DataColumnSlotAndIdentifier, PendingRequest> pendingRequests =
      new ConcurrentHashMap<>();
  private boolean started = false;
  private boolean coolDownTillNextSlot = false;
  private volatile boolean fillingUp = false;
  private final AtomicLong syncedColumnCount = new AtomicLong();

  public DasCustodySync(
      UpdatableDataColumnSidecarCustody custody,
      DataColumnSidecarRetriever retriever,
      int maxPendingColumnRequests,
      int minPendingColumnRequests) {
    this.custody = custody;
    this.retriever = retriever;
    this.maxPendingColumnRequests = maxPendingColumnRequests;
    this.minPendingColumnRequests = minPendingColumnRequests;
  }

  public DasCustodySync(
      UpdatableDataColumnSidecarCustody custody, DataColumnSidecarRetriever retriever) {
    this(custody, retriever, 10 * 1024, 2 * 1024);
  }

  private void onRequestComplete(PendingRequest request, DataColumnSidecar response) {
    custody
        .onNewValidatedDataColumnSidecar(response)
        .thenRun(
            () -> {
              synchronized (this) {
                pendingRequests.remove(request.columnId);
                syncedColumnCount.incrementAndGet();
                fillUpIfNeeded();
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private boolean wasCancelledImplicitly(Throwable exception) {
    return exception instanceof CancellationException
        || (exception instanceof CompletionException
            && exception.getCause() instanceof CancellationException);
  }

  private synchronized void onRequestException(PendingRequest request, Throwable exception) {
    if (wasCancelledImplicitly(exception)) {
      // request was cancelled explicitly here
    } else {
      LOG.warn("[nyota] Unexpected exception for request " + request, exception);
    }
  }

  private void fillUpIfNeeded() {
    if (started
        && pendingRequests.size() <= minPendingColumnRequests
        && !coolDownTillNextSlot
        && !fillingUp) {
      fillUp();
    }
  }

  private void fillUp() {
    fillingUp = true;
    int newRequestCount = maxPendingColumnRequests - pendingRequests.size();
    final SafeFuture<Set<DataColumnSlotAndIdentifier>> missingColumnsToRequestFuture =
        custody
            .retrieveMissingColumns()
            .filter(columnSlotId -> !pendingRequests.containsKey(columnSlotId))
            .limit(newRequestCount)
            .collect(new HashSet<>());

    // TODO cancel those which are not missing anymore for whatever reason

    missingColumnsToRequestFuture
        .thenPeek(
            missingColumnsToRequest -> {
              for (DataColumnSlotAndIdentifier missingColumn : missingColumnsToRequest) {
                addPendingRequest(missingColumn);
              }

              if (missingColumnsToRequest.size() < newRequestCount) {
                coolDownTillNextSlot = true;
              }

              {
                Set<UInt64> missingSlots =
                    missingColumnsToRequest.stream()
                        .map(DataColumnSlotAndIdentifier::slot)
                        .collect(Collectors.toSet());
                LOG.info(
                    "[nyota] DataCustodySync.fillUp: synced={} pending={}, missingColumns={}({})",
                    syncedColumnCount,
                    pendingRequests.size(),
                    missingColumnsToRequest.size(),
                    missingSlots);
              }
            })
        .whenComplete((__, ___) -> fillingUp = false)
        .ifExceptionGetsHereRaiseABug();
  }

  private synchronized void addPendingRequest(final DataColumnSlotAndIdentifier missingColumn) {
    if (pendingRequests.containsKey(missingColumn)) {
      return;
    }
    final SafeFuture<DataColumnSidecar> promise = retriever.retrieve(missingColumn);
    final PendingRequest request = new PendingRequest(missingColumn, promise);
    pendingRequests.put(missingColumn, request);
    promise.finish(
        response -> onRequestComplete(request, response), err -> onRequestException(request, err));
  }

  public void start() {
    started = true;
    fillUp();
  }

  public synchronized void stop() {
    started = false;
    for (PendingRequest request : pendingRequests.values()) {
      request.columnPromise.cancel(true);
    }
    pendingRequests.clear();
  }

  @Override
  public void onSlot(UInt64 slot) {
    coolDownTillNextSlot = false;
    fillUpIfNeeded();
  }

  private record PendingRequest(
      DataColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> columnPromise) {}
}
