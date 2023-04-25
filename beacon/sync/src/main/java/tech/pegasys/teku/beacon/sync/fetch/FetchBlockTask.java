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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class FetchBlockTask extends AbstractFetchTask<Bytes32, SignedBeaconBlock> {

  private static final Logger LOG = LogManager.getLogger();

  protected final Bytes32 blockRoot;

  public FetchBlockTask(final P2PNetwork<Eth2Peer> eth2Network, final Bytes32 blockRoot) {
    super(eth2Network, Optional.empty());
    this.blockRoot = blockRoot;
  }

  public FetchBlockTask(
      final P2PNetwork<Eth2Peer> eth2Network,
      final Optional<Eth2Peer> preferredPeer,
      final Bytes32 blockRoot) {
    super(eth2Network, preferredPeer);
    this.blockRoot = blockRoot;
  }

  @Override
  public Bytes32 getKey() {
    return blockRoot;
  }

  @Override
  SafeFuture<FetchResult<SignedBeaconBlock>> fetch(final Eth2Peer peer) {
    return peer.requestBlockByRoot(blockRoot)
        .thenApply(
            maybeBlock ->
                maybeBlock
                    .map(block -> FetchResult.createSuccessful(peer, block))
                    .orElseGet(() -> FetchResult.createFailed(peer, Status.FETCH_FAILED)))
        .exceptionally(
            err -> {
              LOG.debug(
                  String.format(
                      "Failed to fetch block by root %s from peer %s", blockRoot, peer.getId()),
                  err);
              return FetchResult.createFailed(peer, Status.FETCH_FAILED);
            });
  }
}
