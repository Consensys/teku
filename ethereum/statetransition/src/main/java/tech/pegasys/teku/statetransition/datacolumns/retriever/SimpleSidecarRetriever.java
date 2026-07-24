/*
 * Copyright Consensys Software Inc., 2026
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

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
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
import tech.pegasys.teku.infrastructure.time.TimeProvider;
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

  /** Cadence at which {@link #nextRound()} runs to match pending requests to peers. */
  public static final Duration DEFAULT_ROUND_PERIOD = Duration.ofSeconds(1);

  /**
   * Default time a request must have been continuously in-flight before it becomes eligible for a
   * hedged second attempt. Chosen to be shorter than the RPC response-chunk timeout so a slow peer
   * is hedged well before its request would otherwise time out, but long enough that healthy peers
   * (which respond in well under a second) are never hedged.
   */
  public static final Duration DEFAULT_HEDGE_DELAY = Duration.ofSeconds(3);

  private final Spec spec;
  private final MiscHelpersFulu miscHelpersFulu;
  private final DasPeerCustodyCountSupplier custodyCountSupplier;
  private final DataColumnReqResp reqResp;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final Duration roundPeriod;
  private final int maxRequestCount;

  /**
   * Fraction of the currently pending requests that may have a redundant ("hedged") second
   * in-flight attempt at any time. Hedging lets a column stuck on a slow/unresponsive peer be
   * re-dispatched to an alternate custody peer without waiting for the original RPC to time out.
   * {@code 0} disables hedging entirely.
   */
  private final double overlapFraction;

  /**
   * How long a request must have been continuously in-flight before it becomes eligible for a
   * hedged second attempt. This targets genuinely slow peers rather than blindly duplicating every
   * request. Measured against wall-clock time (not round counts): {@link #nextRound()} can be
   * triggered off its fixed cadence by {@link #flush()}, so round counts are not a reliable measure
   * of elapsed time.
   */
  private final Duration hedgeDelay;

  private final Map<DataColumnSlotAndIdentifier, RetrieveRequest> pendingRequests =
      new ConcurrentHashMap<>();
  private final Map<UInt256, ConnectedPeer> connectedPeers = new ConcurrentHashMap<>();
  private final AtomicBoolean started = new AtomicBoolean(false);
  // Ensures only one nextRound() body runs at a time (see nextRound()).
  private final AtomicBoolean roundInProgress = new AtomicBoolean(false);
  private final AtomicLong retrieveCounter = new AtomicLong();
  private final AtomicLong errorCounter = new AtomicLong();
  private final AtomicLong hedgeCounter = new AtomicLong();
  private final DataColumnPeerManager peerManager;

  public SimpleSidecarRetriever(
      final Spec spec,
      final DataColumnPeerManager peerManager,
      final DasPeerCustodyCountSupplier custodyCountSupplier,
      final DataColumnReqResp reqResp,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final double overlapFraction) {
    this(
        spec,
        peerManager,
        custodyCountSupplier,
        reqResp,
        asyncRunner,
        timeProvider,
        DEFAULT_ROUND_PERIOD,
        overlapFraction,
        DEFAULT_HEDGE_DELAY);
  }

  public SimpleSidecarRetriever(
      final Spec spec,
      final DataColumnPeerManager peerManager,
      final DasPeerCustodyCountSupplier custodyCountSupplier,
      final DataColumnReqResp reqResp,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final Duration roundPeriod,
      final double overlapFraction,
      final Duration hedgeDelay) {
    this.spec = spec;
    this.miscHelpersFulu =
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
    this.custodyCountSupplier = custodyCountSupplier;
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
    this.roundPeriod = roundPeriod;
    this.reqResp = reqResp;
    this.peerManager = peerManager;
    this.peerManager.addPeerListener(this);
    this.maxRequestCount =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
            .getMaxRequestDataColumnSidecars();
    this.overlapFraction = overlapFraction;
    this.hedgeDelay = hedgeDelay;
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
        .ifPresent(request -> reqRespSucceeded(request, sidecar));
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  /**
   * Produces the peer/request pairs to activate this round: first the primary matches (requests
   * with no in-flight attempt), then any hedged matches for slow in-flight requests, bounded by
   * {@link #overlapFraction}.
   */
  private List<RequestMatch> matchRequestsAndPeers() {
    final RequestTracker ongoingRequestsTracker = createFromCurrentPendingRequests();
    final List<RequestMatch> matches = new ArrayList<>();

    // Primary: requests that currently have no in-flight attempt.
    pendingRequests.entrySet().stream()
        .filter(entry -> entry.getValue().attemptingPeers.isEmpty())
        .sorted(Comparator.comparing(entry -> entry.getKey().slot()))
        .forEach(
            entry ->
                findBestMatchingPeer(entry.getValue(), ongoingRequestsTracker, Set.of())
                    .ifPresent(
                        peer -> {
                          ongoingRequestsTracker.decreaseAvailableRequests(peer.nodeId);
                          matches.add(new RequestMatch(peer, entry.getValue()));
                        }));

    addHedgeMatches(ongoingRequestsTracker, matches);
    return matches;
  }

  /**
   * Adds redundant attempts for requests that have been in-flight for too long, re-dispatching them
   * to an alternate custody peer. Bounded by the overlap budget so at most {@code overlapFraction}
   * of the pending requests carry a duplicate attempt at once.
   *
   * <p>A request carries at most two concurrent attempts; escalation past that is implicit, driven
   * by the RPC first-chunk timeout ({@code
   * Eth2OutgoingRequestHandler.RESPONSE_CHUNK_ARRIVAL_TIMEOUT}, ~10s) freeing a slot to re-hedge a
   * still-stuck request to a fresh peer. Example (hedgeDelay=3s, RPC timeout=10s):
   *
   * <ul>
   *   <li>t=0 — primary attempt → peer A. attemptingPeers={A}.
   *   <li>t=3s (hedgeDelay) — size==1 → hedge → peer B. {A,B}.
   *   <li>t=6s — size==2, not eligible; both attempts still outstanding (nothing new).
   *   <li>t≈10s (RPC timeout) — A times out → removed, {B}; firstInFlightAt not reset (B still in
   *       flight), so immediately hedge-eligible again → hedge to fresh peer C. {B,C}.
   *   <li>t=13s — B times out → hedge to D. {C,D} … rotating a dead peer out ~every RPC timeout
   *       until the column arrives or all attempts drain (then primary re-dispatches).
   * </ul>
   */
  private void addHedgeMatches(
      final RequestTracker ongoingRequestsTracker, final List<RequestMatch> matches) {
    if (overlapFraction <= 0.0) {
      return;
    }
    // The hedge budget is a fraction of the number of pending column retrievals, not of the peer
    // request limits (maxRequestCount / getCurrentRequestLimit). Those limits still cap what any
    // single peer will accept and are enforced in findMatchingPeers; here we instead want the
    // redundancy to scale with the amount of outstanding work, matching how the rest of nextRound
    // reasons over the whole pending set. This keeps overlap proportional and self-scaling (5% of
    // in-flight work) rather than coupling it to per-peer rate limiting, which is a separate
    // concern.
    final int maxOverlap = (int) Math.floor(overlapFraction * pendingRequests.size());
    if (maxOverlap <= 0) {
      return;
    }
    // A request is hedged at most once: it only becomes eligible below while it has exactly one
    // attempt (size() == 1), and a hedge takes it to two. So a request never has more than two
    // concurrent attempts, and counting requests with size() > 1 counts each hedged request exactly
    // once against the budget (there are no extra attempts to miss).
    final long currentOverlap =
        pendingRequests.values().stream().filter(r -> r.attemptingPeers.size() > 1).count();
    int budget = maxOverlap - (int) currentOverlap;
    if (budget <= 0) {
      return;
    }

    final UInt64 nowMillis = timeProvider.getTimeInMillis();
    // Best-effort selection: iterate lazily and stop as soon as the budget is spent, so we don't
    // walk the whole pending set when many requests are eligible. We intentionally don't sort by
    // slot here - sorting is a stateful barrier that would force a full walk every round, and it is
    // unnecessary: the primary loop above is slot-ordered, so lower-slot requests are dispatched
    // (and reach hedge-eligibility) first anyway.
    final Iterator<RetrieveRequest> eligible =
        pendingRequests.values().stream()
            .filter(request -> request.attemptingPeers.size() == 1)
            .filter(request -> request.hasBeenInFlightFor(nowMillis, hedgeDelay))
            .iterator();

    while (budget > 0 && eligible.hasNext()) {
      final RetrieveRequest request = eligible.next();
      final Optional<ConnectedPeer> alternatePeer =
          findBestMatchingPeer(request, ongoingRequestsTracker, request.attemptingPeers);
      if (alternatePeer.isPresent()) {
        ongoingRequestsTracker.decreaseAvailableRequests(alternatePeer.get().nodeId);
        matches.add(new RequestMatch(alternatePeer.get(), request));
        hedgeCounter.incrementAndGet();
        budget--;
      }
    }
  }

  private boolean activateMatchedRequest(final RequestMatch match) {
    if (!match.request.attemptingPeers.add(match.peer.nodeId)) {
      // already attempting this column via this peer
      return false;
    }
    // Record when this request first went in-flight (used to decide hedging eligibility). No-op for
    // a hedged second attempt, which keeps the original start time.
    match.request.markInFlightStart(timeProvider.getTimeInMillis());

    final SafeFuture<DataColumnSidecar> reqRespPromise =
        reqResp.requestDataColumnSidecar(match.peer.nodeId, match.request.columnId);
    match.peer.countSidecarRequest();

    final SafeFuture<Void> activeRpcRequest =
        reqRespPromise.handle(
            (sidecar, err) -> {
              reqRespCompleted(match.request, match.peer, sidecar);
              if (err == null) {
                match.peer.countSidecarReceived();
              } else {
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

    // Safe to install after firing: reqResp buffers the request and only completes it on flush(),
    // which happens at the end of this round, so the handle callback above cannot run before this
    // assignment.
    match.request.activeRequests.put(
        match.peer.nodeId, new ActiveRequest(activeRpcRequest, match.peer));
    return true;
  }

  private Optional<ConnectedPeer> findBestMatchingPeer(
      final RetrieveRequest request,
      final RequestTracker ongoingRequestsTracker,
      final Set<UInt256> excludedPeers) {
    final Stream<ConnectedPeer> matchingPeers =
        findMatchingPeers(request, ongoingRequestsTracker)
            .filter(peer -> !excludedPeers.contains(peer.nodeId));

    // Preferring peers with the best response rate, then preferring less busy peers among equals
    final Comparator<ConnectedPeer> comparator =
        Comparator.comparing(ConnectedPeer::getResponseScore)
            .thenComparing(
                (ConnectedPeer peer) ->
                    ongoingRequestsTracker.getAvailableRequestCount(peer.nodeId));
    return matchingPeers.max(comparator);
  }

  private Stream<ConnectedPeer> findMatchingPeers(
      final RetrieveRequest request, final RequestTracker ongoingRequestsTracker) {
    return connectedPeers.values().stream()
        .filter(peer -> peer.isCustodyFor(request.columnId))
        .filter(peer -> peer.hasSlotAvailable(request.columnId.slot()))
        .filter(peer -> ongoingRequestsTracker.hasAvailableRequests(peer.nodeId));
  }

  private void disposeCompletedRequests() {
    pendingRequests
        .entrySet()
        .removeIf(
            pendingEntry -> {
              final RetrieveRequest pendingRequest = pendingEntry.getValue();
              if (pendingRequest.result.isDone()) {
                pendingRequest.cancelActiveRequests();
                return true;
              }
              return false;
            });
  }

  private void nextRound() {
    // Serialize round bodies. nextRound is invoked both on the fixed-delay schedule and from
    // flush(), and the DAS async runner is multi-threaded, so two rounds could otherwise run
    // concurrently. The matching logic (primary-vs-hedge selection, the overlap budget, the shared
    // RequestTracker) all assume a single round observes and mutates the pending set atomically;
    // overlapping rounds could dispatch multiple "primary" attempts for the same column (exceeding
    // the two-attempt cap and bypassing hedgeDelay) and double-spend the overlap budget. A skipped
    // round is harmless: the in-progress round already reflects the current state, and the next
    // scheduled round runs within roundPeriod.
    if (!roundInProgress.compareAndSet(false, true)) {
      return;
    }
    try {
      disposeCompletedRequests();

      final long activatedMatches =
          matchRequestsAndPeers().stream()
              .map(this::activateMatchedRequest)
              .filter(activated -> activated)
              .count();

      if (LOG.isTraceEnabled()) {
        final long activeRequestCount =
            pendingRequests.values().stream().filter(r -> !r.attemptingPeers.isEmpty()).count();
        LOG.trace(
            "SimpleSidecarRetriever.nextRound: completed: {}, errored: {}, hedged: {}, total pending: {}, active pending: {}, new active: {}, number of custody peers: {}",
            retrieveCounter,
            errorCounter,
            hedgeCounter,
            pendingRequests.size(),
            activeRequestCount,
            activatedMatches,
            gatherAvailableCustodiesInfo());
      }

      reqResp.flush();
    } finally {
      roundInProgress.set(false);
    }
  }

  private void reqRespSucceeded(final RetrieveRequest request, final DataColumnSidecar sidecar) {
    if (pendingRequests.remove(request.columnId) != null) {
      request.result.completeAsync(sidecar, asyncRunner);
      retrieveCounter.incrementAndGet();
      // cancel any redundant/hedged in-flight attempts for this column
      request.cancelActiveRequests();
    }
  }

  private void reqRespCompleted(
      final RetrieveRequest request,
      final ConnectedPeer peer,
      final DataColumnSidecar maybeResult) {
    if (maybeResult != null && pendingRequests.remove(request.columnId) != null) {
      request.result.completeAsync(maybeResult, asyncRunner);
      retrieveCounter.incrementAndGet();
      // cancel any redundant/hedged in-flight attempts for this column
      request.cancelActiveRequests();
      return;
    }
    // Either this attempt failed, or another (hedged) attempt already won the race. Release just
    // this peer's attempt so the request can be re-dispatched next round if still pending.
    request.attemptingPeers.remove(peer.nodeId);
    request.activeRequests.remove(peer.nodeId);
    if (request.attemptingPeers.isEmpty()) {
      // No attempts left: restart the in-flight clock so a fresh attempt is measured from scratch.
      request.clearInFlightStart();
    }
    if (maybeResult == null) {
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
  public void peerConnected(
      final UInt256 nodeId, final Supplier<Optional<UInt64>> maybeEarliestAvailableSlot) {
    LOG.trace(
        "SimpleSidecarRetriever.peerConnected: 0x...{}", () -> nodeId.toHexString().substring(58));
    connectedPeers.computeIfAbsent(
        nodeId,
        __ ->
            new ConnectedPeer(
                nodeId,
                maybeEarliestAvailableSlot,
                miscHelpersFulu,
                spec,
                () -> custodyCountSupplier.getCustodyGroupCountForPeer(nodeId)));
  }

  @Override
  public void peerDisconnected(final UInt256 nodeId) {
    LOG.trace(
        "SimpleSidecarRetriever.peerDisconnected: 0x...{}",
        () -> nodeId.toHexString().substring(58));
    connectedPeers.remove(nodeId);
  }

  @VisibleForTesting
  Map<UInt256, ConnectedPeer> getConnectedPeers() {
    return connectedPeers;
  }

  @VisibleForTesting
  long getHedgeCount() {
    return hedgeCounter.get();
  }

  private record ActiveRequest(SafeFuture<Void> promise, ConnectedPeer peer) {}

  private static class RetrieveRequest {
    private static final long NOT_IN_FLIGHT = -1L;

    final DataColumnSlotAndIdentifier columnId;
    final SafeFuture<DataColumnSidecar> result = new SafeFuture<>();
    // Peers with a reserved or in-flight attempt for this column. Also acts as the activation
    // guard.
    final Set<UInt256> attemptingPeers = ConcurrentHashMap.newKeySet();
    final Map<UInt256, ActiveRequest> activeRequests = new ConcurrentHashMap<>();
    // Wall-clock time (millis) at which this request first went in-flight, or NOT_IN_FLIGHT when it
    // currently has no attempts. Used to measure how long a request has been stuck.
    final AtomicLong firstInFlightAtMillis = new AtomicLong(NOT_IN_FLIGHT);

    private RetrieveRequest(final DataColumnSlotAndIdentifier columnId) {
      this.columnId = columnId;
    }

    void markInFlightStart(final UInt64 nowMillis) {
      firstInFlightAtMillis.compareAndSet(NOT_IN_FLIGHT, nowMillis.longValue());
    }

    void clearInFlightStart() {
      firstInFlightAtMillis.set(NOT_IN_FLIGHT);
    }

    boolean hasBeenInFlightFor(final UInt64 nowMillis, final Duration delay) {
      final long start = firstInFlightAtMillis.get();
      return start != NOT_IN_FLIGHT && nowMillis.longValue() - start >= delay.toMillis();
    }

    void cancelActiveRequests() {
      activeRequests.values().forEach(activeRequest -> activeRequest.promise().cancel(true));
      activeRequests.clear();
      attemptingPeers.clear();
      clearInFlightStart();
    }
  }

  static class ConnectedPeer {
    private final UInt256 nodeId;
    private final Supplier<Optional<UInt64>> maybeEarliestAvailableSlot;
    private final Cache<CacheKey, Set<UInt64>> custodyIndicesCache = LRUCache.create(2);
    private final MiscHelpersFulu miscHelpersFulu;
    private final Spec spec;
    private final Supplier<Integer> custodyCountSupplier;
    // just to avoid starting with non-divisible 0/0
    private final AtomicInteger sidecarsRequested = new AtomicInteger(1);
    private final AtomicInteger sidecarsReceived = new AtomicInteger(1);

    private record CacheKey(SpecVersion specVersion, int custodyCount) {}

    public ConnectedPeer(
        final UInt256 nodeId,
        final Supplier<Optional<UInt64>> maybeEarliestAvailableSlot,
        final MiscHelpersFulu miscHelpersFulu,
        final Spec spec,
        final Supplier<Integer> custodyCountSupplier) {
      this.nodeId = nodeId;
      this.maybeEarliestAvailableSlot = maybeEarliestAvailableSlot;
      this.miscHelpersFulu = miscHelpersFulu;
      this.spec = spec;
      this.custodyCountSupplier = custodyCountSupplier;
    }

    private Set<UInt64> calcNodeCustodyIndices(final CacheKey cacheKey) {
      return miscHelpersFulu.computeCustodyColumnIndices(nodeId, cacheKey.custodyCount());
    }

    private Set<UInt64> getNodeCustodyIndices(final SpecVersion specVersion) {
      return custodyIndicesCache.get(
          new CacheKey(specVersion, custodyCountSupplier.get()), this::calcNodeCustodyIndices);
    }

    public boolean isCustodyFor(final DataColumnSlotAndIdentifier columnId) {
      return getNodeCustodyIndices(spec.atSlot(columnId.slot())).contains(columnId.columnIndex());
    }

    public boolean hasSlotAvailable(final UInt64 slot) {
      // if we don't have information, we consider it optimistically
      return maybeEarliestAvailableSlot.get().map(slot::isGreaterThanOrEqualTo).orElse(true);
    }

    /**
     * Score is 0 to 10, where 10 is peer which always response well on all queries. Score is
     * bucketed to 11 values, so another comparator could use different criteria to score several
     * peers from one bucket.
     */
    public int getResponseScore() {
      return (int) (sidecarsReceived.get() * 10.0F / sidecarsRequested.get());
    }

    public void countSidecarRequest() {
      final int current = sidecarsRequested.incrementAndGet();
      if (current == Integer.MAX_VALUE) {
        resetCounters();
      }
    }

    private void resetCounters() {
      sidecarsRequested.set(1);
      sidecarsReceived.set(1);
    }

    public void countSidecarReceived() {
      final int current = sidecarsReceived.incrementAndGet();
      if (current == Integer.MAX_VALUE) {
        resetCounters();
      }
    }

    @VisibleForTesting
    AtomicInteger getSidecarsRequested() {
      return sidecarsRequested;
    }

    @VisibleForTesting
    AtomicInteger getSidecarsReceived() {
      return sidecarsReceived;
    }
  }

  private record RequestMatch(ConnectedPeer peer, RetrieveRequest request) {}

  private RequestTracker createFromCurrentPendingRequests() {
    final Map<UInt256, Integer> pendingRequestsCount = new HashMap<>();
    pendingRequests
        .values()
        .forEach(
            request ->
                request
                    .activeRequests
                    .values()
                    .forEach(
                        activeRequest ->
                            pendingRequestsCount.merge(
                                activeRequest.peer().nodeId, 1, Integer::sum)));
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
