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
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_block;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;

public class SyncManager {

  private final Eth2Network network;
  private final ChainStorageClient storageClient;
  private final EventBus eventBus;

  private final StateTransition stateTransition = new StateTransition(false);
  private static final int BAD_SYNC_DISCONNECT_REASON_CODE = 128;

  public SyncManager(
      final Eth2Network network, final ChainStorageClient storageClient, final EventBus eventBus) {
    this.network = network;
    this.storageClient = storageClient;
    this.eventBus = eventBus;
  }

  private void sync() {
    Optional<Eth2Peer> possibleSyncPeer = findBestSyncPeer();

    // If there are no peers ahead of us, return
    if (possibleSyncPeer.isEmpty()) {
      return;
    }

    Eth2Peer syncPeer = possibleSyncPeer.get();
    UnsignedLong syncAdvertisedFinalizedEpoch = syncPeer.getStatus().getFinalizedEpoch();

    CompletableFuture<Void> syncCompletableFuture =
        requestSyncBlocks(syncPeer, blockResponseListener(syncPeer));

    syncCompletableFuture.thenRun(
        () -> {
          if (storageClient
                  .getStore()
                  .getFinalizedCheckpoint()
                  .getEpoch()
                  .compareTo(syncAdvertisedFinalizedEpoch)
              < 0) {
            disconnectFromPeerAndRunSyncAgain(syncPeer);
          }
        });
  }

  private Optional<Eth2Peer> findBestSyncPeer() {
    UnsignedLong ourFinalizedEpoch = storageClient.getStore().getFinalizedCheckpoint().getEpoch();
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
    UnsignedLong startSlot =
        compute_start_slot_at_epoch(storageClient.getStore().getFinalizedCheckpoint().getEpoch());
    UnsignedLong step = UnsignedLong.ONE;
    UnsignedLong count = headBlockSlot.minus(startSlot);
    return peer.requestBlocksByRange(headBlockRoot, startSlot, count, step, blockResponseListener);
  }

  private ResponseStream.ResponseListener<BeaconBlock> blockResponseListener(Eth2Peer peer) {
    return ((block) -> {
      try {
        Store.Transaction transaction = storageClient.getStore().startTransaction();
        on_block(transaction, block, stateTransition);
        transaction.commit();
        eventBus.post(new StoreDiskUpdateEvent(transaction));
      } catch (StateTransitionException e) {
        disconnectFromPeerAndRunSyncAgain(peer);
      }
    });
  }

  private void disconnectFromPeerAndRunSyncAgain(Eth2Peer peer) {
    peer.sendGoodbye(REASON_FAULT_ERROR).thenRun(this::sync);
  }
}
