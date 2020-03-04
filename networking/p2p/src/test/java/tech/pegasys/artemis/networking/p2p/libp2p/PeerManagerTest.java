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

package tech.pegasys.artemis.networking.p2p.libp2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;

public class PeerManagerTest {

  private final PeerManager peerManager =
      new PeerManager(new NoOpMetricsSystem(), Collections.emptyList(), Collections.emptyMap());

  @Test
  public void subscribeConnect_singleListener() {
    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    // Add a peer
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(new MockNodeId(1));
    peerManager.onConnectedPeer(peer);
    assertThat(connectedPeers).containsExactly(peer);

    // Add another peer
    final Peer peer2 = mock(Peer.class);
    when(peer2.getId()).thenReturn(new MockNodeId(2));
    peerManager.onConnectedPeer(peer2);
    assertThat(connectedPeers).containsExactly(peer, peer2);
  }

  @Test
  public void subscribeConnect_multipleListeners() {
    final List<Peer> connectedPeers = new ArrayList<>();
    final List<Peer> connectedPeersB = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    peerManager.subscribeConnect(connectedPeersB::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(new MockNodeId(1));
    peerManager.onConnectedPeer(peer);

    assertThat(connectedPeers).containsExactly(peer);
    assertThat(connectedPeersB).containsExactly(peer);
  }
}
