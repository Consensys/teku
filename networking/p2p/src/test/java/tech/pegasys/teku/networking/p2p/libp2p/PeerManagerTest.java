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

package tech.pegasys.teku.networking.p2p.libp2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.libp2p.core.Connection;
import io.libp2p.core.Network;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.security.SecureChannel.Session;
import io.libp2p.crypto.keys.EcdsaKt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

public class PeerManagerTest {

  private final ReputationManager reputationManager = mock(ReputationManager.class);
  private final Network network = mock(Network.class);

  private final PeerManager peerManager =
      new PeerManager(
          new NoOpMetricsSystem(),
          reputationManager,
          Collections.emptyList(),
          Collections.emptyList(),
          peerId -> 0.0);

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
  public void shouldCreatePeerTypeMetrics() {
    final MetricsSystem metricsSystem = mock(MetricsSystem.class);
    final LabelledGauge gauge = mock(LabelledGauge.class);

    when(metricsSystem.createLabelledGauge(
            eq(TekuMetricCategory.LIBP2P), eq("connected_peers_current"), any(), any()))
        .thenReturn(gauge);
    new PeerManager(
        metricsSystem,
        reputationManager,
        Collections.emptyList(),
        Collections.emptyList(),
        peerId -> 0.0);

    for (PeerClientType type : PeerClientType.values()) {
      verify(gauge).labels(any(), eq(type.getDisplayName()));
    }
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

  @Test
  public void subscribeConnect_shouldRejectConnectionThatAlreadyExists() {
    final List<Peer> connectedPeers = new ArrayList<>();
    peerManager.subscribeConnect(connectedPeers::add);

    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(new MockNodeId(1));
    peerManager.onConnectedPeer(peer);
    assertThat(connectedPeers).containsExactly(peer);

    Assertions.assertThrows(
        PeerAlreadyConnectedException.class, () -> peerManager.onConnectedPeer(peer));

    assertThat(connectedPeers).containsExactly(peer);
  }

  @Test
  public void shouldReportFailedConnectionsToReputationManager() {
    final Multiaddr multiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/9000");
    final MultiaddrPeerAddress peerAddress = new MultiaddrPeerAddress(new MockNodeId(1), multiaddr);
    when(network.connect(multiaddr))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Nope")));

    final SafeFuture<Peer> result = peerManager.connect(peerAddress, network);
    assertThat(result).isCompletedExceptionally();

    verify(reputationManager).reportInitiatedConnectionFailed(peerAddress);
    verify(reputationManager, never()).reportInitiatedConnectionSuccessful(peerAddress);
  }

  @Test
  public void shouldReportSuccessfulConnectionsToReputationManager() {
    final Connection connection = mock(Connection.class);
    final Session secureSession =
        new Session(PeerId.random(), PeerId.random(), EcdsaKt.generateEcdsaKeyPair().component2());
    when(connection.secureSession()).thenReturn(secureSession);
    when(connection.closeFuture()).thenReturn(new SafeFuture<>());
    final Multiaddr multiaddr = Multiaddr.fromString("/ip4/127.0.0.1/tcp/9000");
    final MultiaddrPeerAddress peerAddress = new MultiaddrPeerAddress(new MockNodeId(1), multiaddr);
    final SafeFuture<Connection> connectionFuture = new SafeFuture<>();
    when(network.connect(multiaddr)).thenReturn(connectionFuture);

    final SafeFuture<Peer> result = peerManager.connect(peerAddress, network);
    peerManager.handleConnection(connection);
    connectionFuture.complete(connection);
    assertThat(result).isCompleted();

    verify(reputationManager).reportInitiatedConnectionSuccessful(peerAddress);
    verify(reputationManager, never()).reportInitiatedConnectionFailed(peerAddress);
  }
}
