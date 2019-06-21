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
import com.google.common.eventbus.Subscribe;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
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
import tech.pegasys.artemis.networking.p2p.hobbits.HobbitsSocketHandler;
import tech.pegasys.artemis.networking.p2p.hobbits.Peer;
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
  private final State state;
  private NetServer server;
  private NetClient client;
  private List<URI> staticPeers;
  private Map<URI, HobbitsSocketHandler> handlersMap = new ConcurrentHashMap<>();
  private ConcurrentHashMap<String, Boolean> receivedMessages = new ConcurrentHashMap<>();

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
      List<URI> staticPeers) {
    this.eventBus = eventBus;
    this.vertx = vertx;
    this.store = store;
    this.port = port;
    this.advertisedPort = advertisedPort;
    this.networkInterface = networkInterface;
    this.staticPeers = staticPeers;
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

  private void sendMessage(
      MessageSender.Verb verb,
      String attributes,
      org.apache.tuweni.plumtree.Peer peer,
      Bytes hash,
      Bytes bytes) {
    if (!started.get()) {
      return;
    }
    HobbitsSocketHandler handler = handlersMap.get(((Peer) peer).uri());
    if (handler != null) {
      vertx.executeBlocking(
          h -> {
            handler.gossipMessage(verb.ordinal(), attributes, hash, Bytes32.random(), bytes);
          },
          res -> {});
    }
  }

  @SuppressWarnings("StringSplitter")
  private void processGossip(Bytes gossipMessage, String attr) {
    String[] attributes = attr.split(",");
    if (attributes[0].equalsIgnoreCase("ATTESTATION")) {
      this.eventBus.post(Attestation.fromBytes(gossipMessage));
    } else if (attributes[0].equalsIgnoreCase("BLOCK")) {
      BeaconBlock block = BeaconBlock.fromBytes(gossipMessage);
      receivedMessages.put(block.toBytes().toHexString(), true);
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
              connect(uri)
                  .thenAccept(
                      peer ->
                          STDOUT.log(
                              Level.INFO, "Connected to peer: " + ((Peer) peer).uri().getPort()));
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
          return new HobbitsSocketHandler(eventBus, netSocket, userAgent, peer, store, state);
        });
  }

  @Override
  public Collection<?> getPeers() {
    return handlersMap.values().stream()
        .map(HobbitsSocketHandler::peer)
        .collect(Collectors.toList());
  }

  @Override
  public Collection<?> getHandlers() {
    return handlersMap.values();
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
                  new HobbitsSocketHandler(eventBus, socket, userAgent, peer, store, state);
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

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    Bytes bytes = block.toBytes();
    if (!this.receivedMessages.containsKey(bytes.toHexString())) {
      STDOUT.log(
          Level.INFO,
          "Gossiping new block with state root: " + block.getState_root().toHexString());
      String attributes = "BLOCK" + "," + String.valueOf(new Date().getTime());
      state.sendGossipMessage(attributes, bytes);
      this.receivedMessages.put(bytes.toHexString(), true);
    } else {
      LOG.log(Level.INFO, "Ignoring block " + block.getState_root().toHexString());
    }
  }

  @Subscribe
  public void onNewUnprocessedAttestation(Attestation attestation) {
    Bytes bytes = attestation.toBytes();
    if (!this.receivedMessages.containsKey(bytes.toHexString())) {
      STDOUT.log(
          Level.INFO,
          "Gossiping new attestation for block root: "
              + attestation.getData().getBeacon_block_root().toHexString());
      String attributes = "ATTESTATION" + "," + String.valueOf(new Date().getTime());
      state.sendGossipMessage(attributes, bytes);
      this.receivedMessages.put(bytes.toHexString(), true);
    } else {
      LOG.log(
          Level.INFO,
          "Ignoring attestation " + attestation.getData().getBeacon_block_root().toHexString());
    }
  }
}
