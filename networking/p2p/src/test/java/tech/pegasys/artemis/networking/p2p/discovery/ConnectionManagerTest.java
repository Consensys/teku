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

package tech.pegasys.artemis.networking.p2p.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.network.p2p.peer.StubPeer;
import tech.pegasys.artemis.networking.p2p.connection.ConnectionManager;
import tech.pegasys.artemis.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

class ConnectionManagerTest {
  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> network = mock(P2PNetwork.class);

  private final DiscoveryService discoveryService = mock(DiscoveryService.class);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @BeforeEach
  public void setUp() {
    when(discoveryService.searchForPeers()).thenReturn(new SafeFuture<>());
  }

  @Test
  public void shouldConnectToStaticPeersOnStart() {
    final ConnectionManager manager = createManager("peer1", "peer2");
    when(network.connect(anyString())).thenReturn(SafeFuture.completedFuture(null));
    manager.start().join();

    verify(network).connect("peer1");
    verify(network).connect("peer2");
  }

  @Test
  public void shouldRetryConnectionToStaticPeerAfterDelayWhenInitialAttemptFails() {
    final ConnectionManager manager = createManager("peer1");

    final SafeFuture<Peer> connectionFuture1 = new SafeFuture<>();
    final SafeFuture<Peer> connectionFuture2 = new SafeFuture<>();
    when(network.connect("peer1")).thenReturn(connectionFuture1).thenReturn(connectionFuture2);
    manager.start().join();
    verify(network).connect("peer1");

    connectionFuture1.completeExceptionally(new RuntimeException("Nope"));

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(network, times(2)).connect("peer1");
  }

  @Test
  public void shouldReconnectWhenPersistentPeerDisconnects() {
    final ConnectionManager manager = createManager("peer1");

    final MockNodeId peerId = new MockNodeId();
    final StubPeer peer = new StubPeer(peerId);
    when(network.connect("peer1"))
        .thenReturn(SafeFuture.completedFuture(peer))
        .thenReturn(new SafeFuture<>());
    manager.start().join();
    verify(network).connect("peer1");
    peer.disconnect();

    verify(network, times(2)).connect("peer1");
  }

  @Test
  public void shouldAddNewPeerToStaticList() {
    final ConnectionManager manager = createManager();

    final MockNodeId peerId = new MockNodeId();
    final StubPeer peer = new StubPeer(peerId);
    when(network.connect("peer1"))
        .thenReturn(SafeFuture.completedFuture(peer))
        .thenReturn(new SafeFuture<>());
    manager.start().join();

    manager.addStaticPeer("peer1");
    verify(network).connect("peer1");
    peer.disconnect();

    verify(network, times(2)).connect("peer1");
  }

  @Test
  public void shouldNotAddDuplicatePeerToStaticList() {
    final ConnectionManager manager = createManager("peer1");

    final MockNodeId peerId = new MockNodeId();
    final StubPeer peer = new StubPeer(peerId);
    when(network.connect("peer1"))
        .thenReturn(SafeFuture.completedFuture(peer))
        .thenReturn(new SafeFuture<>());
    manager.start().join();

    verify(network).connect("peer1");

    manager.addStaticPeer("peer1");
    // Doesn't attempt to connect a second time.
    verify(network, times(1)).connect("peer1");
  }

  @Test
  public void shouldConnectToKnownPeersWhenStarted() {
    final ConnectionManager manager = createManager();
    final DiscoveryPeer discoveryPeer1 = new DiscoveryPeer(Bytes.of(1), new InetSocketAddress(1));
    final DiscoveryPeer discoveryPeer2 = new DiscoveryPeer(Bytes.of(2), new InetSocketAddress(2));
    when(discoveryService.streamKnownPeers()).thenReturn(Stream.of(discoveryPeer1, discoveryPeer2));
    when(network.connect(any(DiscoveryPeer.class))).thenReturn(new SafeFuture<>());

    manager.start().join();

    verify(network).connect(discoveryPeer1);
    verify(network).connect(discoveryPeer2);
  }

  @Test
  public void shouldNotRetryConnectionsToDiscoveredPeersOnFailure() {
    final ConnectionManager manager = createManager();
    final DiscoveryPeer discoveryPeer = new DiscoveryPeer(Bytes.of(1), new InetSocketAddress(1));
    when(discoveryService.streamKnownPeers()).thenReturn(Stream.of(discoveryPeer));
    final SafeFuture<Peer> connectionFuture = new SafeFuture<>();
    when(network.connect(any(DiscoveryPeer.class))).thenReturn(connectionFuture);

    manager.start().join();
    verify(network).connect(discoveryPeer);

    connectionFuture.completeExceptionally(new RuntimeException("Failed"));

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verify(network, times(1)).connect(discoveryPeer); // No further attempts to connect
  }

