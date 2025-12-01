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

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public class SimpleSidecarRetriever
    implements DataColumnSidecarRetriever, DataColumnPeerManager.PeerListener {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final MiscHelpersFulu miscHelpersFulu;
  private final DasPeerCustodyCountSupplier custodyCountSupplier;
  private final DataColumnReqResp reqResp;
  private final AsyncRunner asyncRunner;
  private final Duration roundPeriod;
  private final int maxRequestCount;

  private final Map<DataColumnSlotAndIdentifier, RetrieveRequest> pendingRequests =
      new ConcurrentHashMap<>();
  private final Map<UInt256, ConnectedPeer> connectedPeers = new ConcurrentHashMap<>();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicLong retrieveCounter = new AtomicLong();
  private final AtomicLong errorCounter = new AtomicLong();

  public SimpleSidecarRetriever(
      final Spec spec,
      final DataColumnPeerManager peerManager,
      final DasPeerCustodyCountSupplier custodyCountSupplier,
      final DataColumnReqResp reqResp,
      final AsyncRunner asyncRunner,
      final Duration roundPeriod) {
    this.spec = spec;
    this.miscHelpersFulu =
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
    this.custodyCountSupplier = custodyCountSupplier;
    this.asyncRunner = asyncRunner;
    this.roundPeriod = roundPeriod;
    this.reqResp = reqResp;
    peerManager.addPeerListener(this);
    this.maxRequestCount =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
            .getMaxRequestDataColumnSidecars();
  }

  private void startIfNecessary() {
    if (started.compareAndSet(false, true)) {
      asyncRunner.runWithFixedDelay(
          this::nextRound, roundPeriod, err -> LOG.debug("Unexpected error", err));
    }
  }

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier columnId) {
    final RetrieveRequest request =
        pendingRequests.computeIfAbsent(columnId, __ -> new RetrieveRequest(columnId));
    startIfNecessary();
    return request.result;
  }

  @Override
  public void flush() {
    asyncRunner.runAsync(this::nextRound).finishStackTrace();
  }

  @Override
  public void onNewValidatedSidecar(
      final DataColumnSidecar sidecar, final RemoteOrigin remoteOrigin) {
    final DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar);

    Optional.ofNullable(pendingRequests.get(dataColumnSlotAndIdentifier))
        .filter(request -> !request.result.isDone())
        .ifPresent(request -> reqRespCompleted(request, sidecar));
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  private Stream<RequestMatch> matchRequestsAndPeers() {
    final RequestTracker ongoingRequestsTracker = createFromCurrentPendingRequests();
    return pendingRequests.entrySet().stream()
        .filter(entry -> entry.getValue().activeRpcRequest == null)
        .sorted(Comparator.comparing(entry -> entry.getKey().slot()))
        .flatMap(
            entry -> {
              final RetrieveRequest request = entry.getValue();
              return findBestMatchingPeer(request, ongoingRequestsTracker).stream()
                  .peek(peer -> ongoingRequestsTracker.decreaseAvailableRequests(peer.nodeId))
                  .map(peer -> new RequestMatch(peer, request));
            });
  }

  private boolean activateMatchedRequest(final RequestMatch match) {
    if (!match.request.activeRpcRequestSet.compareAndSet(false, true)) {
      // already activated
      return false;
    }

    final SafeFuture<DataColumnSidecar> reqRespPromise =
        reqResp.requestDataColumnSidecar(match.peer.nodeId, match.request.columnId);
    match.request.onPeerRequest(match.peer().nodeId);

    final SafeFuture<Void> activeRpcRequest =
        reqRespPromise.handle(
            (sidecar, err) -> {
              reqRespCompleted(match.request, sidecar);

              if (err != null) {
                LOG.debug(
                    "SimpleSidecarRetriever.Request failed for {} due to: {}",
                    () -> match.request.columnId,
                    () -> ExceptionUtil.getMessageOrSimpleName(err));
              }

              return null;
            });

    // here we make sure that if something goes wrong in the handle call we
    // log all the info to fix the bug
    activeRpcRequest.ignoreCancelException().finishStackTrace();

    match.request.activeRpcRequest = new ActiveRequest(activeRpcRequest, match.peer);
    return true;
  }

  private Optional<ConnectedPeer> findBestMatchingPeer(
      final RetrieveRequest request, final RequestTracker ongoingRequestsTracker) {
    final Stream<ConnectedPeer> matchingPeers = findMatchingPeers(request, ongoingRequestsTracker);

    // taking first the peers which were not requested yet, then peers which are less busy
    final Comparator<ConnectedPeer> comparator =
        Comparator.comparing((ConnectedPeer peer) -> request.getPeerRequestCount(peer.nodeId))
            .reversed()
            .thenComparing(
                (ConnectedPeer peer) ->
                    ongoingRequestsTracker.getAvailableRequestCount(peer.nodeId));
    return matchingPeers.max(comparator);
  }

  private Stream<ConnectedPeer> findMatchingPeers(
      final RetrieveRequest request, final RequestTracker ongoingRequestsTracker) {
    return connectedPeers.values().stream()
        .filter(peer -> peer.isCustodyFor(request.columnId))
        .filter(peer -> ongoingRequestsTracker.hasAvailableRequests(peer.nodeId));
  }

  private void disposeCompletedRequests() {
    pendingRequests
        .entrySet()
        .removeIf(
            pendingEntry -> {
              final RetrieveRequest pendingRequest = pendingEntry.getValue();
              if (pendingRequest.result.isDone()) {
                if (pendingRequest.activeRpcRequest != null) {
                  pendingRequest.activeRpcRequest.promise().cancel(true);
                }
                return true;
              }
              return false;
            });
  }

  private void nextRound() {
    disposeCompletedRequests();

    final long activatedMatches =
        matchRequestsAndPeers()
            .map(this::activateMatchedRequest)
            .filter(activated -> activated)
            .count();

    if (LOG.isTraceEnabled()) {
      final long activeRequestCount =
          pendingRequests.values().stream().filter(r -> r.activeRpcRequest != null).count();
      LOG.trace(
          "SimpleSidecarRetriever.nextRound: completed: {}, errored: {},  total pending: {}, active pending: {}, new active: {}, number of custody peers: {}",
          retrieveCounter,
          errorCounter,
          pendingRequests.size(),
          activeRequestCount,
          activatedMatches,
          gatherAvailableCustodiesInfo());
    }

    reqResp.flush();
  }

  private void reqRespCompleted(
      final RetrieveRequest request, final DataColumnSidecar maybeResult) {
    if (maybeResult != null && pendingRequests.remove(request.columnId) != null) {
      request.result.completeAsync(maybeResult, asyncRunner);
      retrieveCounter.incrementAndGet();
    } else if (request.activeRpcRequestSet.compareAndSet(true, false)) {
      request.activeRpcRequest = null;
      errorCounter.incrementAndGet();
    }
  }

  private String gatherAvailableCustodiesInfo() {
    final SpecVersion specVersion = spec.forMilestone(SpecMilestone.FULU);
    final Map<UInt64, Long> colIndexToCount =
        connectedPeers.values().stream()
            .flatMap(p -> p.getNodeCustodyIndices(specVersion).stream())
            .collect(Collectors.groupingBy(i -> i, Collectors.counting()));
    final int numberOfColumns =
        SpecConfigFulu.required(specVersion.getConfig()).getNumberOfColumns();
    IntStream.range(0, numberOfColumns)
        .mapToObj(UInt64::valueOf)
        .forEach(idx -> colIndexToCount.putIfAbsent(idx, 0L));
    colIndexToCount.replaceAll((colIdx, count) -> Long.min(3, count));
    final Map<Long, Long> custodyCountToPeerCount =
        colIndexToCount.entrySet().stream()
            .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.counting()));
    return new TreeMap<>(custodyCountToPeerCount)
        .entrySet().stream()
            .map(
                entry -> {
                  String peerCnt = entry.getKey() == 3 ? "3+" : "" + entry.getKey();
                  return entry.getValue() + " cols: " + peerCnt + " peers";
                })
            .collect(Collectors.joining(","));
  }

  @Override
  public void peerConnected(final UInt256 nodeId) {
    LOG.trace(
        "SimpleSidecarRetriever.peerConnected: 0x...{}", () -> nodeId.toHexString().substring(58));
    connectedPeers.computeIfAbsent(nodeId, __ -> new ConnectedPeer(nodeId));
  }

  @Override
  public void peerDisconnected(final UInt256 nodeId) {
    LOG.trace(
        "SimpleSidecarRetriever.peerDisconnected: 0x...{}",
        () -> nodeId.toHexString().substring(58));
    connectedPeers.remove(nodeId);
  }

  private record ActiveRequest(SafeFuture<Void> promise, ConnectedPeer peer) {}

  private static class RetrieveRequest {
    final DataColumnSlotAndIdentifier columnId;
    final SafeFuture<DataColumnSidecar> result = new SafeFuture<>();
    final Map<UInt256, Integer> peerRequestCount = new HashMap<>();
    final AtomicBoolean activeRpcRequestSet = new AtomicBoolean(false);
    volatile ActiveRequest activeRpcRequest = null;

    private RetrieveRequest(final DataColumnSlotAndIdentifier columnId) {
      this.columnId = columnId;
    }

    public void onPeerRequest(final UInt256 peerId) {
      peerRequestCount.compute(peerId, (__, curCount) -> curCount == null ? 1 : curCount + 1);
    }

    public int getPeerRequestCount(final UInt256 peerId) {
      return peerRequestCount.getOrDefault(peerId, 0);
    }
  }

  private class ConnectedPeer {
    final UInt256 nodeId;
    final Cache<CacheKey, Set<UInt64>> custodyIndicesCache = LRUCache.create(2);

    private record CacheKey(SpecVersion specVersion, int custodyCount) {}

    public ConnectedPeer(final UInt256 nodeId) {
      this.nodeId = nodeId;
    }

    private Set<UInt64> calcNodeCustodyIndices(final CacheKey cacheKey) {
      return new HashSet<>(
          miscHelpersFulu.computeCustodyColumnIndices(nodeId, cacheKey.custodyCount()));
    }

    private Set<UInt64> getNodeCustodyIndices(final SpecVersion specVersion) {
      return custodyIndicesCache.get(
          new CacheKey(specVersion, custodyCountSupplier.getCustodyGroupCountForPeer(nodeId)),
          this::calcNodeCustodyIndices);
    }

    public boolean isCustodyFor(final DataColumnSlotAndIdentifier columnId) {
      return getNodeCustodyIndices(spec.atSlot(columnId.slot())).contains(columnId.columnIndex());
    }
  }

  private record RequestMatch(ConnectedPeer peer, RetrieveRequest request) {}

  private RequestTracker createFromCurrentPendingRequests() {
    final Map<UInt256, Integer> pendingRequestsCount =
        pendingRequests.values().stream()
            .map(r -> r.activeRpcRequest)
            .filter(Objects::nonNull)
            .map(r -> r.peer().nodeId)
            .collect(Collectors.groupingBy(r -> r, Collectors.reducing(0, e -> 1, Integer::sum)));
    return new RequestTracker(pendingRequestsCount);
  }

  private class RequestTracker {
    private final Map<UInt256, Integer> pendingRequestsCount;

    private RequestTracker(final Map<UInt256, Integer> pendingRequestsCount) {
      this.pendingRequestsCount = pendingRequestsCount;
    }

    int getAvailableRequestCount(final UInt256 nodeId) {
      return Integer.min(maxRequestCount, reqResp.getCurrentRequestLimit(nodeId))
          - pendingRequestsCount.getOrDefault(nodeId, 0);
    }

    boolean hasAvailableRequests(final UInt256 nodeId) {
      return getAvailableRequestCount(nodeId) > 0;
    }

    void decreaseAvailableRequests(final UInt256 nodeId) {
      pendingRequestsCount.compute(nodeId, (__, cnt) -> cnt == null ? 1 : cnt + 1);
    }
  }
}
