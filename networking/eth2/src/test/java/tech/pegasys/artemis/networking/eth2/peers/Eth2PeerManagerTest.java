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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.peers.Eth2PeerManager.PeerValidatorFactory;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.storage.client.CombinedChainDataClient;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.async.SafeFuture;

public class Eth2PeerManagerTest {

  private final PeerStatusFactory statusFactory = PeerStatusFactory.create(1L);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData storageClient = mock(RecentChainData.class);
  private final StatusMessageFactory statusMessageFactory = new StatusMessageFactory(storageClient);

  private final PeerChainValidator peerChainValidator = mock(PeerChainValidator.class);
  private final PeerValidatorFactory peerValidatorFactory = (peer, status) -> peerChainValidator;
  private final SafeFuture<Boolean> peerValidationResult = new SafeFuture<>();

  private final Eth2PeerManager peerManager =
      new Eth2PeerManager(
          combinedChainDataClient, storageClient, new NoOpMetricsSystem(), peerValidatorFactory);

  @BeforeEach
  public void setup() {
    when(peerChainValidator.run()).thenReturn(peerValidationResult);
  }

  @Test
  public void subscribeConnect_singleListener() {
    // Setup validation to succeed
    peerValidationResult.complete(true);

    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    // Add a peer
    final Peer peer = createPeer(1);
    final Eth2Peer eth2Peer = createEth2Peer(peer);
    peerManager.onConnect(peer);

    // Connect event should not broadcast until status is set
    assertThat(connectedPeers).isEmpty();

    // Set status and check event was broadcast
    setInitialPeerStatus(eth2Peer);
    assertThat(connectedPeers).containsExactly(eth2Peer);
  }

  @Test
  public void subscribeConnect_peerWithInvalidChain() {
    // Setup validation to fail
    peerValidationResult.complete(false);

    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    // Add a peer
    final Peer peer = createPeer(1);
    final Eth2Peer eth2Peer = createEth2Peer(peer);
    peerManager.onConnect(peer);

    // Connect event should not broadcast until status is set
    assertThat(connectedPeers).isEmpty();

    // Set status, which should trigger peerValidation to fail
    setInitialPeerStatus(eth2Peer);
    assertThat(connectedPeers).isEmpty();
  }

  @Test
  public void subscribeConnect_singleListener_multiplePeers() {
    // Setup validation to succeed
    peerValidationResult.complete(true);

    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    // Add a peer
    final Peer peer = createPeer(1);
    final Eth2Peer eth2Peer = createEth2Peer(peer);
    peerManager.onConnect(peer);
    setInitialPeerStatus(eth2Peer);
    assertThat(connectedPeers).containsExactly(eth2Peer);

    // Add another peer
    final Peer peerB = createPeer(2);
    final Eth2Peer eth2PeerB = createEth2Peer(peerB);
    peerManager.onConnect(peerB);
    setInitialPeerStatus(eth2PeerB);
    assertThat(connectedPeers).containsExactly(eth2Peer, eth2PeerB);
  }

  @Test
  public void subscribeConnect_multipleListeners() {
    // Setup validation to succeed
    peerValidationResult.complete(true);

    final List<Peer> connectedPeers = new ArrayList<>();
    final List<Peer> connectedPeersB = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);
    peerManager.subscribeConnect(connectedPeersB::add);
    // Sanity check
    assertThat(connectedPeers).isEmpty();

    final Peer peer = createPeer(1);
    final Eth2Peer eth2Peer = createEth2Peer(peer);
    peerManager.onConnect(peer);
    setInitialPeerStatus(eth2Peer);

    assertThat(connectedPeers).containsExactly(eth2Peer);
    assertThat(connectedPeersB).containsExactly(eth2Peer);
  }

  private Peer createPeer(final int id) {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(new MockNodeId(id));
    return peer;
  }

  private void setInitialPeerStatus(final Eth2Peer peer) {
    final PeerStatus status = statusFactory.random();
    peerManager.getConnectedPeer(peer.getId()).updateStatus(status);
  }

  private Eth2Peer createEth2Peer(final Peer peer) {
    final Eth2Peer eth2Peer =
        new Eth2Peer(peer, peerManager.getBeaconChainMethods(), statusMessageFactory);
    when(peer.idMatches(eth2Peer)).thenReturn(true);
    return eth2Peer;
  }
}
