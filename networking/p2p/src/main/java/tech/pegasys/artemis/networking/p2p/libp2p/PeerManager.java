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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionHandler;
import io.libp2p.core.Network;
import io.libp2p.core.multiformats.Multiaddr;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
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
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.events.Subscribers;

public class PeerManager implements ConnectionHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<RpcMethod, RpcHandler> rpcHandlers;

  private ConcurrentHashMap<NodeId, Peer> connectedPeerMap = new ConcurrentHashMap<>();
  private final List<PeerHandler> peerHandlers;

  private final Subscribers<PeerConnectedSubscriber<Peer>> connectSubscribers =
      Subscribers.create(true);

  public PeerManager(
      final MetricsSystem metricsSystem,
      final List<PeerHandler> peerHandlers,
      final Map<RpcMethod, RpcHandler> rpcHandlers) {
    this.peerHandlers = peerHandlers;
    this.rpcHandlers = rpcHandlers;
    // TODO - add metrics
  }

  @Override
  public void handleConnection(@NotNull final Connection connection) {
    Peer peer = new LibP2PPeer(connection, rpcHandlers);
    onConnectedPeer(peer);
  }

  public long subscribeConnect(final PeerConnectedSubscriber<Peer> subscriber) {
    return connectSubscribers.subscribe(subscriber);
  }

  public void unsubscribeConnect(final long subscriptionId) {
    connectSubscribers.unsubscribe(subscriptionId);
  }

  public SafeFuture<Peer> connect(final Multiaddr peer, final Network network) {
    LOG.debug("Connecting to {}", peer);
    return SafeFuture.of(network.connect(peer))
        .thenApply(
            connection -> {
              final LibP2PNodeId nodeId =
                  new LibP2PNodeId(connection.secureSession().getRemoteId());
              final Peer connectedPeer = connectedPeerMap.get(nodeId);
              if (connectedPeer == null) {
                if (connection.closeFuture().isDone()) {
                  // Connection has been immediately closed and the peer already removed
                  // Since the connection is closed anyway, we can create a new peer to wrap it.
                  return new LibP2PPeer(connection, rpcHandlers);
                } else {
                  // Theoretically this should never happen because removing from the map is done
                  // by the close future completing, but make a loud noise just in case.
                  throw new IllegalStateException(
                      "No peer registered for established connection to " + nodeId);
                }
              }
              return connectedPeer;
            })
        .exceptionallyCompose(this::handleConcurrentConnectionInitiation);
  }

  private CompletionStage<Peer> handleConcurrentConnectionInitiation(final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    return rootCause instanceof PeerAlreadyConnectedException
        ? SafeFuture.completedFuture(((PeerAlreadyConnectedException) rootCause).getPeer())
        : SafeFuture.failedFuture(error);
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
      peer.subscribeDisconnect(() -> onDisconnectedPeer(peer));
    } else {
      LOG.trace("Disconnecting duplicate connection to {}", peer::getId);
      throw new PeerAlreadyConnectedException(peer);
    }
  }

  @VisibleForTesting
  void onDisconnectedPeer(Peer peer) {
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

  /**
   * Indicates that two connections to the same PeerID were incorrectly established.
   *
   * <p>LibP2P usually detects attempts to establish multiple connections at the same time, but if
   * we have incoming and outgoing connections simultaneously to the same peer, sometimes it slips
   * through. In that case this exception is thrown so that the new connection is terminated before
   * handshakes complete and we are able to identify the situation and return the existing peer.
   */
  private static class PeerAlreadyConnectedException extends RuntimeException {
    private final Peer peer;

    public PeerAlreadyConnectedException(final Peer peer) {
      super("Already connected to peer " + peer.getId().toBase58());
      this.peer = peer;
    }

    public Peer getPeer() {
      return peer;
    }
  }
}
