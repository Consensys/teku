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
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.concurrent.AsyncCompletion;
import org.apache.tuweni.concurrent.CompletableAsyncCompletion;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.plumtree.EphemeralPeerRepository;
import org.apache.tuweni.plumtree.MessageSender;
import org.apache.tuweni.plumtree.State;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.hobbits.AbstractSocketHandler;
import tech.pegasys.artemis.networking.p2p.hobbits.Peer;
import tech.pegasys.artemis.networking.p2p.hobbits.SocketHandlerFactory;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;

/**
 * Hobbits Ethereum Wire Protocol implementation.
 *
 * <p>This P2P implementation uses clear messages relying on the hobbits wire format.
 */
public final class HobbitsP2PNetwork implements P2PNetwork {
  private static final ALogger LOG = new ALogger(HobbitsP2PNetwork.class.getName());
  private static final ALogger STDOUT = new ALogger("stdout");
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final EventBus eventBus;
  private final Vertx vertx;
  private final ChainStorageClient store;
  private final int port;
  private final int advertisedPort;
  private final String networkInterface;
  private final String userAgent = "Artemis SNAPSHOT";
  private State state;
  private NetServer server;
  private NetClient client;
  private List<URI> staticPeers;
  private Map<URI, AbstractSocketHandler> handlersMap = new ConcurrentHashMap<>();
  private ConcurrentHashMap<String, Boolean> receivedMessages = new ConcurrentHashMap<>();
  private GossipProtocol gossipProtocol;

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
      ChainStorageClient store,
      int port,
      int advertisedPort,
      String networkInterface,
      List<URI> staticPeers,
      GossipProtocol gossipProtocol) {
    this.eventBus = eventBus;
    this.vertx = vertx;
    this.store = store;
    this.port = port;
    this.advertisedPort = advertisedPort;
    this.networkInterface = networkInterface;
    this.staticPeers = staticPeers;
    this.gossipProtocol = gossipProtocol;
    eventBus.register(this);
    this.state =
        new State(
            new EphemeralPeerRepository(),
            Hash::sha2_256,
            this::sendMessage,
            this::processGossip,
            (bytes, peer) -> true,
            (peer) -> true,
            200,
            200);
  }

  @SuppressWarnings({"rawtypes"})
  private AbstractSocketHandler createSocketHandler(NetSocket netSocket, Peer peer) {
    return new SocketHandlerFactory()
        .create(
            this.gossipProtocol.name(),
            new Object[] {
              this.eventBus, netSocket, userAgent, peer, store, state, receivedMessages
            },
            new Class[] {
              EventBus.class,
              NetSocket.class,
              String.class,
              Peer.class,
              ChainStorageClient.class,
              State.class,
              ConcurrentHashMap.class
            });
  }

  @SuppressWarnings("StringSplitter")
  private void sendMessage(
      MessageSender.Verb verb,
      String attr,
      org.apache.tuweni.plumtree.Peer peer,
      Bytes hash,
      Bytes bytes) {
    if (!started.get()) {
      return;
    }
    AbstractSocketHandler handler = handlersMap.get(((Peer) peer).uri());
    String[] attributes = attr.split(",");
    if (handler != null) {
      CompletableFuture.runAsync(
          () ->
              handler.gossipMessage(
                  verb.ordinal(),
                  attributes[0],
                  Long.valueOf(attributes[1]),
                  hash,
                  Bytes32.random(),
                  bytes));
    }
  }

  @SuppressWarnings("StringSplitter")
  private void processGossip(Bytes gossipMessage, String attr) {
    String[] attributes = attr.split(",");
    if (attributes[0].equalsIgnoreCase("ATTESTATION")) {
      Attestation attestation = Attestation.fromBytes(gossipMessage);
      this.eventBus.post(attestation);
    } else if (attributes[0].equalsIgnoreCase("BLOCK")) {
      BeaconBlock block = BeaconBlock.fromBytes(gossipMessage);
      this.eventBus.post(block);
    }
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
    staticPeers
        .parallelStream()
        .forEach(
            uri -> {
              connect(uri);
            });
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
          state.addPeer(peer);
          return createSocketHandler(netSocket, peer);
        });
  }

  @Override
  public Collection<?> getPeers() {
    return handlersMap.values().stream()
        .map(AbstractSocketHandler::peer)
        .collect(Collectors.toList());
  }

  @Override
  public Collection<?> getHandlers() {
    return handlersMap.values();
  }

  CompletableFuture<?> connect(URI peerURI) {
    CompletableFuture<Peer> connected = new CompletableFuture<>();
    connected.whenCompleteAsync(
        (peer, th) -> {
          STDOUT.log(Level.INFO, "Connected to " + peer.uri());
          AbstractSocketHandler handler = handlersMap.get(peer.uri());
          if (handler != null) {
            STDOUT.log(Level.INFO, "Send hello to: " + peer.uri());
            handler.sendHello();
          }
        });
    AbstractSocketHandler existingHandler = handlersMap.get(peerURI);
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
              NetSocket netSocket = res.result();
              Peer peer = new Peer(peerURI);
              AbstractSocketHandler handler = createSocketHandler(netSocket, peer);
              handlersMap.put(peerURI, handler);
              state.addPeer(peer);
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
  public void subscribe(String event) {
    // TODO
    if (!started.get()) {}
  }

  @Override
  public void stop() {
    if (started.compareAndSet(true, false)) {
      try {
        for (AbstractSocketHandler handler : handlersMap.values()) {
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
}
