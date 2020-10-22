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

import io.libp2p.core.Connection;
import io.libp2p.core.PeerId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.DisconnectRequestHandler;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedSubscriber;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;

public class LibP2PPeer implements Peer {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<RpcMethod, RpcHandler> rpcHandlers;
  private final ReputationManager reputationManager;
  private final Connection connection;
  private final AtomicBoolean connected = new AtomicBoolean(true);
  private final MultiaddrPeerAddress peerAddress;

  private volatile Optional<DisconnectReason> disconnectReason = Optional.empty();
  private volatile boolean disconnectLocallyInitiated = false;
  private volatile DisconnectRequestHandler disconnectRequestHandler =
      reason -> {
        disconnectImmediately(Optional.of(reason), true);
        return SafeFuture.COMPLETE;
      };

  public LibP2PPeer(
      final Connection connection,
      final Map<RpcMethod, RpcHandler> rpcHandlers,
      final ReputationManager reputationManager) {
    this.connection = connection;
    this.rpcHandlers = rpcHandlers;
    this.reputationManager = reputationManager;

    final PeerId peerId = connection.secureSession().getRemoteId();
    final NodeId nodeId = new LibP2PNodeId(peerId);
    peerAddress = new MultiaddrPeerAddress(nodeId, connection.remoteAddress());
    SafeFuture.of(connection.closeFuture())
        .finish(
            this::handleConnectionClosed,
            error ->
                LOG.trace(
                    "Peer {} connection close future completed exceptionally", peerId, error));
  }

  @Override
  public PeerAddress getAddress() {
    return peerAddress;
  }

  @Override
  public boolean isConnected() {
    return connected.get();
  }

  @Override
  public void disconnectImmediately(
      final Optional<DisconnectReason> reason, final boolean locallyInitiated) {
    connected.set(false);
    disconnectReason = reason;
    disconnectLocallyInitiated = locallyInitiated;
    SafeFuture.of(connection.close())
        .finish(
            () -> LOG.trace("Disconnected from {}", getId()),
            error -> LOG.warn("Failed to disconnect from peer {}", getId(), error));
  }

  @Override
  public SafeFuture<Void> disconnectCleanly(final DisconnectReason reason) {
    connected.set(false);
    disconnectReason = Optional.of(reason);
    disconnectLocallyInitiated = true;
    return disconnectRequestHandler
        .requestDisconnect(reason)
        .handle(
            (__, error) -> {
              if (error != null) {
                LOG.debug("Failed to disconnect from " + getId() + " cleanly.", error);
              }
              disconnectImmediately(Optional.of(reason), true);
              return null;
            });
  }

  @Override
  public void setDisconnectRequestHandler(final DisconnectRequestHandler handler) {
    this.disconnectRequestHandler = handler;
  }

  @Override
  public void subscribeDisconnect(final PeerDisconnectedSubscriber subscriber) {
    SafeFuture.of(connection.closeFuture())
        .always(() -> subscriber.onDisconnected(disconnectReason, disconnectLocallyInitiated));
  }

  @Override
  public SafeFuture<RpcStream> sendRequest(
      RpcMethod rpcMethod, final Bytes initialPayload, final RpcRequestHandler handler) {
    RpcHandler rpcHandler = rpcHandlers.get(rpcMethod);
    if (rpcHandler == null) {
      throw new IllegalArgumentException("Unknown rpc method invoked: " + rpcMethod.getId());
    }
    return rpcHandler.sendRequest(connection, initialPayload, handler);
  }

  @Override
  public boolean connectionInitiatedLocally() {
    return connection.isInitiator();
  }

  @Override
  public boolean connectionInitiatedRemotely() {
    return !connectionInitiatedLocally();
  }

  private void handleConnectionClosed() {
    LOG.debug("Disconnected from peer {}", getId());
    connected.set(false);
  }

  @Override
  public void adjustReputation(final ReputationAdjustment adjustment) {
    final boolean shouldDisconnect = reputationManager.adjustReputation(getAddress(), adjustment);
    if (shouldDisconnect) {
      disconnectCleanly(DisconnectReason.REMOTE_FAULT).reportExceptions();
    }
  }
}
