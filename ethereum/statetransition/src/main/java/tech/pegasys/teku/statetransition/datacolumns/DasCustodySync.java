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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;

public class DasCustodySync implements SlotEventsChannel {

  private final UpdatableDataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;
  private final int maxPendingColumnRequests = 1024;
  private final int minPendingColumnRequests = 512;

  private Map<ColumnSlotAndIdentifier, PendingRequest> pendingRequests = new HashMap<>();
  private boolean started = false;

  public DasCustodySync(
      UpdatableDataColumnSidecarCustody custody, DataColumnSidecarRetriever retriever) {
    this.custody = custody;
    this.retriever = retriever;
  }

  private synchronized void onRequestComplete(PendingRequest request) {
    DataColumnSidecar result = request.columnPromise.join();
    custody.onNewValidatedDataColumnSidecar(result);
    pendingRequests.remove(request.columnId);
    fillUpIfNeeded();
  }

  private void fillUpIfNeeded() {
    if (started && pendingRequests.size() <= minPendingColumnRequests) {
      fillUp();
    }
  }

  private synchronized void fillUp() {
    int newRequestCount = maxPendingColumnRequests - pendingRequests.size();
    Set<ColumnSlotAndIdentifier> missingColumnsToRequest =
        custody
            .streamMissingColumns()
            .filter(c -> !pendingRequests.containsKey(c))
            .limit(newRequestCount)
            .collect(Collectors.toSet());

    // cancel those which are not missing anymore for whatever reason
    Iterator<Map.Entry<ColumnSlotAndIdentifier, PendingRequest>> it =
        pendingRequests.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<ColumnSlotAndIdentifier, PendingRequest> pendingEntry = it.next();
      if (!missingColumnsToRequest.contains(pendingEntry.getKey())) {
        pendingEntry.getValue().columnPromise().cancel(true);
        it.remove();
      }
    }

    for (ColumnSlotAndIdentifier missingColumn : missingColumnsToRequest) {
      SafeFuture<DataColumnSidecar> promise = retriever.retrieve(missingColumn);
      PendingRequest request = new PendingRequest(missingColumn, promise);
      pendingRequests.put(missingColumn, request);
      promise.thenAccept(__ -> onRequestComplete(request)).ifExceptionGetsHereRaiseABug();
    }
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
    fillUpIfNeeded();
  }

  private record PendingRequest(
      ColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> columnPromise) {}
}
