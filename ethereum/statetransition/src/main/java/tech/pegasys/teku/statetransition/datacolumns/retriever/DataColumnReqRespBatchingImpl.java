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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStreamHandler;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public class DataColumnReqRespBatchingImpl implements DataColumnReqResp {
  private final BatchDataColumnsByRootReqResp batchRpc;

  public DataColumnReqRespBatchingImpl(final BatchDataColumnsByRootReqResp batchRpc) {
    this.batchRpc = batchRpc;
  }

  private record RequestEntry(
      UInt256 nodeId,
      DataColumnIdentifier columnIdentifier,
      SafeFuture<DataColumnSidecar> promise) {}

  private final ConcurrentLinkedQueue<RequestEntry> bufferedRequests =
      new ConcurrentLinkedQueue<>();

  @Override
  public SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
      final UInt256 nodeId, final DataColumnIdentifier columnIdentifier) {
    RequestEntry entry = new RequestEntry(nodeId, columnIdentifier, new SafeFuture<>());
    bufferedRequests.add(entry);
    return entry.promise();
  }

  @Override
  public void flush() {
    Map<UInt256, List<RequestEntry>> byNodes = new HashMap<>();
    RequestEntry request;
    while ((request = bufferedRequests.poll()) != null) {
      byNodes.computeIfAbsent(request.nodeId, __ -> new ArrayList<>()).add(request);
    }
    for (Map.Entry<UInt256, List<RequestEntry>> entry : byNodes.entrySet()) {
      flushForNode(entry.getKey(), entry.getValue());
    }
  }

  private void flushForNode(final UInt256 nodeId, final List<RequestEntry> nodeRequests) {
    AsyncStream<DataColumnSidecar> response =
        batchRpc.requestDataColumnSidecarsByRoot(
            nodeId, nodeRequests.stream().map(e -> e.columnIdentifier).toList());

    response.consume(
        new AsyncStreamHandler<>() {
          private final AtomicInteger count = new AtomicInteger();
          private final Map<DataColumnIdentifier, RequestEntry> requestsNyColumnId =
              nodeRequests.stream()
                  .collect(Collectors.toMap(RequestEntry::columnIdentifier, req -> req));

          @Override
          public SafeFuture<Boolean> onNext(final DataColumnSidecar dataColumnSidecar) {
            final DataColumnIdentifier colId =
                DataColumnIdentifier.createFromSidecar(dataColumnSidecar);
            final RequestEntry request = requestsNyColumnId.get(colId);
            if (request == null) {
              return SafeFuture.failedFuture(
                  new IllegalArgumentException(
                      "Responded data column was not requested: " + colId));
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
    return batchRpc.getCurrentRequestLimit(nodeId);
  }
}
