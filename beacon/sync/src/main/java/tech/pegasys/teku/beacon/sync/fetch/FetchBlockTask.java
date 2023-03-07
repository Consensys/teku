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

package tech.pegasys.teku.beacon.sync.fetch;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlockResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;

public class FetchBlockTask extends AbstractFetchTask {
  private static final Logger LOG = LogManager.getLogger();

  protected final Bytes32 blockRoot;

  private final AtomicInteger numberOfRuns = new AtomicInteger(0);

  public FetchBlockTask(final P2PNetwork<Eth2Peer> eth2Network, final Bytes32 blockRoot) {
    super(eth2Network);
    this.blockRoot = blockRoot;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public int getNumberOfRetries() {
    return Math.max(0, numberOfRuns.get() - 1);
  }

  /**
   * Selects random {@link Eth2Peer} from the network and fetches a block by root. It also tracks
   * the number of runs and the already queried peers.
   */
  public SafeFuture<FetchBlockResult> run() {
    if (cancelled.get()) {
      return SafeFuture.completedFuture(FetchBlockResult.createFailed(Status.CANCELLED));
    }

    final Optional<Eth2Peer> maybePeer = findRandomPeer();

    if (maybePeer.isEmpty()) {
      return SafeFuture.completedFuture(FetchBlockResult.createFailed(Status.NO_AVAILABLE_PEERS));
    }
    final Eth2Peer peer = maybePeer.get();

    numberOfRuns.incrementAndGet();
    trackQueriedPeer(peer);

    return fetchBlock(peer);
  }

  private SafeFuture<FetchBlockResult> fetchBlock(final Eth2Peer peer) {
    return peer.requestBlockByRoot(blockRoot)
        .thenApply(
            maybeBlock ->
                maybeBlock
                    .map(FetchBlockResult::createSuccessful)
                    .orElseGet(() -> FetchBlockResult.createFailed(Status.FETCH_FAILED)))
        .exceptionally(
            err -> {
              LOG.debug("Failed to fetch block by root " + blockRoot, err);
              return FetchBlockResult.createFailed(Status.FETCH_FAILED);
            });
  }
}
