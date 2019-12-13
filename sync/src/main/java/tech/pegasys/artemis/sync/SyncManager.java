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

import com.google.common.base.Throwables;
import com.google.common.primitives.UnsignedLong;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.statetransition.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class SyncManager {

  private final Eth2Network network;
  private final ChainStorageClient storageClient;
  private final BlockImporter blockImporter;

  public SyncManager(
      final Eth2Network network,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter) {
    this.network = network;
    this.storageClient = storageClient;
    this.blockImporter = blockImporter;
  }

  public CompletableFuture<Void> sync() {
    CompletableFuture<Void> future = new CompletableFuture<>();
    executeSync(future);
    return future;
  }

  private void executeSync(CompletableFuture<Void> finalResult) {
    Optional<Eth2Peer> possibleSyncPeer = findBestSyncPeer();

    // If there are no peers ahead of us, return
    if (possibleSyncPeer.isEmpty()) {
      finalResult.complete(null);
      return;
    }

    Eth2Peer syncPeer = possibleSyncPeer.get();
    PeerSync peerSync = new PeerSync(syncPeer, storageClient, blockImporter);

    peerSync
        .sync()
        .whenComplete(
            (result, err) -> {
              if (err != null) {
                Throwable rootException = Throwables.getRootCause(err);
                if (rootException instanceof PeerSync.FaultyAdvertisementException
                    || rootException instanceof PeerSync.BadBlockException) {
                  executeSync(finalResult);
                } else {
                  finalResult.completeExceptionally(err);
                }
              } else {
                finalResult.complete(null);
              }
            });
  }

  private Optional<Eth2Peer> findBestSyncPeer() {
    UnsignedLong ourFinalizedEpoch = storageClient.getFinalizedEpoch();
    return network
        .streamPeers()
        .filter(peer -> peer.getStatus().getFinalizedEpoch().compareTo(ourFinalizedEpoch) > 0)
        .max(Comparator.comparing(p -> p.getStatus().getFinalizedEpoch()));
  }
}
