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

package tech.pegasys.teku.networking.eth2.peers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import com.google.common.primitives.Ints;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.network.p2p.peer.StubPeer;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.p2p.connection.ReputationManager;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;

class Eth2PeerSelectionStrategyTest {

  private static final Optional<EnrForkId> ENR_FORK_ID = Optional.empty();
  private static final PeerAddress PEER1 = new PeerAddress(new MockNodeId(1));
  private static final PeerAddress PEER2 = new PeerAddress(new MockNodeId(2));
  private static final PeerAddress PEER3 = new PeerAddress(new MockNodeId(3));
  private static final PeerAddress PEER4 = new PeerAddress(new MockNodeId(4));
  private static final DiscoveryPeer DISCOVERY_PEER1 = createDiscoveryPeer(PEER1, 1);
  private static final DiscoveryPeer DISCOVERY_PEER2 = createDiscoveryPeer(PEER2, 2);
  private static final DiscoveryPeer DISCOVERY_PEER3 = createDiscoveryPeer(PEER3, 3);

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> network = mock(P2PNetwork.class);

  private final StubPeerScorer peerScorer = new StubPeerScorer();
  private final PeerSubnetSubscriptions peerSubnetSubscriptions =
      mock(PeerSubnetSubscriptions.class);
  private final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory =
      network -> peerSubnetSubscriptions;
  private final ReputationManager reputationManager = mock(ReputationManager.class);

  @BeforeEach
  void setUp() {
    when(peerSubnetSubscriptions.createScorer()).thenReturn(peerScorer);
    when(reputationManager.isConnectionInitiationAllowed(any())).thenReturn(true);
    when(network.createPeerAddress(any(DiscoveryPeer.class)))
        .thenAnswer(
            invocation -> {
              final DiscoveryPeer peer = invocation.getArgument(0);
              return new PeerAddress(new MockNodeId(peer.getPublicKey()));
            });
  }

  @Test
  void selectPeersToConnect_shouldNotConnectToPeersWithBadReputation() {
    final Eth2PeerSelectionStrategy strategy = createStrategy();
    when(reputationManager.isConnectionInitiationAllowed(PEER1)).thenReturn(false);
    when(reputationManager.isConnectionInitiationAllowed(PEER2)).thenReturn(true);

    assertThat(
            strategy.selectPeersToConnect(network, () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2)))
        .containsExactly(PEER2);
  }

  @Test
  void selectPeersToConnect_shouldLimitNumberOfNewConnections() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(1, 2);

    assertThat(
            strategy.selectPeersToConnect(
                network, () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3)))
        .containsExactly(PEER1, PEER2);
  }

  @Test
  void selectPeersToConnect_shouldConnectToNewPeersWhenSubnetsNeedsMoreSubscribers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(1, 2);
    when(network.getPeerCount()).thenReturn(2); // At upper bound of peers
    when(peerSubnetSubscriptions.getSubscribersRequired()).thenReturn(2);
    peerScorer.setScore(DISCOVERY_PEER1.getPersistentSubnets(), 0);
    peerScorer.setScore(DISCOVERY_PEER2.getPersistentSubnets(), 200);
    peerScorer.setScore(DISCOVERY_PEER3.getPersistentSubnets(), 150);

    // Connect to additional peers to try to fill subnets even though it goes over the target
    assertThat(
            strategy.selectPeersToConnect(
                network, () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3)))
        .containsExactlyInAnyOrder(PEER2, PEER3);
  }

  @Test
  void selectPeersToConnect_shouldConnectToHighestScoringPeers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(2, 2);

    final DiscoveryPeer discoveryPeer1 = createDiscoveryPeer(PEER1, 1);
    final DiscoveryPeer discoveryPeer2 = createDiscoveryPeer(PEER2, 2);
    final DiscoveryPeer discoveryPeer3 = createDiscoveryPeer(PEER3, 3);
    final DiscoveryPeer discoveryPeer4 = createDiscoveryPeer(PEER4, 4);
    peerScorer.setScore(discoveryPeer1.getPersistentSubnets(), 100);
    peerScorer.setScore(discoveryPeer2.getPersistentSubnets(), 200);
    peerScorer.setScore(discoveryPeer3.getPersistentSubnets(), 150);
    peerScorer.setScore(discoveryPeer4.getPersistentSubnets(), 500);

    assertThat(
            strategy.selectPeersToConnect(
                network,
                () -> List.of(discoveryPeer1, discoveryPeer2, discoveryPeer3, discoveryPeer4)))
        .containsExactlyInAnyOrder(PEER2, PEER4);
  }

  @Test
  void selectPeersToConnect_shouldNotConnectToAlreadyConnectedPeers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy();
    when(network.isConnected(PEER1)).thenReturn(true);
    when(network.isConnected(PEER3)).thenReturn(true);

    assertThat(
            strategy.selectPeersToConnect(
                network, () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3)))
        .containsExactly(PEER2);
  }

  @Test
  void selectPeersToDisconnect_shouldDisconnectLowestScoringPeersWhenPeerCountExceedsUpperBound() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(0, 1);
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    final StubPeer peer3 = new StubPeer(new MockNodeId(3));
    peerScorer.setScore(peer1.getId(), 100);
    peerScorer.setScore(peer2.getId(), 200);
    peerScorer.setScore(peer3.getId(), 150);

    when(network.getPeerCount()).thenReturn(3);
    when(network.streamPeers()).thenReturn(Stream.of(peer1, peer2, peer3));

    assertThat(strategy.selectPeersToDisconnect(network, peer -> true))
        .containsExactlyInAnyOrder(peer1, peer3);
  }

  @Test
  void selectPeersToDisconnect_shouldNotDisconnectFromPeersThatPredicateRejects() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(0, 0);
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    final StubPeer peer3 = new StubPeer(new MockNodeId(3));
    when(network.getPeerCount()).thenReturn(3);
    when(network.streamPeers()).thenReturn(Stream.of(peer1, peer2, peer3));

    assertThat(strategy.selectPeersToDisconnect(network, peer -> peer != peer2))
        .containsExactlyInAnyOrder(peer1, peer3);
  }

  private Eth2PeerSelectionStrategy createStrategy() {
    return createStrategy(10, 20);
  }

  private Eth2PeerSelectionStrategy createStrategy(
      final int peerCountLowerBound, final int peerCountUpperBound) {
    return new Eth2PeerSelectionStrategy(
        new TargetPeerRange(peerCountLowerBound, peerCountUpperBound),
        peerSubnetSubscriptionsFactory,
        reputationManager);
  }

  private static DiscoveryPeer createDiscoveryPeer(final PeerAddress peer, final int... subnetIds) {
    return createDiscoveryPeer(peer.getId().toBytes(), subnetIds);
  }

  private static DiscoveryPeer createDiscoveryPeer(final Bytes peerId, final int... subnetIds) {
    return new DiscoveryPeer(
        peerId,
        new InetSocketAddress(InetAddress.getLoopbackAddress(), peerId.trimLeadingZeros().toInt()),
        ENR_FORK_ID,
        new Bitvector(Ints.asList(subnetIds), ATTESTATION_SUBNET_COUNT));
  }
}
