/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.networking.p2p.connection;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.network.p2p.peer.StubPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;

class ConnectionManagerTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalAltair();
  private static final SchemaDefinitionsSupplier SCHEMA_DEFINITIONS_SUPPLIER =
      SPEC::getGenesisSchemaDefinitions;
  private static final Optional<EnrForkId> ENR_FORK_ID = Optional.empty();
  private static final PeerAddress PEER1 = new PeerAddress(new MockNodeId(1));
  private static final PeerAddress PEER2 = new PeerAddress(new MockNodeId(2));
  private static final PeerAddress PEER3 = new PeerAddress(new MockNodeId(3));
  private static final DiscoveryPeer DISCOVERY_PEER1 = createDiscoveryPeer(PEER1);
  private static final DiscoveryPeer DISCOVERY_PEER2 = createDiscoveryPeer(PEER2);

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> network = mock(P2PNetwork.class);

  private final DiscoveryService discoveryService = mock(DiscoveryService.class);
  private final PeerSelectionStrategy peerSelectionStrategy = mock(PeerSelectionStrategy.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);

  @BeforeEach
  public void setUp() {
    when(discoveryService.searchForPeers()).thenReturn(SafeFuture.completedFuture(emptyList()));
    when(peerSelectionStrategy.selectPeersToConnect(eq(network), any(), any()))
        .thenAnswer(
            invocation -> {
              final Supplier<? extends Collection<DiscoveryPeer>> candidateSupplier =
                  invocation.getArgument(2);
              return candidateSupplier.get().stream()
                  .map(peer -> new PeerAddress(new MockNodeId(peer.getPublicKey())))
                  .collect(toList());
            });
  }

  @Test
  public void shouldConnectToStaticPeersOnStart() {
    final ConnectionManager manager = createManager(PEER1, PEER2);
    when(network.connect(any(PeerAddress.class))).thenReturn(SafeFuture.completedFuture(null));
    assertThat(manager.start()).isCompleted();

    verify(network).connect(PEER1);
    verify(network).connect(PEER2);
  }

  @Test
  public void shouldRetryConnectionToStaticPeerAfterDelayWhenInitialAttemptFails() {
    final ConnectionManager manager = createManager(PEER1);

    final SafeFuture<Peer> connectionFuture1 = new SafeFuture<>();
    final SafeFuture<Peer> connectionFuture2 = new SafeFuture<>();
    when(network.connect(PEER1)).thenReturn(connectionFuture1).thenReturn(connectionFuture2);
    assertThat(manager.start()).isCompleted();
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

    peer.disconnectImmediately(Optional.empty(), true);
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
    peer.disconnectImmediately(Optional.empty(), true);

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
    peer.disconnectImmediately(Optional.empty(), true);

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
    advanceTimeABit();

    verify(network).connect(PEER1);
    verify(network).connect(PEER2);
  }

  @Test
  @Disabled("Shouldn't work, disabled until clarifying")
  public void shouldNotRetryConnectionsToDiscoveredPeersOnFailure() {
    final ConnectionManager manager = createManager();
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.of(DISCOVERY_PEER1))
        .thenReturn(Stream.of(DISCOVERY_PEER1));
    final SafeFuture<Peer> connectionFuture = new SafeFuture<>();
    when(network.connect(any(PeerAddress.class))).thenReturn(connectionFuture);

    manager.start().join();
    advanceTimeABit();
    verify(network).connect(PEER1);

    connectionFuture.completeExceptionally(new RuntimeException("Failed"));

    advanceTimeByInitialInterval();
    verify(network, times(1)).connect(PEER1); // No further attempts to connect
  }

  @Test
  @Disabled("Shouldn't work, disabled until clarifying")
  public void shouldNotRetryConnectionsToDiscoveredPeersOnDisconnect() {
    final ConnectionManager manager = createManager();
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.of(DISCOVERY_PEER1))
        .thenReturn(Stream.of(DISCOVERY_PEER1));
    final SafeFuture<Peer> connectionFuture = new SafeFuture<>();
    when(network.connect(any(PeerAddress.class))).thenReturn(connectionFuture);

    manager.start().join();
    advanceTimeABit();
    verify(network).connect(PEER1);

    final StubPeer peer = new StubPeer(new MockNodeId(DISCOVERY_PEER1.getPublicKey()));
    connectionFuture.complete(peer);

    peer.disconnectImmediately(Optional.empty(), true);
    advanceTimeByInitialInterval();
    verify(network, times(1)).connect(PEER1); // No further attempts to connect
  }

  @Test
  public void shouldPeriodicallyTriggerNewDiscoverySearch() {
    final SafeFuture<Collection<DiscoveryPeer>> search1 = new SafeFuture<>();
    final SafeFuture<Collection<DiscoveryPeer>> search2 = new SafeFuture<>();
    when(discoveryService.searchForPeers()).thenReturn(search1).thenReturn(search2);

    final ConnectionManager manager = createManager();
    SafeFuture<?> startFuture = manager.start();
    search1.complete(emptyList());
    advanceTimeABit();
    startFuture.join();

    verify(discoveryService, times(1)).searchForPeers();

    advanceTimeABit();
    verify(discoveryService, times(1)).searchForPeers(); // Shouldn't immediately search again

    search2.complete(emptyList());
    advanceTimeByInitialInterval();
    verify(discoveryService, times(2)).searchForPeers(); // But should after a delay
  }

  @Test
  public void shouldTriggerNewDiscoverySearchAfterFailure() {
    final SafeFuture<Collection<DiscoveryPeer>> search1 = new SafeFuture<>();
    final SafeFuture<Collection<DiscoveryPeer>> search2 = new SafeFuture<>();
    when(discoveryService.searchForPeers()).thenReturn(search1).thenReturn(search2);

    final ConnectionManager manager = createManager();
    SafeFuture<?> startFuture = manager.start();
    search1.completeExceptionally(new RuntimeException("Nope"));
    advanceTimeABit();
    startFuture.join();

    verify(discoveryService, times(1)).searchForPeers();

    advanceTimeABit();
    verify(discoveryService, times(1)).searchForPeers(); // Shouldn't immediately search again

    search2.complete(emptyList());
    advanceTimeByInitialInterval();
    verify(discoveryService, times(2)).searchForPeers(); // But should after a delay
  }

  @Test
  public void shouldStopTriggeringDiscoverySearchesWhenStopped() {
    final SafeFuture<Collection<DiscoveryPeer>> search1 = new SafeFuture<>();
    final SafeFuture<Collection<DiscoveryPeer>> search2 = new SafeFuture<>();
    when(discoveryService.searchForPeers()).thenReturn(search1).thenReturn(search2);
    final ConnectionManager manager = createManager();

    SafeFuture<?> startFuture = manager.start();
    search1.complete(emptyList());
    advanceTimeABit();
    verify(discoveryService).searchForPeers();

    SafeFuture<?> stopFuture = manager.stop();

    startFuture.join();
    stopFuture.join();

    advanceTimeByInitialInterval();
    verify(discoveryService, times(1)).searchForPeers(); // Shouldn't search again
  }

  @Test
  public void shouldConnectToKnownPeersWhenDiscoverySearchCompletes() {
    final SafeFuture<Collection<DiscoveryPeer>> search1 = new SafeFuture<>();
    when(network.connect(any(PeerAddress.class))).thenReturn(new SafeFuture<>());
    when(discoveryService.searchForPeers()).thenReturn(search1);
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.empty()) // No known peers at startup
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2)); // Search found some new peers
    final ConnectionManager manager = createManager();

    SafeFuture<?> startFuture = manager.start();
    search1.complete(emptyList());
    startFuture.join();
    // First search is empty, second is successful, so we need to capture 2 searches
    advanceTimeABit();
    advanceTimeByInitialInterval();
    verify(discoveryService, times(2)).searchForPeers();

    verify(network).connect(PEER1);
    verify(network).connect(PEER2);
  }

  @Test
  public void shouldConsiderNewlyFoundPeersForConnectionWhenDiscoverySearchCompletes() {
    final SafeFuture<Collection<DiscoveryPeer>> search1 = new SafeFuture<>();
    when(network.connect(any(PeerAddress.class))).thenReturn(new SafeFuture<>());
    when(discoveryService.searchForPeers()).thenReturn(search1);
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.empty()) // No known peers at startup
        .thenReturn(Stream.of(DISCOVERY_PEER2)); // Search found some new peers
    final ConnectionManager manager = createManager();

    SafeFuture<?> startFuture = manager.start();
    search1.complete(List.of(DISCOVERY_PEER1));
    advanceTimeABit();
    startFuture.join();
    verify(discoveryService).searchForPeers();
    verify(network).connect(PEER1);

    advanceTimeByInitialInterval();
    verify(network).connect(PEER2);
  }

  @Test
  public void shouldUsePeerSelectionStrategyToSelectPeersToConnectTo() {
    when(network.connect(any(PeerAddress.class))).thenReturn(new SafeFuture<>());
    when(discoveryService.streamKnownPeers()).thenReturn(Stream.empty());
    when(peerSelectionStrategy.selectPeersToConnect(eq(network), any(), any()))
        .thenReturn(List.of(PEER1, PEER3));

    final ConnectionManager manager = createManager();
    manager.start().join();
    advanceTimeABit();

    verify(network).connect(PEER1);
    verify(network).connect(PEER3);
    // Only connected those two peers.
    verify(network, times(2)).connect(any());
  }

  @Test
  public void shouldUsePeerSelectionStrategyToSelectPeersToDisconnect() {
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    final ConnectionManager manager = createManager();
    when(network.connect(PEER1)).thenReturn(SafeFuture.completedFuture(peer1));
    when(network.connect(PEER2)).thenReturn(SafeFuture.completedFuture(peer2));
    manager.start().join();

    final PeerConnectedSubscriber<Peer> peerConnectedSubscriber = getPeerConnectedSubscriber();

    when(peerSelectionStrategy.selectPeersToDisconnect(eq(network), any()))
        .thenReturn(List.of(peer1));
    peerConnectedSubscriber.onConnected(peer1);

    assertThat(peer2.isConnected()).isTrue();
    assertThat(peer1.isConnected()).isFalse();
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
    advanceTimeABit();

    verify(network).connect(PEER1);
    verify(network).connect(PEER2);
  }

  @Test
  public void shouldNotConnectPeersThatDoNotPassPeerFilter() {
    final ConnectionManager manager = createManager();
    manager.addPeerPredicate((peer) -> !peer.equals(DISCOVERY_PEER2));
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    when(network.connect(PEER1)).thenReturn(SafeFuture.completedFuture(peer1));
    when(network.connect(PEER2)).thenReturn(SafeFuture.completedFuture(peer2));
    when(discoveryService.streamKnownPeers())
        .thenReturn(Stream.of(DISCOVERY_PEER1, DISCOVERY_PEER2));

    manager.start().join();
    advanceTimeABit();

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

  @Test
  public void shouldSwitchToLongDelayAfter6InitialSearches() {
    final ConnectionManager manager = createManager();
    SafeFuture<?> startFuture = manager.start();
    advanceTimeABit();
    startFuture.join();
    // Start search
    verify(discoveryService, times(1)).searchForPeers();

    advanceTimeByInitialInterval();
    verify(discoveryService, times(2)).searchForPeers();

    advanceTimeByInitialInterval();
    verify(discoveryService, times(3)).searchForPeers();

    advanceTimeByInitialInterval();
    verify(discoveryService, times(4)).searchForPeers();

    advanceTimeByInitialInterval();
    verify(discoveryService, times(5)).searchForPeers();

    advanceTimeByInitialInterval();
    verify(discoveryService, times(6)).searchForPeers();

    // Should switch to long delay after initial 1 + 5 rush searches
    advanceTimeByInitialInterval();
    verify(discoveryService, times(6)).searchForPeers();

    timeProvider.advanceTimeBySeconds(ConnectionManager.DISCOVERY_INTERVAL.getSeconds());
    asyncRunner.executeDueActionsRepeatedly();
    verify(discoveryService, times(7)).searchForPeers();

    timeProvider.advanceTimeBySeconds(ConnectionManager.DISCOVERY_INTERVAL.getSeconds());
    asyncRunner.executeDueActionsRepeatedly();
    verify(discoveryService, times(8)).searchForPeers();
  }

  private void advanceTimeABit() {
    timeProvider.advanceTimeByMillis(1);
    asyncRunner.executeDueActionsRepeatedly();
  }

  private void advanceTimeByInitialInterval() {
    timeProvider.advanceTimeBySeconds(ConnectionManager.STARTUP_DISCOVERY_INTERVAL.getSeconds());
    asyncRunner.executeDueActionsRepeatedly();
  }

  private PeerConnectedSubscriber<Peer> getPeerConnectedSubscriber() {
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<PeerConnectedSubscriber<Peer>> captor =
        ArgumentCaptor.forClass(PeerConnectedSubscriber.class);
    verify(network).subscribeConnect(captor.capture());
    return captor.getValue();
  }

  private ConnectionManager createManager(final PeerAddress... peers) {
    return new ConnectionManager(
        new NoOpMetricsSystem(),
        discoveryService,
        asyncRunner,
        network,
        peerSelectionStrategy,
        Arrays.asList(peers));
  }

  private static DiscoveryPeer createDiscoveryPeer(final PeerAddress peer, final int... subnetIds) {
    return createDiscoveryPeer(peer.getId().toBytes(), subnetIds);
  }

  private static DiscoveryPeer createDiscoveryPeer(final Bytes peerId, final int... subnetIds) {
    return new DiscoveryPeer(
        peerId,
        new InetSocketAddress(InetAddress.getLoopbackAddress(), peerId.trimLeadingZeros().toInt()),
        ENR_FORK_ID,
        SCHEMA_DEFINITIONS_SUPPLIER.getAttnetsENRFieldSchema().ofBits(subnetIds),
        SCHEMA_DEFINITIONS_SUPPLIER.getSyncnetsENRFieldSchema().getDefault());
  }
}
