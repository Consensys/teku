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
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.consensys.cava.concurrent.AsyncCompletion;
import net.consensys.cava.concurrent.CompletableAsyncCompletion;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.data.adapter.TimeSeriesAdapter;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.hobbits.HobbitsSocketHandler;
import tech.pegasys.artemis.networking.p2p.hobbits.Peer;

/**
 * Hobbits Ethereum Wire Protocol implementation.
 *
 * <p>This P2P implementation uses clear messages relying on the hobbits wire format.
 */
public final class HobbitsP2PNetwork implements P2PNetwork {

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final EventBus eventBus;
  private final Vertx vertx;
  private final int port;
  private final int advertisedPort;
  private final String networkInterface;
  private final String userAgent = "Artemis SNAPSHOT";
  private NetServer server;
  private NetClient client;
  private List<URI> staticPeers;
  private TimeSeriesRecord chainData;
  private Map<URI, HobbitsSocketHandler> handlersMap = new ConcurrentHashMap<>();

  /**
   * Default constructor
   *
   * @param eventBus the event bus of the instance
   * @param vertx the vertx instance to rely to build network elements
   * @param port the port to bind to
   * @param advertisedPort the port to advertise on
   * @param networkInterface the network interface to bind to
   * @param staticPeers the static peers this network will connect to
   */
  public HobbitsP2PNetwork(
      EventBus eventBus,
      Vertx vertx,
      int port,
      int advertisedPort,
      String networkInterface,
      List<URI> staticPeers) {
    this.eventBus = eventBus;
    this.vertx = vertx;
    this.port = port;
    this.advertisedPort = advertisedPort;
    this.networkInterface = networkInterface;
    this.staticPeers = staticPeers;
    this.chainData = new TimeSeriesRecord();
    eventBus.register(this);
  }

  @Override
  public void run() {
    if (started.compareAndSet(false, true)) {
      client = vertx.createNetClient(new NetClientOptions().setTcpKeepAlive(true));
      server =
          vertx
              .createNetServer(
                  new NetServerOptions()
                      .setPort(port)
                      .setHost(networkInterface)
                      .setTcpKeepAlive(true))
              .connectHandler(this::receiveMessage);
      server.listen(
          res -> {
            if (res.failed()) {
              throw new RuntimeException(res.cause());
            } else {
              connectStaticPeers();
            }
          });
    }
  }

  private void connectStaticPeers() {
    for (URI peer : staticPeers) {
      connect(peer);
    }
  }

  private void receiveMessage(NetSocket netSocket) {
    URI peerURI =
        URI.create(
            "hob+tcp://"
                + netSocket.remoteAddress().host()
                + ":"
                + netSocket.remoteAddress().port());
    handlersMap.computeIfAbsent(
        peerURI,
        uri -> {
          Peer peer = new Peer(peerURI);
          return new HobbitsSocketHandler(netSocket, userAgent, peer, chainData);
        });
  }

  @Override
  public Collection<?> getPeers() {
    return handlersMap.values().stream()
        .map(HobbitsSocketHandler::peer)
        .collect(Collectors.toList());
  }

  CompletableFuture<?> connect(URI peerURI) {
    CompletableFuture<Peer> connected = new CompletableFuture<>();
    HobbitsSocketHandler existingHandler = handlersMap.get(peerURI);
    if (existingHandler != null) {
      connected.complete(existingHandler.peer());
    } else {

      client.connect(
          peerURI.getPort(),
          peerURI.getHost(),
          res -> {
            if (res.failed()) {
              connected.completeExceptionally(res.cause());
            } else {
              NetSocket socket = res.result();
              Peer peer = new Peer(peerURI);
              HobbitsSocketHandler handler =
                  new HobbitsSocketHandler(socket, userAgent, peer, chainData);
              handlersMap.put(peerURI, handler);
              handler.sendHello();
              handler.sendStatus();
              connected.complete(peer);
            }
          });
    }
    return connected;
  }

  @Override
  public CompletableFuture<?> connect(String peer) {
    return connect(URI.create(peer));
  }

  @Override
  public void subscribe(String event) {}

  @Override
  public void stop() {
    if (started.compareAndSet(true, false)) {
      try {
        for (HobbitsSocketHandler handler : handlersMap.values()) {
          handler.disconnect();
        }
        CompletableAsyncCompletion completed = AsyncCompletion.incomplete();
        server.close(
            res -> {
              if (res.failed()) {
                completed.completeExceptionally(res.cause());
              } else {
                completed.complete();
              }
            });
        completed.join(10, TimeUnit.SECONDS);
      } catch (InterruptedException | TimeoutException e) {
        throw new RuntimeException(e);
      } finally {
        client.close();
      }
    }
  }

  @Override
  public boolean isListening() {
    return started.get();
  }

  @Override
  public void close() throws IOException {
    stop();
  }

  @Override
  public synchronized void onDataEvent(RawRecord record) {
    TimeSeriesAdapter adapter = new TimeSeriesAdapter(record);
    chainData = adapter.transform();
  }
}
