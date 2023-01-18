/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beacon.sync.gossip;

import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.gossip.FetchBlockResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class FetchBlockTask {
  private static final Logger LOG = LogManager.getLogger();
  private static final Comparator<Eth2Peer> SHUFFLING_COMPARATOR =
      Comparator.comparing(p -> Math.random());

  private final P2PNetwork<Eth2Peer> eth2Network;
  private final Bytes32 blockRoot;
  private final Set<NodeId> queriedPeers = Collections.newSetFromMap(new ConcurrentHashMap<>());

  private final AtomicInteger numberOfRuns = new AtomicInteger(0);
  private final AtomicBoolean cancelled = new AtomicBoolean(false);

  public FetchBlockTask(final P2PNetwork<Eth2Peer> eth2Network, final Bytes32 blockRoot) {
    this.eth2Network = eth2Network;
    this.blockRoot = blockRoot;
  }

  public void cancel() {
    cancelled.set(true);
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public int getNumberOfRetries() {
    return Math.max(0, numberOfRuns.get() - 1);
  }

  public SafeFuture<FetchBlockResult> run() {
    if (cancelled.get()) {
      return SafeFuture.completedFuture(FetchBlockResult.createFailed(Status.CANCELLED));
    }

    final Optional<Eth2Peer> maybePeer =
        eth2Network
            .streamPeers()
            .filter(p -> !queriedPeers.contains(p.getId()))
            .min(
                Comparator.comparing(Eth2Peer::getOutstandingRequests)
                    .thenComparing(SHUFFLING_COMPARATOR));

    if (maybePeer.isEmpty()) {
      return SafeFuture.completedFuture(FetchBlockResult.createFailed(Status.NO_AVAILABLE_PEERS));
    }
    final Eth2Peer peer = maybePeer.get();

    numberOfRuns.incrementAndGet();
    queriedPeers.add(peer.getId());

    return fetchBlock(peer, blockRoot)
        .exceptionally(
            err -> {
              LOG.debug("Failed to fetch block " + blockRoot, err);
              return FetchBlockResult.createFailed(Status.FETCH_FAILED);
            });
  }

  protected SafeFuture<FetchBlockResult> fetchBlock(final Eth2Peer peer, final Bytes32 blockRoot) {
    return peer.requestBlockByRoot(blockRoot)
        .thenApply(
            maybeBlock ->
                maybeBlock
                    .map(FetchBlockResult::createSuccessful)
                    .orElseGet(() -> FetchBlockResult.createFailed(Status.FETCH_FAILED)));
  }
}
