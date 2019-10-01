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
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.network.NetworkImpl;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.HelloMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RPCMethods;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;

public class PeerManager implements ConnectionHandler {
  private static final ALogger STDOUT = new ALogger("stdout");
  private final ALogger LOG = new ALogger(PeerManager.class.getName());

  private final ScheduledExecutorService scheduler;
  private final ChainStorageClient chainStorageClient;
  private static final long RECONNECT_TIMEOUT = 5000;

  private ConcurrentHashMap<Multiaddr, Peer> connectedPeerMap = new ConcurrentHashMap<>();

  private final RPCMethods rpcMethods;

  public PeerManager(
      final ScheduledExecutorService scheduler, final ChainStorageClient chainStorageClient) {
    this.scheduler = scheduler;
    this.chainStorageClient = chainStorageClient;
    this.rpcMethods = new RPCMethods(this::hello, this::goodbye);
  }

  @Override
  public void handleConnection(@NotNull final Connection connection) {
    Peer peer = new Peer(connection);
    onConnectedPeer(peer);
    connection.closeFuture().thenRun(() -> onDisconnectedPeer(peer));

    if (connection.isInitiator()) {
      rpcMethods
          .getHello()
          .invokeRemote(connection, createLocalHelloMessage())
          .thenApply(resp -> peer.getRemoteHelloMessage().complete(resp));
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
              } else if (throwable == null) {
                STDOUT.log(
                    Level.DEBUG,
                    "Connection to peer: "
                        + conn.getSecureSession().getRemoteId()
                        + " was successful");
                conn.closeFuture()
                    .thenAccept(
                        ignore -> {
                          LOG.log(
                              Level.DEBUG, "Connection to " + peer + " closed. Will retry shortly");
                          scheduler.schedule(
                              () -> connect(peer, network),
                              RECONNECT_TIMEOUT,
                              TimeUnit.MILLISECONDS);
                        });
              }
            });
  }

  private Peer getPeer(Connection conn) {
    return connectedPeerMap.get(conn.remoteAddress());
  }

  protected void onConnectedPeer(Peer peer) {
    if (!connectedPeerMap.containsKey(peer.getPeerMultiaddr())) {
      STDOUT.log(Level.DEBUG, "onConnectedPeer() " + peer.getPeerMultiaddr());
      connectedPeerMap.put(peer.getPeerMultiaddr(), peer);
    }
  }

  protected void onDisconnectedPeer(Peer peer) {
    if (connectedPeerMap.remove(peer.getPeerMultiaddr()) != null) {
      STDOUT.log(Level.DEBUG, "Peer disconnected: " + peer.getPeerId());
    }
  }

  private HelloMessage hello(Connection connection, HelloMessage message) {
    if (connection.isInitiator()) {
      throw new IllegalArgumentException("Responder peer shouldn't initiate Hello message");
    } else {
      STDOUT.log(
          Level.DEBUG, "Peer " + connection.getSecureSession().getRemoteId() + " said hello.");
      STDOUT.log(Level.DEBUG, message.toString());
      getPeer(connection).getRemoteHelloMessage().complete(message);
      return createLocalHelloMessage();
    }
  }

  private Void goodbye(Connection connection, GoodbyeMessage message) {
    STDOUT.log(
        Level.DEBUG, "Peer " + connection.getSecureSession().getRemoteId() + " said goodbye.");
    return null;
  }

  private HelloMessage createLocalHelloMessage() {
    return new HelloMessage(
        chainStorageClient.getBestBlockRootState().getFork().getCurrent_version(),
        chainStorageClient.getStore().getFinalizedCheckpoint().getRoot(),
        chainStorageClient.getStore().getFinalizedCheckpoint().getEpoch(),
        chainStorageClient.getBestBlockRoot(),
        chainStorageClient.getBestSlot());
  }

  public RPCMethods getRPCMethods() {
    return rpcMethods;
  }
}
