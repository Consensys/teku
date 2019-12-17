/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.sync;

import com.google.common.primitives.UnsignedLong;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.statetransition.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class SyncManager {

  private final Eth2Network network;
  private final ChainStorageClient storageClient;
  private final BlockImporter blockImporter;

  private final AtomicBoolean syncActive = new AtomicBoolean(false);

  public SyncManager(
      final Eth2Network network,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter) {
    this.network = network;
    this.storageClient = storageClient;
    this.blockImporter = blockImporter;
    network.subscribeConnect(this::onNewPeer);
  }

  public CompletableFuture<Void> sync() {
    if (!syncActive.compareAndSet(false, true)) {
      return CompletableFuture.failedFuture(new RuntimeException("Sync already active"));
    }
    return executeSync().thenRun(() -> syncActive.set(false));
  }

  private CompletableFuture<Void> executeSync() {
    Optional<Eth2Peer> possibleSyncPeer = findBestSyncPeer();

    // If there are no peers ahead of us, return
    if (possibleSyncPeer.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    Eth2Peer syncPeer = possibleSyncPeer.get();
    PeerSync peerSync = new PeerSync(syncPeer, storageClient, blockImporter);

    return peerSync
        .sync()
        .thenCompose(
            result -> {
              if (result != PeerSyncResult.SUCCESSFUL_SYNC) {
                return executeSync();
              } else {
                return CompletableFuture.completedFuture(null);
              }
            });
  }

  private Optional<Eth2Peer> findBestSyncPeer() {
    return network
        .streamPeers()
        .filter(this::isPeerSyncSuitable)
        .max(Comparator.comparing(p -> p.getStatus().getFinalizedEpoch()));
  }

  private void onNewPeer(Eth2Peer peer) {
    if (isPeerSyncSuitable(peer)) {
      if (!syncActive.get()) {
        executeSync();
      }
    }
  }

  private boolean isPeerSyncSuitable(Eth2Peer peer) {
    UnsignedLong ourFinalizedEpoch = storageClient.getFinalizedEpoch();
    return peer.getStatus().getFinalizedEpoch().compareTo(ourFinalizedEpoch) > 0;
  }
}
