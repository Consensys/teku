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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_block;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
  private final Set<Eth2Peer> peerBlacklist = Collections.newSetFromMap(new ConcurrentHashMap<>());

  public SyncManager(
      final Eth2Network network, final ChainStorageClient storageClient, final EventBus eventBus) {
    this.network = network;
    this.storageClient = storageClient;
    this.eventBus = eventBus;
  }

  private void sync() {
    Eth2Peer syncPeer = findSyncPeer().orElseThrow();
    requestSyncBlocks(
        syncPeer,
        ((block) -> {
          try {
            Store.Transaction transaction = storageClient.getStore().startTransaction();
            on_block(transaction, block, stateTransition);
            transaction.commit();
            eventBus.post(new StoreDiskUpdateEvent(transaction));
          } catch (StateTransitionException e) {
            peerBlacklist.add(syncPeer);
            sync();
          }
        }));
  }

  private Optional<Eth2Peer> findSyncPeer() {
    UnsignedLong ourFinalizedEpoch = storageClient.getStore().getFinalizedCheckpoint().getEpoch();
    return network
        .streamPeers()
        .filter(peer -> !peerBlacklist.contains(peer))
        .filter(
            peer -> {
              Eth2Peer.StatusData statusData = peer.getStatus();
              return statusData.getFinalizedEpoch().compareTo(ourFinalizedEpoch) > 0;
            })
        .findAny();
  }

  private void requestSyncBlocks(
      Eth2Peer peer, ResponseStream.ResponseListener<BeaconBlock> blockResponseListener) {
    Eth2Peer.StatusData peerStatusData = peer.getStatus();
    Bytes32 headBlockRoot = peerStatusData.getHeadRoot();
    UnsignedLong peerFinalizedBlockSlot =
        compute_start_slot_at_epoch(peerStatusData.getFinalizedEpoch());
    UnsignedLong startSlot =
        compute_start_slot_at_epoch(storageClient.getStore().getFinalizedCheckpoint().getEpoch());
    UnsignedLong step = UnsignedLong.ONE;
    UnsignedLong count = peerFinalizedBlockSlot.minus(startSlot);
    peer.requestBlocksByRange(headBlockRoot, startSlot, count, step, blockResponseListener);
  }
}
