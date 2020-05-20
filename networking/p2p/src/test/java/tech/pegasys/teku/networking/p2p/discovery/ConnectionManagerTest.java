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

package tech.pegasys.teku.networking.p2p.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.network.p2p.peer.StubPeer;
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager;
import tech.pegasys.teku.networking.p2p.connection.ReputationManager;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectRequestHandler.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.async.StubAsyncRunner;

class ConnectionManagerTest {

  private static final Optional<Bytes> ENR_FORK_ID = Optional.of(Bytes.EMPTY);
  private static final PeerAddress PEER1 = new PeerAddress(new MockNodeId(1));
  private static final PeerAddress PEER2 = new PeerAddress(new MockNodeId(2));
  private static final PeerAddress PEER3 = new PeerAddress(new MockNodeId(3));
  private static final PeerAddress PEER4 = new PeerAddress(new MockNodeId(4));
  private static final DiscoveryPeer DISCOVERY_PEER1 =
      new DiscoveryPeer(Bytes.of(1), new InetSocketAddress(1), ENR_FORK_ID);
  private static final DiscoveryPeer DISCOVERY_PEER2 =
      new DiscoveryPeer(Bytes.of(2), new InetSocketAddress(2), ENR_FORK_ID);
  private static final DiscoveryPeer DISCOVERY_PEER3 =
      new DiscoveryPeer(Bytes.of(3), new InetSocketAddress(3), ENR_FORK_ID);
  private static final DiscoveryPeer DISCOVERY_PEER4 =
      new DiscoveryPeer(Bytes.of(4), new InetSocketAddress(4), ENR_FORK_ID);

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> network = mock(P2PNetwork.class);

  private final DiscoveryService discoveryService = mock(DiscoveryService.class);
  private final ReputationManager reputationManager = mock(ReputationManager.class);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @BeforeEach
  public void setUp() {
    when(reputationManager.isConnectionInitiationAllowed(any())).thenReturn(true);
    when(discoveryService.searchForPeers()).thenReturn(new SafeFuture<>());
    when(network.createPeerAddress(DISCOVERY_PEER1)).thenReturn(PEER1);
    when(network.createPeerAddress(DISCOVERY_PEER2)).thenReturn(PEER2);
    when(network.createPeerAddress(DISCOVERY_PEER3)).thenReturn(PEER3);
    when(network.createPeerAddress(DISCOVERY_PEER4)).thenReturn(PEER4);
  }

  @Test
  public void shouldConnectToStaticPeersOnStart() {
    final ConnectionManager manager = createManager(PEER1, PEER2);
    when(network.connect(any(PeerAddress.class))).thenReturn(SafeFuture.completedFuture(null));
    manager.start().join();

    verify(network).connect(PEER1);
    verify(network).connect(PEER2);
  }

  @Test
  public void shouldRetryConnectionToStaticPeerAfterDelayWhenInitialAttemptFails() {
    final ConnectionManager manager = createManager(PEER1);

    final SafeFuture<Peer> connectionFuture1 = new SafeFuture<>();
    final SafeFuture<Peer> connectionFuture2 = new SafeFuture<>();
    when(network.connect(PEER1)).thenReturn(connectionFuture1).thenReturn(connectionFuture2);
    manager.start().join();
    verify(network).connect(PEER1);

    connectionFuture1.completeExceptionally(new RuntimeException("Nope"));

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(network, times(2)).connect(PEER1);
  }

  @Test
  public void shouldRetryConnectionToStaticPeerAfterRetryAndDisconnect() {
    final ConnectionManager manager = createManager(PEER1);
    final MockNodeId peerId = new MockNodeId();
    final StubPeer peer = new StubPeer(peerId);

    final SafeFuture<Peer> connectionFuture1 = new SafeFuture<>();
    final SafeFuture<Peer> connectionFuture2 = SafeFuture.completedFuture(peer);
    when(network.connect(PEER1)).thenReturn(connectionFuture1).thenReturn(connectionFuture2);
    manager.start().join();
    verify(network).connect(PEER1);

    connectionFuture1.completeExceptionally(new RuntimeException("Nope"));

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(network, times(2)).connect(PEER1);

    peer.disconnectImmediately();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(network, times(3)).connect(PEER1);
  }

