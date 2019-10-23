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
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RPCMethods;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods.BeaconBlocksMessageHandler;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods.GoodbyeMessageHandler;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods.HelloMessageFactory;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods.HelloMessageHandler;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;

public class PeerManager implements ConnectionHandler, PeerLookup {
  private static final ALogger STDOUT = new ALogger("stdout");
  private final ALogger LOG = new ALogger(PeerManager.class.getName());

  private final ScheduledExecutorService scheduler;
  private static final long RECONNECT_TIMEOUT = 5000;
  private final HelloMessageFactory helloMessageFactory;

  private ConcurrentHashMap<Multiaddr, Peer> connectedPeerMap = new ConcurrentHashMap<>();

  private final RPCMethods rpcMethods;

  public PeerManager(
      final ScheduledExecutorService scheduler, final ChainStorageClient chainStorageClient) {
    this.scheduler = scheduler;
    helloMessageFactory = new HelloMessageFactory(chainStorageClient);
    this.rpcMethods =
        new RPCMethods(
            this,
            new HelloMessageHandler(helloMessageFactory),
            new GoodbyeMessageHandler(),
            new BeaconBlocksMessageHandler());
  }

  @Override
  public void handleConnection(@NotNull final Connection connection) {
    Peer peer = new Peer(connection);
    onConnectedPeer(peer);
    connection.closeFuture().thenRun(() -> onDisconnectedPeer(peer));

    if (connection.isInitiator()) {
      rpcMethods
          .getHello()
          .invokeRemote(connection, helloMessageFactory.createLocalHelloMessage())
          .thenAccept(peer::receivedHelloMessage);
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

  @Override
  public Peer getPeer(Connection conn) {
    return connectedPeerMap.get(conn.remoteAddress());
  }

  protected void onConnectedPeer(Peer peer) {
    final boolean wasAdded = connectedPeerMap.putIfAbsent(peer.getPeerMultiaddr(), peer) == null;
    if (wasAdded) {
      STDOUT.log(Level.DEBUG, "onConnectedPeer() " + peer.getPeerMultiaddr());
    }
  }

  protected void onDisconnectedPeer(Peer peer) {
    if (connectedPeerMap.remove(peer.getPeerMultiaddr()) != null) {
      STDOUT.log(Level.DEBUG, "Peer disconnected: " + peer.getPeerId());
    }
  }

  public RPCMethods getRPCMethods() {
    return rpcMethods;
  }
}
