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
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.network.NetworkImpl;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.PeerHandler;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.artemis.networking.p2p.rpc.RpcMethod;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.events.Subscribers;

public class PeerManager implements ConnectionHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final NetworkConfig networkConfig;
  private final ScheduledExecutorService scheduler;
  private static final long RECONNECT_TIMEOUT = 5000;
  private final Map<RpcMethod, RpcHandler> rpcHandlers;

  private ConcurrentHashMap<NodeId, Peer> connectedPeerMap = new ConcurrentHashMap<>();
  private final List<PeerHandler> peerHandlers;

  private final Subscribers<PeerConnectedSubscriber<Peer>> connectSubscribers =
      Subscribers.create(true);

  public PeerManager(
      final NetworkConfig networkConfig,
      final ScheduledExecutorService scheduler,
      final MetricsSystem metricsSystem,
      final List<PeerHandler> peerHandlers,
      final Map<RpcMethod, RpcHandler> rpcHandlers) {
    this.networkConfig = networkConfig;
    this.scheduler = scheduler;
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

  public SafeFuture<?> connect(final Multiaddr peer, final NetworkImpl network) {
    STDOUT.log(Level.DEBUG, "On " + networkConfig.getNetworkInterface() + ":" + networkConfig.getListenPort() + "Connecting to " + peer);
    return SafeFuture.of(network.connect(peer))
        .whenComplete(
            (conn, throwable) -> {
              if (throwable != null) {
                STDOUT.log(
                    Level.DEBUG,
                    "Connection to " + peer + " failed. Will retry shortly: " + throwable);
                ignoreFuture(
                    scheduler.schedule(
                        () -> connect(peer, network).reportExceptions(),
                        RECONNECT_TIMEOUT,
                        TimeUnit.MILLISECONDS));
              } else {
                STDOUT.log(
                    Level.DEBUG,
                    "Connection to peer: "
                        + conn.getSecureSession().getRemoteId()
                        + " was successful");
                SafeFuture.of(conn.closeFuture())
                    .finish(
                        () -> {
                          LOG.debug("Connection to {} closed. Will retry shortly", peer);
                          ignoreFuture(
                              scheduler.schedule(
                                  () -> connect(peer, network).reportExceptions(),
                                  RECONNECT_TIMEOUT,
                                  TimeUnit.MILLISECONDS));
                        });
              }
            });
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