  @Test
  public void shouldReconnectWhenPersistentPeerDisconnects() {
    final ConnectionManager manager = createManager(PEER1);

    final MockNodeId peerId = new MockNodeId();
    final StubPeer peer = new StubPeer(peerId);
    when(network.connect(PEER1))
        .thenReturn(SafeFuture.completedFuture(peer))
        .thenReturn(new SafeFuture<>());
    manager.start().join();
    verify(network).connect(PEER1);
    peer.disconnectImmediately();

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(network, times(2)).connect(PEER1);
  }

  @Test
  public void shouldAddNewPeerToStaticList() {
    final ConnectionManager manager = createManager();

    final MockNodeId peerId = new MockNodeId();
    final StubPeer peer = new StubPeer(peerId);
    when(network.connect(PEER1))
        .thenReturn(SafeFuture.completedFuture(peer))
        .thenReturn(new SafeFuture<>());
    manager.start().join();

    manager.addStaticPeer(PEER1);
    verify(network).connect(PEER1);
    peer.disconnectImmediately();

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(network, times(2)).connect(PEER1);
  }

  @Test
  public void shouldNotAddDuplicatePeerToStaticList() {
    final ConnectionManager manager = createManager(PEER1);

    final MockNodeId peerId = new MockNodeId();
    final StubPeer peer = new StubPeer(peerId);
    when(network.connect(PEER1))
        .thenReturn(SafeFuture.completedFuture(peer))
        .thenReturn(new SafeFuture<>());
    manager.start().join();

    verify(network).connect(PEER1);

    manager.addStaticPeer(PEER1);
    // Doesn't attempt to connect a second time.
    verify(network, times(1)).connect(PEER1);
  }

