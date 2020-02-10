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

import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.async.FutureUtil.ignoreFuture;

import com.google.common.annotations.VisibleForTesting;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionHandler;
import io.libp2p.core.Network;
import io.libp2p.core.multiformats.Multiaddr;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.artemis.networking.p2p.network.PeerHandler;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.artemis.networking.p2p.rpc.RpcMethod;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.events.Subscribers;

public class PeerManager implements ConnectionHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final AsyncRunner asynRunner;
  private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(30);
  private final Map<RpcMethod, RpcHandler> rpcHandlers;

  private ConcurrentHashMap<NodeId, Peer> connectedPeerMap = new ConcurrentHashMap<>();
  private final List<PeerHandler> peerHandlers;

  private final Subscribers<PeerConnectedSubscriber<Peer>> connectSubscribers =
      Subscribers.create(true);

  public PeerManager(
      final AsyncRunner asynRunner,
      final MetricsSystem metricsSystem,
      final List<PeerHandler> peerHandlers,
      final Map<RpcMethod, RpcHandler> rpcHandlers) {
    this.asynRunner = asynRunner;
    this.peerHandlers = peerHandlers;
    this.rpcHandlers = rpcHandlers;
    // TODO - add metrics
  }

  @Override
  public void handleConnection(@NotNull final Connection connection) {
    Peer peer = new LibP2PPeer(connection, rpcHandlers);
    onConnectedPeer(peer);
    SafeFuture.of(connection.closeFuture()).finish(() -> onDisconnectedPeer(peer));
  }

  public long subscribeConnect(final PeerConnectedSubscriber<Peer> subscriber) {
    return connectSubscribers.subscribe(subscriber);
  }

  public void unsubscribeConnect(final long subscriptionId) {
    connectSubscribers.unsubscribe(subscriptionId);
  }

  public SafeFuture<?> connect(final Multiaddr peer, final Network network) {
    STDOUT.log(Level.DEBUG, "Connecting to " + peer);
    final SafeFuture<Connection> initialConnectionFuture = SafeFuture.of(network.connect(peer));
    initialConnectionFuture
        .thenCompose(
            conn -> {
              LOG.debug("Connection to peer {} was successful", conn.secureSession().getRemoteId());
              return SafeFuture.of(conn.closeFuture());
            })
        .exceptionally(
            (err) -> {
              LOG.debug("Connection to {} failed: {}", peer, err);
              return null;
            })
        .thenAccept(
            (res) -> {
              LOG.debug(
                  "Connection to {} was closed. Will retry in {} sec",
                  peer,
                  RECONNECT_TIMEOUT.toSeconds());

              final SafeFuture<?> retryFuture =
                  asynRunner.runAfterDelay(
                      () -> connect(peer, network),
                      RECONNECT_TIMEOUT.toMillis(),
                      TimeUnit.MILLISECONDS);
              ignoreFuture(retryFuture);
            });
    return initialConnectionFuture;
  }

  public Optional<Peer> getPeer(NodeId id) {
    return Optional.ofNullable(connectedPeerMap.get(id));
  }

  @VisibleForTesting
  void onConnectedPeer(Peer peer) {
    final boolean wasAdded = connectedPeerMap.putIfAbsent(peer.getId(), peer) == null;
    if (wasAdded) {
      STDOUT.log(Level.DEBUG, "onConnectedPeer() " + peer.getId());
      peerHandlers.forEach(h -> h.onConnect(peer));
      connectSubscribers.forEach(c -> c.onConnected(peer));
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

  public int getPeerCount() {
    return connectedPeerMap.size();
  }
}
