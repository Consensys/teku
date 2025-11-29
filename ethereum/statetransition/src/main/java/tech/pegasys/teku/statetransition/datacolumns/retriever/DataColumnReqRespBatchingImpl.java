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

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStreamHandler;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public class DataColumnReqRespBatchingImpl implements DataColumnReqResp {
  private static final Logger LOG = LogManager.getLogger();
  // 64 slots * 128 columns at max = 8192, half of MAX_REQUEST_DATA_COLUMN_SIDECARS
  private static final int MAX_BATCH_SIZE = 64;

  private final Spec spec;
  private final BatchDataColumnsByRangeReqResp byRangeRpc;
  private final BatchDataColumnsByRootReqResp byRootRpc;
  private final DataColumnsByRootIdentifierSchema byRootSchema;

  public DataColumnReqRespBatchingImpl(
      final Spec spec,
      final BatchDataColumnsByRangeReqResp byRangeRpc,
      final BatchDataColumnsByRootReqResp byRootRpc,
      final DataColumnsByRootIdentifierSchema byRootSchema) {
    this.spec = spec;
    this.byRangeRpc = byRangeRpc;
    this.byRootRpc = byRootRpc;
    this.byRootSchema = byRootSchema;
  }

  private record RequestEntry(
      UInt256 nodeId,
      Supplier<UInt64> finalizedEpochSupplier,
      DataColumnSlotAndIdentifier columnIdentifier,
      SafeFuture<DataColumnSidecar> promise) {}

  private final ConcurrentLinkedQueue<RequestEntry> bufferedRequests =
      new ConcurrentLinkedQueue<>();

  @Override
  public SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
      final UInt256 nodeId,
      final Supplier<UInt64> finalizedEpochSupplier,
      final DataColumnSlotAndIdentifier columnIdentifier) {
    final RequestEntry entry =
        new RequestEntry(nodeId, finalizedEpochSupplier, columnIdentifier, new SafeFuture<>());
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

  private record ByRootRequest(Bytes32 root, Set<UInt64> columns) {}

  private void flushForNode(final UInt256 nodeId, final List<RequestEntry> nodeRequests) {
    LOG.info(
        "Flushing requests for node {}: {}",
        nodeId,
        nodeRequests.stream().map(RequestEntry::columnIdentifier).toList());
    if (nodeRequests.isEmpty()) {
      return;
    }

    final UInt64 firstNonFinalizedSlot =
        spec.computeStartSlotAtEpoch(nodeRequests.getFirst().finalizedEpochSupplier().get())
            .increment();
    final List<ByRangeRequest> byRangeRequests =
        generateByRangeRequests(nodeRequests, firstNonFinalizedSlot);
    final List<RequestEntry> remainingNodeRequests = filterRequests(nodeRequests, byRangeRequests);
    final List<ByRootRequest> byRootRequests = generateByRootRequests(remainingNodeRequests);
    LOG.trace(
        "Processing prepared requests for node {}: byRoot({}), byRange({})",
        nodeId,
        byRootRequests,
        byRangeRequests);

    final List<DataColumnsByRootIdentifier> byRootIdentifiers =
        byRootRequests.stream()
            .map(
                byRootRequest ->
                    byRootSchema.create(
                        byRootRequest.root(), byRootRequest.columns().stream().toList()))
            .toList();
    final List<List<DataColumnsByRootIdentifier>> byRootBatches =
        Lists.partition(byRootIdentifiers, MAX_BATCH_SIZE);

    final AsyncStream<DataColumnSidecar> byRootStream =
        AsyncStream.createUnsafe(byRootBatches.iterator())
            .flatMap(byRootBatch -> byRootRpc.requestDataColumnSidecarsByRoot(nodeId, byRootBatch));
    final AsyncStream<DataColumnSidecar> byRangeStream =
        AsyncStream.createUnsafe(byRangeRequests.iterator())
            .flatMap(
                byRangeRequest ->
                    byRangeRpc.requestDataColumnSidecarsByRange(
                        nodeId,
                        byRangeRequest.start(),
                        byRangeRequest.end().increment().minus(byRangeRequest.start()).intValue(),
                        new ArrayList<>(byRangeRequest.columns())));

    byRootStream.merge(byRangeStream).consume(createResponseHandler(nodeRequests));
  }

  private List<RequestEntry> filterRequests(
      final List<RequestEntry> nodeRequests, final List<ByRangeRequest> byRangeRequests) {
    final List<RequestEntry> remainingNodeRequests = new ArrayList<>();
    final Set<SlotAndColumnIndex> requested = new HashSet<>();
    byRangeRequests.forEach(
        request -> {
          for (UInt64 slot = request.start();
              slot.isLessThanOrEqualTo(request.end());
              slot = slot.increment()) {
            final UInt64 currentSlot = slot;
            request
                .columns()
                .forEach(columnId -> requested.add(new SlotAndColumnIndex(currentSlot, columnId)));
          }
        });
    nodeRequests.stream()
        .filter(
            request ->
                !requested.contains(
                    new SlotAndColumnIndex(
                        request.columnIdentifier().slot(),
                        request.columnIdentifier().columnIndex())))
        .forEach(remainingNodeRequests::add);

    return remainingNodeRequests;
  }

  record SlotAndColumnIndex(UInt64 slot, UInt64 columnIndex) {}

  private List<ByRangeRequest> generateByRangeRequests(
      final List<RequestEntry> nodeRequests, final UInt64 firstNonFinalizedSlot) {
    final NavigableMap<UInt64, Set<UInt64>> bySlotMap = new TreeMap<>();
    nodeRequests.forEach(
        nodeRequest -> {
          final UInt64 slot = nodeRequest.columnIdentifier().slot();
          if (slot.isGreaterThanOrEqualTo(firstNonFinalizedSlot)) {
            return;
          }
          final UInt64 column = nodeRequest.columnIdentifier.columnIndex();
          bySlotMap.computeIfAbsent(slot, k -> new HashSet<>()).add(column);
        });

    if (bySlotMap.isEmpty()) {
      return Collections.emptyList();
    }

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
    return Collections.unmodifiableList(byRangeRequests);
  }

  private List<ByRootRequest> generateByRootRequests(final List<RequestEntry> nodeRequests) {
    final NavigableMap<SlotAndBlockRoot, Set<UInt64>> bySlotAndBlockRootMap = new TreeMap<>();
    nodeRequests.forEach(
        nodeRequest -> {
          final UInt64 column = nodeRequest.columnIdentifier.columnIndex();
          final SlotAndBlockRoot key = nodeRequest.columnIdentifier().getSlotAndBlockRoot();
          bySlotAndBlockRootMap.computeIfAbsent(key, k -> new HashSet<>()).add(column);
        });

    if (bySlotAndBlockRootMap.isEmpty()) {
      return Collections.emptyList();
    }

    final List<ByRootRequest> byRootRequests = new ArrayList<>();
    while (!bySlotAndBlockRootMap.isEmpty()) {
      final Map.Entry<SlotAndBlockRoot, Set<UInt64>> current =
          bySlotAndBlockRootMap.pollFirstEntry();
      byRootRequests.add(new ByRootRequest(current.getKey().getBlockRoot(), current.getValue()));
    }

    return Collections.unmodifiableList(byRootRequests);
  }

  private AsyncStreamHandler<DataColumnSidecar> createResponseHandler(
      final List<RequestEntry> nodeRequests) {
    final Map<DataColumnSlotAndIdentifier, RequestEntry> requestsByColumnId =
        nodeRequests.stream().collect(Collectors.toMap(RequestEntry::columnIdentifier, req -> req));
    return new AsyncStreamHandler<>() {

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
          return TRUE_FUTURE;
        }
      }

      @Override
      public void onComplete() {
        nodeRequests.stream()
            .filter(req -> !req.promise().isDone())
            .forEach(
                req -> req.promise().completeExceptionally(new DasColumnNotAvailableException()));
      }

      @Override
      public void onError(final Throwable err) {
        nodeRequests.forEach(e -> e.promise().completeExceptionally(err));
      }
    };
  }

  @Override
  public int getCurrentRequestLimit(final UInt256 nodeId) {
    return byRangeRpc.getCurrentRequestLimit(nodeId);
  }
}
