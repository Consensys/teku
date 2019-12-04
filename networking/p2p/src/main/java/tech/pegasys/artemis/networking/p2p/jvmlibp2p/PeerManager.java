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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionHandler;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.network.NetworkImpl;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class PeerManager implements ConnectionHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final ScheduledExecutorService scheduler;
  private static final long RECONNECT_TIMEOUT = 5000;

  private ConcurrentHashMap<NodeId, Peer> connectedPeerMap = new ConcurrentHashMap<>();
  private final List<PeerHandler> peerHandlers;

  public PeerManager(
      final ScheduledExecutorService scheduler,
      final ChainStorageClient chainStorageClient,
      final MetricsSystem metricsSystem,
      final List<PeerHandler> peerHandlers) {
    this.scheduler = scheduler;
    this.peerHandlers = peerHandlers;
  }

  @Override
  public void handleConnection(@NotNull final Connection connection) {
    Peer peer = new LibP2PPeer(connection);
    onConnectedPeer(peer);
    connection.closeFuture().thenRun(() -> onDisconnectedPeer(peer));
  }

  public CompletableFuture<?> connect(final Multiaddr peer, final NetworkImpl network) {
    STDOUT.log(Level.DEBUG, "Connecting to " + peer);
    return network
        .connect(peer)
        .whenComplete(
            (conn, throwable) -> {
              if (throwable != null) {
                STDOUT.log(
                    Level.DEBUG,
                    "Connection to " + peer + " failed. Will retry shortly: " + throwable);
                scheduler.schedule(
                    () -> connect(peer, network), RECONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
              } else {
                STDOUT.log(
                    Level.DEBUG,
                    "Connection to peer: "
                        + conn.getSecureSession().getRemoteId()
                        + " was successful");
                conn.closeFuture()
                    .thenAccept(
                        ignore -> {
                          LOG.debug("Connection to {} closed. Will retry shortly", peer);
                          scheduler.schedule(
                              () -> connect(peer, network),
                              RECONNECT_TIMEOUT,
                              TimeUnit.MILLISECONDS);
                        });
              }
            });
  }

  public Peer getPeer(Connection conn) {
    final NodeId nodeId = new LibP2PNodeId(conn.getSecureSession().getRemoteId());
    return connectedPeerMap.get(nodeId);
  }

  public Optional<Peer> getPeer(NodeId id) {
    return Optional.ofNullable(connectedPeerMap.get(id));
  }

  private void onConnectedPeer(Peer peer) {
    final boolean wasAdded = connectedPeerMap.putIfAbsent(peer.getId(), peer) == null;
    if (wasAdded) {
      STDOUT.log(Level.DEBUG, "onConnectedPeer() " + peer.getId());
      peerHandlers.forEach(h -> h.onConnect(peer));
    }
  }

  private void onDisconnectedPeer(Peer peer) {
    if (connectedPeerMap.remove(peer.getId()) != null) {
      STDOUT.log(Level.DEBUG, "Peer disconnected: " + peer.getId());
      peerHandlers.forEach(h -> h.onDisconnect(peer));
    }
  }

  public Stream<Peer> streamPeers() {
    return connectedPeerMap.values().stream();
  }
}
