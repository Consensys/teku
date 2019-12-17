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

package tech.pegasys.artemis.networking.eth2.peers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;

public class Eth2PeerManagerTest {

  private final ChainStorageClient storageClient = mock(ChainStorageClient.class);
  private final HistoricalChainData historicalChainData = mock(HistoricalChainData.class);
  private final StatusMessageFactory statusMessageFactory = new StatusMessageFactory(storageClient);
  private final Eth2PeerManager peerManager =
      new Eth2PeerManager(storageClient, historicalChainData, new NoOpMetricsSystem());

  @Test
  public void subscribeConnect_singleListener() {
    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    // Add a peer
    final Peer peer = createPeer();
    final Eth2Peer eth2Peer = createEth2Peer(peer);
    peerManager.onConnect(peer);
    assertThat(connectedPeers).containsExactly(eth2Peer);

    // Add another peer
    final Peer peerB = createPeer();
    final Eth2Peer eth2PeerB = createEth2Peer(peerB);
    peerManager.onConnect(peerB);
    assertThat(connectedPeers).containsExactly(eth2Peer, eth2PeerB);
  }

  @Test
  public void subscribeConnect_multipleListeners() {
    final List<Peer> connectedPeers = new ArrayList<>();
    final List<Peer> connectedPeersB = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    peerManager.subscribeConnect(connectedPeersB::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    final Peer peer = createPeer();
    final Eth2Peer eth2Peer = createEth2Peer(peer);
    peerManager.onConnect(peer);

    assertThat(connectedPeers).containsExactly(eth2Peer);
    assertThat(connectedPeersB).containsExactly(eth2Peer);
  }

  private Peer createPeer() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(new MockNodeId());
    return peer;
  }

  private Eth2Peer createEth2Peer(final Peer peer) {
    return new Eth2Peer(peer, peerManager.getRpcMethods(), statusMessageFactory);
  }
}