  @Test
  public void shouldNotRetryConnectionsToDiscoveredPeersOnDisconnect() {
    final ConnectionManager manager = createManager();
    final DiscoveryPeer discoveryPeer = new DiscoveryPeer(Bytes.of(1), new InetSocketAddress(1));
    when(discoveryService.streamKnownPeers()).thenReturn(Stream.of(discoveryPeer));
    final SafeFuture<Peer> connectionFuture = new SafeFuture<>();
    when(network.connect(any(DiscoveryPeer.class))).thenReturn(connectionFuture);

    manager.start().join();
    verify(network).connect(discoveryPeer);

    final StubPeer peer = new StubPeer(new MockNodeId(discoveryPeer.getPublicKey()));
    connectionFuture.complete(peer);

    peer.disconnect();
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verify(network, times(1)).connect(discoveryPeer); // No further attempts to connect
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
    final DiscoveryPeer discoveryPeer1 = new DiscoveryPeer(Bytes.of(1), new InetSocketAddress(1));
    final DiscoveryPeer discoveryPeer2 = new DiscoveryPeer(Bytes.of(2), new InetSocketAddress(2));
    final SafeFuture<Void> search1 = new SafeFuture<>();
    when(network.connect(any(DiscoveryPeer.class))).thenReturn(new SafeFuture<>());
    when(discoveryService.searchForPeers()).thenReturn(search1);
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.empty()) // No known peers at startup
        .thenReturn(Stream.of(discoveryPeer1, discoveryPeer2)); // Search found some new peers
    final ConnectionManager manager = createManager();

    manager.start().join();
    verify(discoveryService).searchForPeers();

    search1.complete(null);

    verify(network).connect(discoveryPeer1);
    verify(network).connect(discoveryPeer2);
  }

  @Test
  public void shouldLimitNumberOfNewConnectionsToKnownPeersOnStartup() {
    final ConnectionManager manager = createManager(new TargetPeerRange(1, 2));
    final DiscoveryPeer discoveryPeer1 = new DiscoveryPeer(Bytes.of(1), new InetSocketAddress(1));
    final DiscoveryPeer discoveryPeer2 = new DiscoveryPeer(Bytes.of(2), new InetSocketAddress(2));
    final DiscoveryPeer discoveryPeer3 = new DiscoveryPeer(Bytes.of(3), new InetSocketAddress(3));
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.of(discoveryPeer1, discoveryPeer2, discoveryPeer3));
    when(network.connect(any(DiscoveryPeer.class))).thenReturn(new SafeFuture<>());

    manager.start().join();

    verify(network).connect(discoveryPeer1);
    verify(network).connect(discoveryPeer2);
    verify(network, never()).connect(discoveryPeer3);
  }

  @Test
  public void shouldLimitNumberOfNewConnectionsToKnownPeersOnRetry() {
    final DiscoveryPeer discoveryPeer1 = new DiscoveryPeer(Bytes.of(1), new InetSocketAddress(1));
    final DiscoveryPeer discoveryPeer2 = new DiscoveryPeer(Bytes.of(2), new InetSocketAddress(2));
    final DiscoveryPeer discoveryPeer3 = new DiscoveryPeer(Bytes.of(3), new InetSocketAddress(3));
    final DiscoveryPeer discoveryPeer4 = new DiscoveryPeer(Bytes.of(4), new InetSocketAddress(4));
    final SafeFuture<Void> search1 = new SafeFuture<>();
    when(network.connect(any(DiscoveryPeer.class))).thenReturn(new SafeFuture<>());
    when(discoveryService.searchForPeers()).thenReturn(search1);
    when(discoveryService.streamKnownPeers())
        // At startup
        .thenReturn(Stream.of(discoveryPeer1, discoveryPeer2, discoveryPeer3))
        // After search
        .thenReturn(Stream.of(discoveryPeer1, discoveryPeer2, discoveryPeer3, discoveryPeer4));

    final ConnectionManager manager = createManager(new TargetPeerRange(2, 3));

    when(network.getPeerCount()).thenReturn(0);
    manager.start().join();
    verify(discoveryService).searchForPeers();
    verify(network).connect(discoveryPeer1);
    verify(network).connect(discoveryPeer2);
    verify(network).connect(discoveryPeer3);
    verify(network, never()).connect(discoveryPeer4);

    // Only peer 2 actually connected, so should try to connect 2 more nodes
    when(network.getPeerCount()).thenReturn(1);
    when(network.isConnected(discoveryPeer2)).thenReturn(true);
    search1.complete(null);

    verify(network, times(2)).connect(discoveryPeer1);
    verify(network, times(1)).connect(discoveryPeer2); // Not retried
    verify(network, times(2)).connect(discoveryPeer3); // Retried
    verify(network, never()).connect(discoveryPeer4); // Still not required
  }

  private ConnectionManager createManager(final String... peers) {
    return createManager(new TargetPeerRange(5, 10), peers);
  }

  private ConnectionManager createManager(
      final TargetPeerRange targetPeerCount, final String... peers) {
    return new ConnectionManager(
        discoveryService, asyncRunner, network, Arrays.asList(peers), targetPeerCount);
  }
}
