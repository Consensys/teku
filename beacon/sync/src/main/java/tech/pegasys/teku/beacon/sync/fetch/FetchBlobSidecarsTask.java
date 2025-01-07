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

package tech.pegasys.teku.beacon.sync.fetch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class FetchBlobSidecarsTask
    extends AbstractFetchTask<BlockRootAndBlobIdentifiers, List<BlobSidecar>> {

  private static final Logger LOG = LogManager.getLogger();

  private final BlockRootAndBlobIdentifiers blockRootAndBlobIdentifiers;

  FetchBlobSidecarsTask(
      final P2PNetwork<Eth2Peer> eth2Network,
      final BlockRootAndBlobIdentifiers blockRootAndBlobIdentifiers) {
    super(eth2Network, Optional.empty());
    this.blockRootAndBlobIdentifiers = blockRootAndBlobIdentifiers;
  }

  public FetchBlobSidecarsTask(
      final P2PNetwork<Eth2Peer> eth2Network,
      final Optional<Eth2Peer> preferredPeer,
      final BlockRootAndBlobIdentifiers blockRootAndBlobIdentifiers) {
    super(eth2Network, preferredPeer);
    this.blockRootAndBlobIdentifiers = blockRootAndBlobIdentifiers;
  }

  @Override
  public BlockRootAndBlobIdentifiers getKey() {
    return blockRootAndBlobIdentifiers;
  }

  @Override
  SafeFuture<FetchResult<List<BlobSidecar>>> fetch(final Eth2Peer peer) {
    final List<BlobIdentifier> blobIdentifiers = blockRootAndBlobIdentifiers.blobIdentifiers();
    final List<BlobSidecar> blobSidecars = new ArrayList<>(blobIdentifiers.size());
    return peer.requestBlobSidecarsByRoot(
            blobIdentifiers, RpcResponseListener.from(blobSidecars::add))
        .thenApply(__ -> FetchResult.createSuccessful(peer, blobSidecars))
        .exceptionally(
            err -> {
              LOG.debug(
                  String.format(
                      "Failed to fetch %d blob sidecars for block root %s from peer %s",
                      blobIdentifiers.size(),
                      blockRootAndBlobIdentifiers.blockRoot(),
                      peer.getId()),
                  err);
              return FetchResult.createFailed(peer, Status.FETCH_FAILED);
            });
  }
}
