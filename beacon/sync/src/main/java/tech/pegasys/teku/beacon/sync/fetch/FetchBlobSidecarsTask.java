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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class FetchBlobSidecarsTask extends AbstractFetchTask<Bytes32, List<BlobSidecar>> {

  private static final Logger LOG = LogManager.getLogger();

  private final Bytes32 blockRoot;
  private final List<BlobIdentifier> blobIdentifiers;

  FetchBlobSidecarsTask(
      final P2PNetwork<Eth2Peer> eth2Network,
      final Bytes32 blockRoot,
      final List<BlobIdentifier> blobIdentifiers) {
    super(eth2Network, Optional.empty());
    this.blockRoot = blockRoot;
    this.blobIdentifiers = blobIdentifiers;
  }

  public FetchBlobSidecarsTask(
      final P2PNetwork<Eth2Peer> eth2Network,
      final Optional<Eth2Peer> preferredPeer,
      final Bytes32 blockRoot,
      final List<BlobIdentifier> blobIdentifiers) {
    super(eth2Network, preferredPeer);
    this.blockRoot = blockRoot;
    this.blobIdentifiers = blobIdentifiers;
  }

  @Override
  public Bytes32 getKey() {
    return blockRoot;
  }

  @Override
  SafeFuture<FetchResult<List<BlobSidecar>>> fetch(final Eth2Peer peer) {
    final SafeFuture<FetchResult<List<BlobSidecar>>> fetchResult = new SafeFuture<>();
    final List<BlobSidecar> blobSidecars = new ArrayList<>();
    peer.requestBlobSidecarsByRoot(
            blobIdentifiers,
            new RpcResponseHandler<>() {
              @Override
              public void onCompleted(final Optional<? extends Throwable> error) {
                error.ifPresentOrElse(
                    err -> {
                      logFetchError(peer, err);
                      fetchResult.complete(FetchResult.createFailed(peer, Status.FETCH_FAILED));
                    },
                    () -> fetchResult.complete(FetchResult.createSuccessful(peer, blobSidecars)));
              }

              @Override
              public SafeFuture<?> onResponse(final BlobSidecar response) {
                blobSidecars.add(response);
                return SafeFuture.COMPLETE;
              }
            })
        .finish(
            err -> {
              logFetchError(peer, err);
              fetchResult.complete(FetchResult.createFailed(peer, Status.FETCH_FAILED));
            });
    return fetchResult;
  }

  private void logFetchError(final Eth2Peer peer, final Throwable err) {
    LOG.error(
        String.format(
            "Failed to fetch %d blob sidecars for block root %s from peer %s",
            blobIdentifiers.size(), blockRoot, peer.getId()),
        err);
  }
}