  @Test
  public void shouldConnectToKnownPeersWhenStarted() {
    final ConnectionManager manager = createManager();
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2));
    when(network.connect(any(PeerAddress.class))).thenReturn(new SafeFuture<>());

    manager.start().join();

    verify(network).connect(PEER1);
    verify(network).connect(PEER2);
  }

  @Test
  public void shouldNotRetryConnectionsToDiscoveredPeersOnFailure() {
    final ConnectionManager manager = createManager();
    when(discoveryService.streamKnownPeers()).thenReturn(Stream.of(DISCOVERY_PEER1));
    final SafeFuture<Peer> connectionFuture = new SafeFuture<>();
    when(network.connect(any(PeerAddress.class))).thenReturn(connectionFuture);

    manager.start().join();
    verify(network).connect(PEER1);

    connectionFuture.completeExceptionally(new RuntimeException("Failed"));

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verify(network, times(1)).connect(PEER1); // No further attempts to connect
  }

  @Test
  public void shouldNotRetryConnectionsToDiscoveredPeersOnDisconnect() {
    final ConnectionManager manager = createManager();
    when(discoveryService.streamKnownPeers()).thenReturn(Stream.of(DISCOVERY_PEER1));
    final SafeFuture<Peer> connectionFuture = new SafeFuture<>();
    when(network.connect(any(PeerAddress.class))).thenReturn(connectionFuture);

    manager.start().join();
    verify(network).connect(PEER1);

    final StubPeer peer = new StubPeer(new MockNodeId(DISCOVERY_PEER1.getPublicKey()));
    connectionFuture.complete(peer);

    peer.disconnectImmediately();
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verify(network, times(1)).connect(PEER1); // No further attempts to connect
  }

  @Test
  public void shouldPeriodicallyTriggerNewDiscoverySearch() {
    final SafeFuture<Void> search1 = new SafeFuture<>();
    final SafeFuture<Void> search2 = new SafeFuture<>();
    when(discoveryService.searchForPeers()).thenReturn(search1).thenReturn(search2);

    final ConnectionManager manager = createManager();
    manager.start().join();

    verify(discoveryService, times(1)).searchForPeers();

    search1.complete(null);
    verify(discoveryService, times(1)).searchForPeers(); // Shouldn't immediately search again

    asyncRunner.executeQueuedActions();
    verify(discoveryService, times(2)).searchForPeers(); // But should after a delay
  }

  @Test
  public void shouldTriggerNewDiscoverySearchAfterFailure() {
    final SafeFuture<Void> search1 = new SafeFuture<>();
    final SafeFuture<Void> search2 = new SafeFuture<>();
    when(discoveryService.searchForPeers()).thenReturn(search1).thenReturn(search2);

    final ConnectionManager manager = createManager();
    manager.start().join();

    verify(discoveryService, times(1)).searchForPeers();

    search1.completeExceptionally(new RuntimeException("Nope"));
    verify(discoveryService, times(1)).searchForPeers(); // Shouldn't immediately search again

    asyncRunner.executeQueuedActions();
    verify(discoveryService, times(2)).searchForPeers(); // But should after a delay
  }

  @Test
  public void shouldStopTriggeringDiscoverySearchesWhenStopped() {
    final SafeFuture<Void> search1 = new SafeFuture<>();
    final SafeFuture<Void> search2 = new SafeFuture<>();
    when(discoveryService.searchForPeers()).thenReturn(search1).thenReturn(search2);
    final ConnectionManager manager = createManager();

    manager.start().join();
    verify(discoveryService).searchForPeers();

    manager.stop().join();

    search1.complete(null);

    asyncRunner.executeQueuedActions();
    verify(discoveryService, times(1)).searchForPeers(); // Shouldn't search again
  }

  @Test
  public void shouldConnectToKnownPeersWhenDiscoverySearchCompletes() {
    final SafeFuture<Void> search1 = new SafeFuture<>();
    when(network.connect(any(PeerAddress.class))).thenReturn(new SafeFuture<>());
    when(discoveryService.searchForPeers()).thenReturn(search1);
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.empty()) // No known peers at startup
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2)); // Search found some new peers
    final ConnectionManager manager = createManager();

    manager.start().join();
    verify(discoveryService).searchForPeers();

    search1.complete(null);

    verify(network).connect(PEER1);
    verify(network).connect(PEER2);
  }

  @Test
  public void shouldNotConnectToKnownPeersWithBadReputation() {
    final SafeFuture<Void> search1 = new SafeFuture<>();
    when(network.connect(any(PeerAddress.class))).thenReturn(new SafeFuture<>());
    when(discoveryService.searchForPeers()).thenReturn(search1);
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.empty()) // No known peers at startup
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2)); // Search found some new peers
    when(reputationManager.isConnectionInitiationAllowed(PEER1)).thenReturn(false);
    when(reputationManager.isConnectionInitiationAllowed(PEER2)).thenReturn(true);
    final ConnectionManager manager = createManager();

    manager.start().join();
    verify(discoveryService).searchForPeers();

    search1.complete(null);

    verify(network, never()).connect(PEER1);
    verify(network).connect(PEER2);
  }

  @Test
  public void shouldLimitNumberOfNewConnectionsMadeToDiscoveryPeersOnStartup() {
    final ConnectionManager manager = createManager(new TargetPeerRange(1, 2));
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3));
    when(network.connect(any(PeerAddress.class))).thenReturn(new SafeFuture<>());

    manager.start().join();

    verify(network).connect(PEER1);
    verify(network).connect(PEER2);
    verify(network, never()).connect(PEER3);
  }

  @Test
  public void shouldLimitNumberOfNewConnectionsMadeToDiscoveryPeersOnRetry() {
    final SafeFuture<Void> search1 = new SafeFuture<>();
    when(network.connect(any(PeerAddress.class))).thenReturn(new SafeFuture<>());
    when(discoveryService.searchForPeers()).thenReturn(search1);
    when(discoveryService.streamKnownPeers())
        // At startup
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3))
        // After search
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3, DISCOVERY_PEER4));

    final ConnectionManager manager = createManager(new TargetPeerRange(2, 3));

    when(network.getPeerCount()).thenReturn(0);
    manager.start().join();
    verify(discoveryService).searchForPeers();
    verify(network).connect(PEER1);
    verify(network).connect(PEER2);
    verify(network).connect(PEER3);
    verify(network, never()).connect(PEER4);

    // Only peer 2 actually connected, so should try to connect 2 more nodes
    when(network.getPeerCount()).thenReturn(1);
    when(network.isConnected(PEER2)).thenReturn(true);
    search1.complete(null);

    verify(network, times(2)).connect(PEER1);
    verify(network, times(1)).connect(PEER2); // Not retried
    verify(network, times(2)).connect(PEER3); // Retried
    verify(network, never()).connect(PEER4); // Still not required
  }

  @Test
  public void shouldDisconnectDiscoveredPeersWhenPeerCountExceedsLimit() {
    final ConnectionManager manager = createManager(new TargetPeerRange(1, 1));
    manager.start().join();

    final PeerConnectedSubscriber<Peer> peerConnectedSubscriber = getPeerConnectedSubscriber();

    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    when(network.streamPeers()).thenReturn(Stream.of(peer2, peer1));
    when(network.getPeerCount()).thenReturn(2);
    peerConnectedSubscriber.onConnected(peer1);

    // Should disconnect one peer to get back down to our target of max 1 peer.
    assertThat(peer2.getDisconnectReason()).contains(DisconnectReason.TOO_MANY_PEERS);
    assertThat(peer1.isConnected()).isTrue();
  }

  @Test
  public void shouldNotDisconnectStaticPeersWhenPeerCountExceedsLimit() {
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    final ConnectionManager manager = createManager(new TargetPeerRange(1, 1), PEER1, PEER2);
    when(network.connect(PEER1)).thenReturn(SafeFuture.completedFuture(peer1));
    when(network.connect(PEER2)).thenReturn(SafeFuture.completedFuture(peer2));
    manager.start().join();

    final PeerConnectedSubscriber<Peer> peerConnectedSubscriber = getPeerConnectedSubscriber();

    when(network.streamPeers()).thenReturn(Stream.of(peer2, peer1));
    when(network.getPeerCount()).thenReturn(2);
    peerConnectedSubscriber.onConnected(peer1);

    // Should not disconnect static peers
    assertThat(peer2.isConnected()).isTrue();
    assertThat(peer1.isConnected()).isTrue();
  }

  @Test
  public void shouldConnectPeersThatPassPeerFilter() {
    final ConnectionManager manager = createManager();
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    when(network.connect(PEER1)).thenReturn(SafeFuture.completedFuture(peer1));
    when(network.connect(PEER2)).thenReturn(SafeFuture.completedFuture(peer2));
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2));

    manager.start().join();

    verify(network).connect(PEER1);
    verify(network).connect(PEER2);
  }

  @Test
  public void shouldNotConnectPeersThatDontPassPeerFilter() {
    final ConnectionManager manager = createManager();
    manager.addPeerPredicate((peer) -> !peer.equals(DISCOVERY_PEER2));
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    when(network.connect(PEER1)).thenReturn(SafeFuture.completedFuture(peer1));
    when(network.connect(PEER2)).thenReturn(SafeFuture.completedFuture(peer2));
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2));

    manager.start().join();

    verify(network).connect(PEER1);
    verify(network, never()).connect(PEER2);
  }

  @Test
  public void shouldApplyMultiplePeerPredicates() {
    final ConnectionManager manager = createManager();
    manager.addPeerPredicate((peer) -> !peer.equals(DISCOVERY_PEER2));
    manager.addPeerPredicate((peer) -> !peer.equals(DISCOVERY_PEER1));
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    when(network.connect(PEER1)).thenReturn(SafeFuture.completedFuture(peer1));
    when(network.connect(PEER2)).thenReturn(SafeFuture.completedFuture(peer2));
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2));

    manager.start().join();

    verify(network, never()).connect(PEER1);
    verify(network, never()).connect(PEER2);
  }

  private PeerConnectedSubscriber<Peer> getPeerConnectedSubscriber() {
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<PeerConnectedSubscriber<Peer>> captor =
        ArgumentCaptor.forClass(PeerConnectedSubscriber.class);
    verify(network).subscribeConnect(captor.capture());
    return captor.getValue();
  }

  private ConnectionManager createManager(final PeerAddress... peers) {
    return createManager(new TargetPeerRange(5, 10), peers);
  }

  private ConnectionManager createManager(
      final TargetPeerRange targetPeerCount, final PeerAddress... peers) {
    return new ConnectionManager(
        discoveryService,
        reputationManager,
        asyncRunner,
        network,
        Arrays.asList(peers),
        targetPeerCount);
  }
}
