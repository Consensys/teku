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

package tech.pegasys.artemis.networking.p2p;

import com.google.common.eventbus.EventBus;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.consensys.cava.concurrent.AsyncCompletion;
import net.consensys.cava.crypto.SECP256K1;
import net.consensys.cava.rlpx.MemoryWireConnectionsRepository;
import net.consensys.cava.rlpx.WireConnectionRepository;
import net.consensys.cava.rlpx.vertx.VertxRLPxService;
import net.consensys.cava.rlpx.wire.WireConnection;
import org.logl.log4j2.Log4j2LoggerProvider;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;

/**
 * Peer to peer network for beacon nodes, over a RLPx connection.
 *
 * <p>This service exposes services over the subprotocol "bea".
 *
 * @see BeaconSubprotocolHandler
 */
public final class RLPxP2PNetwork implements P2PNetwork {

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final Vertx vertx;
  private final SECP256K1.KeyPair keyPair;
  private final int port;
  private final int advertisedPort;
  private final String networkInterface;
  private final Log4j2LoggerProvider loggerProvider;
  private final EventBus eventBus;

  private WireConnectionRepository wireConnectionRepository;
  private VertxRLPxService service;

  public RLPxP2PNetwork(EventBus eventBus) {
    this(eventBus, Vertx.vertx(), SECP256K1.KeyPair.random(), 9000, 9000, "127.0.0.1");
  }

  public RLPxP2PNetwork(
      EventBus eventBus,
      Vertx vertx,
      SECP256K1.KeyPair keyPair,
      int port,
      int advertisedPort,
      String networkInterface) {
    this.eventBus = eventBus;
    this.vertx = vertx;
    this.keyPair = keyPair;
    this.port = port;
    this.advertisedPort = advertisedPort;
    this.networkInterface = networkInterface;
    this.loggerProvider = new Log4j2LoggerProvider();
  }

  @Override
  public void run() {
    if (started.compareAndSet(false, true)) {
      wireConnectionRepository = new MemoryWireConnectionsRepository();
      service =
          new VertxRLPxService(
              vertx,
              loggerProvider,
              port,
              networkInterface,
              advertisedPort,
              keyPair,
              Collections.singletonList(new BeaconSubprotocol()),
              "Artemis 0.1");
    }
  }

  @Override
  public void stop() {
    if (started.compareAndSet(true, false)) {
      try {
        service.stop().join(10, TimeUnit.SECONDS);
      } catch (TimeoutException | InterruptedException e) {
        throw new RuntimeException(e);
      } finally {
        vertx.close();
      }
    }
  }

  @Override
  public void close() throws IOException {
    stop();
  }

  @Override
  public Collection<?> getPeers() {
    if (!started.get()) {
      throw new IllegalStateException();
    }
    List<String> peers = new ArrayList<>();
    for (WireConnection conn : wireConnectionRepository.asIterable()) {
      peers.add(conn.id());
    }
    return peers;
  }

  @Override
  public CompletableFuture<?> connect(String peer) {
    if (!started.get()) {
      throw new IllegalStateException();
    }
    URI enode = URI.create(peer);

    CompletableFuture<?> completableFuture = new CompletableFuture<>();

    AsyncCompletion completion =
        service.connectTo(
            SECP256K1.PublicKey.fromHexString(enode.getRawUserInfo()),
            new InetSocketAddress(enode.getHost(), enode.getPort()));
    completion.whenComplete(
        exception -> {
          if (exception != null) {
            completableFuture.completeExceptionally(exception);
          } else {
            completableFuture.complete(null);
          }
        });

    return completableFuture;
  }

  @Override
  public void subscribe(String event) {
    if (!started.get()) {
      throw new IllegalStateException();
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isListening() {
    return started.get();
  }
}
