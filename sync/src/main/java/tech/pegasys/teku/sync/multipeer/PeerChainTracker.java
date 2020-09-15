/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.sync.multipeer;

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.sync.multipeer.chains.TargetChains;

public class PeerChainTracker {
  private final EventThread eventThread;
  private final P2PNetwork<Eth2Peer> p2pNetwork;
  private final SyncController syncController;
  private final TargetChains finalizedChains = new TargetChains();
  private final TargetChains nonFinalizedChains = new TargetChains();
  private volatile long connectSubscription;

  @VisibleForTesting
  PeerChainTracker(
      final EventThread eventThread,
      final P2PNetwork<Eth2Peer> p2pNetwork,
      final SyncController syncController) {
    this.eventThread = eventThread;
    this.p2pNetwork = p2pNetwork;
    this.syncController = syncController;
  }

  public void start() {
    connectSubscription =
        p2pNetwork.subscribeConnect(
            peer -> {
              peer.subscribeStatusUpdates(
                  status -> eventThread.execute(() -> onPeerStatusUpdate(peer, status)));
              peer.subscribeDisconnect(
                  (reason, locallyInitiated) ->
                      eventThread.execute(() -> onPeerDisconnected(peer)));
            });
  }

  public void stop() {
    p2pNetwork.unsubscribeConnect(connectSubscription);
  }

  private void onPeerDisconnected(final Eth2Peer peer) {
    eventThread.checkOnEventThread();
    finalizedChains.onPeerDisconnected(peer);
    nonFinalizedChains.onPeerDisconnected(peer);
  }

  private void onPeerStatusUpdate(final Eth2Peer peer, final PeerStatus status) {
    eventThread.checkOnEventThread();
    final SlotAndBlockRoot finalizedChainHead =
        new SlotAndBlockRoot(
            status.getFinalizedCheckpoint().getEpochStartSlot(), status.getFinalizedRoot());
    finalizedChains.onPeerStatusUpdated(peer, finalizedChainHead);

    final SlotAndBlockRoot nonFinalizedChainHead =
        new SlotAndBlockRoot(status.getHeadSlot(), status.getHeadRoot());
    nonFinalizedChains.onPeerStatusUpdated(peer, nonFinalizedChainHead);

    syncController.onTargetChainsUpdated(finalizedChains);
  }

  @VisibleForTesting
  TargetChains getFinalizedChains() {
    return finalizedChains;
  }

  @VisibleForTesting
  TargetChains getNonFinalizedChains() {
    return nonFinalizedChains;
  }
}
