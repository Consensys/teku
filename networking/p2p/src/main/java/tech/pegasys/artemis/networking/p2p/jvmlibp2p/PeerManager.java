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

import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionHandler;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.network.NetworkImpl;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMessageHandler;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMethods;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods.BeaconBlocksByRootMessageHandler;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods.GoodbyeMessageHandler;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods.StatusMessageHandler;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.pantheon.metrics.MetricsSystem;

public class PeerManager implements ConnectionHandler, PeerLookup {
  private static final ALogger STDOUT = new ALogger("stdout");
  private static final Logger LOG = LogManager.getLogger();

  private final ScheduledExecutorService scheduler;
  private static final long RECONNECT_TIMEOUT = 5000;
  private final StatusMessageFactory statusMessageFactory;

  private ConcurrentHashMap<PeerId, Peer> connectedPeerMap = new ConcurrentHashMap<>();

  private final RpcMethods rpcMethods;

  public PeerManager(
      final ScheduledExecutorService scheduler,
      final ChainStorageClient chainStorageClient,
      final MetricsSystem metricsSystem) {
    this.scheduler = scheduler;
    statusMessageFactory = new StatusMessageFactory(chainStorageClient);
    this.rpcMethods =
        new RpcMethods(
            this,
            new StatusMessageHandler(statusMessageFactory),
            new GoodbyeMessageHandler(metricsSystem),
            new BeaconBlocksByRootMessageHandler(chainStorageClient));
  }

  @Override
  public void handleConnection(@NotNull final Connection connection) {
    Peer peer = new Peer(connection, rpcMethods, statusMessageFactory);
    onConnectedPeer(peer);
    connection.closeFuture().thenRun(() -> onDisconnectedPeer(peer));

    if (connection.isInitiator()) {
      peer.sendStatus();
    }
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

  @Override
  public Peer getPeer(Connection conn) {
    return connectedPeerMap.get(conn.getSecureSession().getRemoteId());
  }

  public Optional<Peer> getAvailablePeer(PeerId peerId) {
    return Optional.ofNullable(connectedPeerMap.get(peerId)).filter(Peer::hasStatus);
  }

  private void onConnectedPeer(Peer peer) {
    final boolean wasAdded = connectedPeerMap.putIfAbsent(peer.getPeerId(), peer) == null;
    if (wasAdded) {
      STDOUT.log(Level.DEBUG, "onConnectedPeer() " + peer.getPeerId());
    }
  }

  private void onDisconnectedPeer(Peer peer) {
    if (connectedPeerMap.remove(peer.getPeerId()) != null) {
      STDOUT.log(Level.DEBUG, "Peer disconnected: " + peer.getPeerId());
    }
  }

  public Collection<RpcMessageHandler<?, ?>> getRpcMessageHandlers() {
    return rpcMethods.all();
  }

  public int getAvailablePeerCount() {
    return (int) connectedPeerMap.values().stream().filter(Peer::hasStatus).count();
  }

  public List<String> getPeerIds() {
    return connectedPeerMap.keySet().stream().map(PeerId::toBase58).collect(Collectors.toList());
  }
}
