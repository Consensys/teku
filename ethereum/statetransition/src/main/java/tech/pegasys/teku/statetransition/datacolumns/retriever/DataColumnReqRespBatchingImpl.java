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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStreamHandler;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public class DataColumnReqRespBatchingImpl implements DataColumnReqResp {
  private final BatchDataColumnsByRangeReqResp byRangeRpc;

  public DataColumnReqRespBatchingImpl(final BatchDataColumnsByRangeReqResp byRangeRpc) {
    this.byRangeRpc = byRangeRpc;
  }

  private record RequestEntry(
      UInt256 nodeId,
      DataColumnSlotAndIdentifier columnIdentifier,
      SafeFuture<DataColumnSidecar> promise) {}

  private final ConcurrentLinkedQueue<RequestEntry> bufferedRequests =
      new ConcurrentLinkedQueue<>();

  @Override
  public SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
      final UInt256 nodeId, final DataColumnSlotAndIdentifier columnIdentifier) {
    final RequestEntry entry = new RequestEntry(nodeId, columnIdentifier, new SafeFuture<>());
    bufferedRequests.add(entry);
    return entry.promise();
  }

  @Override
  public void flush() {
    final Map<UInt256, List<RequestEntry>> byNodes = new HashMap<>();
    RequestEntry request;
    while ((request = bufferedRequests.poll()) != null) {
      byNodes.computeIfAbsent(request.nodeId, __ -> new ArrayList<>()).add(request);
    }
    for (final Map.Entry<UInt256, List<RequestEntry>> entry : byNodes.entrySet()) {
      flushForNode(entry.getKey(), entry.getValue());
    }
  }

  private record ByRangeRequest(UInt64 start, UInt64 end, Set<UInt64> columns) {}

  private void flushForNode(final UInt256 nodeId, final List<RequestEntry> nodeRequests) {
    if (nodeRequests.isEmpty()) {
      return;
    }

    final NavigableMap<UInt64, Set<UInt64>> bySlotMap = new TreeMap<>();
    nodeRequests.forEach(
        nodeRequest -> {
          final UInt64 slot = nodeRequest.columnIdentifier().slot();
          final UInt64 column = nodeRequest.columnIdentifier.columnIndex();
          if (bySlotMap.containsKey(slot)) {
            bySlotMap.get(slot).add(column);
          } else {
            final Set<UInt64> columns = new HashSet<>();
            columns.add(column);
            bySlotMap.put(slot, columns);
          }
        });

    Map.Entry<UInt64, Set<UInt64>> start = bySlotMap.pollFirstEntry();
    UInt64 last = start.getKey();
    final List<ByRangeRequest> byRangeRequests = new ArrayList<>();
    while (!bySlotMap.isEmpty()) {
      final Map.Entry<UInt64, Set<UInt64>> next = bySlotMap.pollFirstEntry();
      if (next.getKey().equals(last.increment()) && next.getValue().equals(start.getValue())) {
        last = next.getKey();
      } else {
        byRangeRequests.add(new ByRangeRequest(start.getKey(), last, start.getValue()));
        start = next;
        last = next.getKey();
      }
    }

    if (byRangeRequests.isEmpty() || !byRangeRequests.getLast().end().equals(last)) {
      byRangeRequests.add(new ByRangeRequest(start.getKey(), last, start.getValue()));
    }

    final AsyncStream<DataColumnSidecar> responseStream =
        AsyncStream.createUnsafe(byRangeRequests.iterator())
            .flatMap(
                byRangeRequest ->
                    byRangeRpc.requestDataColumnSidecarsByRange(
                        nodeId,
                        byRangeRequest.start(),
                        byRangeRequest.end().increment().minus(byRangeRequest.start()).intValue(),
                        new ArrayList<>(byRangeRequest.columns())));

    responseStream.consume(
        new AsyncStreamHandler<>() {
          private final AtomicInteger count = new AtomicInteger();
          private final Map<DataColumnSlotAndIdentifier, RequestEntry> requestsByColumnId =
              nodeRequests.stream()
                  .collect(Collectors.toMap(RequestEntry::columnIdentifier, req -> req));

          @Override
          public SafeFuture<Boolean> onNext(final DataColumnSidecar dataColumnSidecar) {
            final DataColumnSlotAndIdentifier dataColumnIdentifier =
                DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar);
            final RequestEntry request = requestsByColumnId.get(dataColumnIdentifier);
            if (request == null) {
              return SafeFuture.failedFuture(
                  new IllegalArgumentException(
                      "Responded data column was not requested: " + dataColumnIdentifier));
            } else {
              request.promise().complete(dataColumnSidecar);
              count.incrementAndGet();
              return TRUE_FUTURE;
            }
          }

          @Override
          public void onComplete() {
            nodeRequests.stream()
                .filter(req -> !req.promise().isDone())
                .forEach(
                    req ->
                        req.promise().completeExceptionally(new DasColumnNotAvailableException()));
          }

          @Override
          public void onError(final Throwable err) {
            nodeRequests.forEach(e -> e.promise().completeExceptionally(err));
          }
        });
  }

  @Override
  public int getCurrentRequestLimit(final UInt256 nodeId) {
    return byRangeRpc.getCurrentRequestLimit(nodeId);
  }
}
