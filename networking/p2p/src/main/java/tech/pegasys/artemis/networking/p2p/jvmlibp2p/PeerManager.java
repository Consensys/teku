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

import com.google.common.primitives.UnsignedLong;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionHandler;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.network.NetworkImpl;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.HelloMessage;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.alogger.ALogger;

public class PeerManager implements ConnectionHandler {
  private static final ALogger STDOUT = new ALogger("stdout");
  private final ALogger LOG = new ALogger(PeerManager.class.getName());

  private final ScheduledExecutorService scheduler;
  private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(1);

  private ConcurrentHashMap<Multiaddr, Peer> connectedPeerMap = new ConcurrentHashMap<>();

  private final RPCMessageHandler<HelloMessage, HelloMessage> rpcMessageHandler;

  public PeerManager(final ScheduledExecutorService scheduler) {
    this.scheduler = scheduler;

    this.rpcMessageHandler =
        new RPCMessageHandler<>("/eth2/beacon_chain/req/hello/1/ssz", this::hello) {
          @Override
          protected CompletableFuture<HelloMessage> invokeLocal(
              Connection connection, HelloMessage helloMessage) {
            return CompletableFuture.completedFuture(
                this.helloHandler.apply(connection, helloMessage));
          }
        };
  }

  @Override
  public void handleConnection(@NotNull final Connection connection) {
    STDOUT.log(Level.INFO, "New connection: " + connection.remoteAddress());
    Peer peer = new Peer(connection);
    connectedPeerMap.put(peer.getPeerMultiaddr(), peer);

    connection
        .closeFuture()
        .thenRun(() -> STDOUT.log(Level.INFO, "Peer disconnected: " + peer.getPeerId()));

    if (connection.isInitiator()) {
      rpcMessageHandler
          .invokeRemote(connection, createHelloMessage())
          .thenApply(resp -> peer.getRemoteHelloMessage().complete(resp));
    }
  }

  public CompletableFuture<?> connect(final Multiaddr peer, final NetworkImpl network) {
    STDOUT.log(Level.INFO, "Connecting to " + peer);
    return network
        .connect(peer)
        .whenComplete(
            (conn, throwable) -> {
              if (throwable != null) {
                STDOUT.log(
                    Level.INFO,
                    "Connection to " + peer + " failed. Will retry shortly: " + throwable);
                scheduler.schedule(
                    () -> connect(peer, network),
                    RECONNECT_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS);
              } else {
                handleConnection(conn);
                conn.closeFuture()
                    .thenAccept(
                        ignore -> {
                          LOG.log(
                              Level.INFO, "Connection to " + peer + " closed. Will retry shortly");
                          scheduler.schedule(
                              () -> connect(peer, network),
                              RECONNECT_TIMEOUT.toMillis(),
                              TimeUnit.MILLISECONDS);
                        });
              }
            });
  }

  private Peer getPeer(Connection conn) {
    return connectedPeerMap.get(conn.remoteAddress());
  }

  protected void onNewActivePeer(Peer peer) {
    STDOUT.log(Level.INFO, "New active peer: " + peer);
  }

  private HelloMessage hello(Connection connection, HelloMessage helloMessage) {
    STDOUT.log(Level.INFO, "Peer said hello");
    if (connection.isInitiator()) {
      throw new IllegalArgumentException("Responder peer shouldn't initiate Hello message");
    } else {
      getPeer(connection).getRemoteHelloMessage().complete(helloMessage);
      return createHelloMessage();
    }
  }

  private HelloMessage createHelloMessage() {
    return new HelloMessage(
        Bytes4.rightPad(Bytes.of(4)),
        Bytes32.random(),
        UnsignedLong.ZERO,
        Bytes32.random(),
        UnsignedLong.ZERO);
  }

  public List<RPCMessageHandler<?, ?>> all() {
    return Arrays.asList(rpcMessageHandler);
  }
}
