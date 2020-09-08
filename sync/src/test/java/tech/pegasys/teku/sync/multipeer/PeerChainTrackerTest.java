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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.sync.multipeer.chains.TargetChainTestUtil.chainWith;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer.PeerStatusSubscriber;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedSubscriber;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.multipeer.eventthread.EventThread;
import tech.pegasys.teku.sync.multipeer.eventthread.InlineEventThread;

class PeerChainTrackerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Eth2Peer> p2pNetwork = mock(P2PNetwork.class);

  private final Eth2Peer peer = mock(Eth2Peer.class);

  private final EventThread eventThread = new InlineEventThread();
  private final PeerStatus status =
      new PeerStatus(
          dataStructureUtil.randomBytes4(),
          dataStructureUtil.randomBytes32(),
          dataStructureUtil.randomEpoch(),
          dataStructureUtil.randomBytes32(),
          dataStructureUtil.randomUInt64());

  private final PeerChainTracker tracker = new PeerChainTracker(eventThread, p2pNetwork);

  @BeforeEach
  void setUp() {
    tracker.start();
  }

  @Test
  void shouldUpdatePeerChainWhenStatusUpdates() {
    connectPeer(peer);

    updateStatus(peer, status);

    final TargetChain finalizedChain =
        chainWith(
            new SlotAndBlockRoot(
                compute_start_slot_at_epoch(status.getFinalizedEpoch()), status.getFinalizedRoot()),
            peer);
    final TargetChain nonfinalizedChain =
        chainWith(new SlotAndBlockRoot(status.getHeadSlot(), status.getHeadRoot()), peer);
    assertThat(tracker.getFinalizedChains().streamChains()).containsExactly(finalizedChain);
    assertThat(tracker.getNonFinalizedChains().streamChains()).containsExactly(nonfinalizedChain);
  }

  @Test
  void shouldRemovePeerWhenDisconnected() {
    connectPeer(peer);
    updateStatus(peer, status);

    disconnectPeer(peer);

    assertThat(tracker.getFinalizedChains().streamChains()).isEmpty();
    assertThat(tracker.getNonFinalizedChains().streamChains()).isEmpty();
  }

  private void updateStatus(final Eth2Peer peer, final PeerStatus status) {
    final ArgumentCaptor<PeerStatusSubscriber> statusCaptor =
        ArgumentCaptor.forClass(PeerStatusSubscriber.class);
    verify(peer).subscribeStatusUpdates(statusCaptor.capture());

    final PeerStatusSubscriber statusSubscriber = statusCaptor.getValue();
    statusSubscriber.onPeerStatus(status);
  }

  private void disconnectPeer(final Eth2Peer peer) {
    final ArgumentCaptor<PeerDisconnectedSubscriber> disconnectCaptor =
        ArgumentCaptor.forClass(PeerDisconnectedSubscriber.class);
    verify(peer).subscribeDisconnect(disconnectCaptor.capture());

    final PeerDisconnectedSubscriber statusSubscriber = disconnectCaptor.getValue();
    statusSubscriber.onDisconnected(Optional.empty(), false);
  }

  @SuppressWarnings("unchecked")
  private void connectPeer(final Eth2Peer peer) {
    final ArgumentCaptor<PeerConnectedSubscriber<Eth2Peer>> connectCaptor =
        ArgumentCaptor.forClass(PeerConnectedSubscriber.class);
    verify(p2pNetwork).subscribeConnect(connectCaptor.capture());

    final PeerConnectedSubscriber<Eth2Peer> connectSubscriber = connectCaptor.getValue();
    connectSubscriber.onConnected(peer);
  }
}
