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

import static tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage.REASON_FAULT_ERROR;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream;
import tech.pegasys.artemis.statetransition.StateTransitionException;
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
    Optional<Eth2Peer> possibleSyncPeer = findBestSyncPeer();

    // If there are no peers ahead of us, return
    if (possibleSyncPeer.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    Eth2Peer syncPeer = possibleSyncPeer.get();
    UnsignedLong syncAdvertisedFinalizedEpoch = syncPeer.getStatus().getFinalizedEpoch();

    CompletableFuture<Void> syncCompletableFuture =
        requestSyncBlocks(syncPeer, blockResponseListener(syncPeer));

    return syncCompletableFuture.thenRun(
        () -> {
          if (storageClient.getFinalizedEpoch().compareTo(syncAdvertisedFinalizedEpoch) < 0) {
            disconnectFromPeerAndRunSyncAgain(syncPeer);
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

  private CompletableFuture<Void> requestSyncBlocks(
      Eth2Peer peer, ResponseStream.ResponseListener<BeaconBlock> blockResponseListener) {
    Eth2Peer.StatusData peerStatusData = peer.getStatus();
    Bytes32 headBlockRoot = peerStatusData.getHeadRoot();
    UnsignedLong headBlockSlot = peerStatusData.getHeadSlot();
    UnsignedLong startSlot = compute_start_slot_at_epoch(storageClient.getFinalizedEpoch());
    UnsignedLong step = UnsignedLong.ONE;
    UnsignedLong count = headBlockSlot.minus(startSlot);
    return peer.requestBlocksByRange(headBlockRoot, startSlot, count, step, blockResponseListener);
  }

  private ResponseStream.ResponseListener<BeaconBlock> blockResponseListener(Eth2Peer peer) {
    return ((block) -> {
      try {
        blockImporter.importBlock(block);
      } catch (StateTransitionException e) {
        disconnectFromPeerAndRunSyncAgain(peer);
        throw new InvalidResponseException("Received bad block from peer", e);
      }
    });
  }

  private void disconnectFromPeerAndRunSyncAgain(Eth2Peer peer) {
    // TODO: this is going to schedule a new sync but never return a CompletableFuture to know when
    // it completes or if it fails. One option to solve this would be to have one class responsible
    // for syncing to a single peer, and another responsible for starting a new sync whenever the
    // previous one finishes (it will eventually decide whether to activate a new sync based on
    // whether we are in sync or not)
    peer.sendGoodbye(REASON_FAULT_ERROR).thenRun(this::sync);
  }
}
