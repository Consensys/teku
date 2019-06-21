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

package tech.pegasys.artemis.networking.p2p.hobbits;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.plumtree.MessageSender;
import org.apache.tuweni.plumtree.State;
import org.apache.tuweni.units.bigints.UInt64;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.networking.p2p.hobbits.Codec.ProtocolType;
import tech.pegasys.artemis.networking.p2p.hobbits.gossip.GossipCodec;
import tech.pegasys.artemis.networking.p2p.hobbits.gossip.GossipMessage;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.GetStatusMessage;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.HelloMessage;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.RPCCodec;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.RPCMessage;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.RPCMethod;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.RequestBlocksMessage;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;

/** TCP persistent connection handler for hobbits messages. */
public final class HobbitsSocketHandler {
  private static final ALogger LOG = new ALogger(HobbitsSocketHandler.class.getName());
  private final EventBus eventBus;
  private final String userAgent;
  private final Peer peer;
  private final ChainStorageClient store;
  private final Set<Long> pendingResponses = new HashSet<>();
  private final AtomicBoolean status = new AtomicBoolean(true);
  private final Consumer<Bytes> messageSender;
  private final Runnable handlerTermination;
  private final State p2pState;

  public HobbitsSocketHandler(
      EventBus eventBus,
      NetSocket netSocket,
      String userAgent,
      Peer peer,
      ChainStorageClient store,
      State p2pState) {
    this.userAgent = userAgent;
    this.peer = peer;
    this.store = store;
    this.eventBus = eventBus;
    this.eventBus.register(this);
    this.messageSender = (bytes) -> netSocket.write(Buffer.buffer(bytes.toArrayUnsafe()));
    this.handlerTermination = netSocket::close;
    this.p2pState = p2pState;

    netSocket.handler(this::handleMessage);
    netSocket.exceptionHandler(this::handleError);
    netSocket.closeHandler(this::closed);
  }

  private void closed(@Nullable Void nothing) {
    if (status.compareAndSet(true, false)) {
      peer.setInactive();
      LOG.log(Level.INFO, "Peer marked inactive " + peer.uri());
    }
  }

  private Bytes buffer = Bytes.EMPTY;

  public synchronized void handleMessage(Buffer message) {
    Bytes messageBytes = Bytes.wrapBuffer(message);

    LOG.log(Level.DEBUG, "Received " + messageBytes.size() + " bytes");
    buffer = Bytes.concatenate(buffer, messageBytes);
    LOG.log(Level.DEBUG, "Buffer at " + buffer.size() + " bytes");

    while (!buffer.isEmpty()) {
      ProtocolType protocolType = Codec.protocolType(buffer);
      if (protocolType == ProtocolType.GOSSIP) {
        GossipMessage gossipMessage = GossipCodec.decode(buffer);
        if (gossipMessage == null) {
          LOG.log(Level.DEBUG, "Message too short");
          return;
        }
        buffer = buffer.slice(gossipMessage.length());
        handleGossipMessage(gossipMessage);
      } else if (protocolType == ProtocolType.RPC) {
        RPCMessage rpcMessage = RPCCodec.decode(buffer);
        if (rpcMessage == null) {
          return;
        }
        buffer = buffer.slice(rpcMessage.length());
        handleRPCMessage(rpcMessage);
      }
    }
  }

  private void handleRPCMessage(RPCMessage rpcMessage) {
    if (RPCMethod.GOODBYE.equals(rpcMessage.method())) {
      closed(null);
    } else if (RPCMethod.HELLO.equals(rpcMessage.method())) {
      if (!pendingResponses.remove(rpcMessage.requestId())) {
        replyHello(rpcMessage.requestId());
      }
      peer.setPeerHello(rpcMessage.bodyAs(HelloMessage.class));
    } else if (RPCMethod.GET_STATUS.equals(rpcMessage.method())) {
      if (!pendingResponses.remove(rpcMessage.requestId())) {
        replyStatus(rpcMessage.requestId());
      }
      peer.setPeerStatus(rpcMessage.bodyAs(GetStatusMessage.class));
    } else if (RPCMethod.REQUEST_BLOCK_HEADERS.equals(rpcMessage.method())) {
      RequestBlocksMessage rb = rpcMessage.bodyAs(RequestBlocksMessage.class);
      List<Optional<BeaconBlock>> blocks =
          store.getProcessedBlocks(rb.startRoot(), rb.max(), rb.skip());
      List<Bytes> blockHeaders = new ArrayList<>();
      blocks.forEach(
          block -> {
            if (block.isPresent()) {
              blockHeaders.add(
                  new BeaconBlockHeader(
                          UnsignedLong.valueOf(block.get().getSlot()),
                          block.get().getPrevious_block_root(),
                          block.get().getState_root(),
                          block.get().getBody().hash_tree_root(),
                          block.get().getSignature())
                      .toBytes());
            }
          });
      if (blockHeaders.size() > 0) {
        sendReply(RPCMethod.BLOCK_HEADERS, blockHeaders, rpcMessage.requestId());
      }
    } else if (RPCMethod.REQUEST_BLOCK_BODIES.equals(rpcMessage.method())) {
      RequestBlocksMessage rb = rpcMessage.bodyAs(RequestBlocksMessage.class);
      List<Optional<BeaconBlock>> blocks =
          store.getProcessedBlocks(rb.startRoot(), rb.max(), rb.skip());
      List<Bytes> blockBodies = new ArrayList<>();
      blocks.forEach(
          block -> {
            if (block.isPresent()) {
              blockBodies.add(block.get().toBytes());
            }
          });
      if (blockBodies.size() > 0) {
        sendReply(RPCMethod.BLOCK_BODIES, blockBodies, rpcMessage.requestId());
      }
    }
  }

  private void handleGossipMessage(GossipMessage gossipMessage) {
    LOG.log(
        Level.INFO,
        "Received new gossip message of type "
            + gossipMessage.method()
            + " from peer: "
            + peer.uri());
    if (MessageSender.Verb.GOSSIP.ordinal() == gossipMessage.method()) {
      peer.setPeerGossip(gossipMessage.body());
      p2pState.receiveGossipMessage(
          peer, gossipMessage.getAttributes(), gossipMessage.body(), gossipMessage.messageHash());
    } else if (MessageSender.Verb.PRUNE.ordinal() == gossipMessage.method()) {
      p2pState.receivePruneMessage(peer);
    } else if (MessageSender.Verb.GRAFT.ordinal() == gossipMessage.method()) {
      p2pState.receiveGraftMessage(peer, gossipMessage.messageHash());
    } else if (MessageSender.Verb.IHAVE.ordinal() == gossipMessage.method()) {
      p2pState.receiveIHaveMessage(peer, gossipMessage.messageHash());
    } else {
      throw new UnsupportedOperationException(gossipMessage.method() + " is not supported");
    }
  }

  private void sendReply(RPCMethod method, Object payload, long requestId) {
    sendBytes(RPCCodec.encode(method, payload, requestId));
  }

  private void sendMessage(RPCMethod method, Object payload) {
    sendBytes(RPCCodec.encode(method, payload, pendingResponses));
  }

  private void sendBytes(Bytes bytes) {
    messageSender.accept(bytes);
  }

  public void disconnect() {
    if (status.get()) {
      sendBytes(RPCCodec.createGoodbye());
      handlerTermination.run();
    }
  }

  public void gossipMessage(
      int method, String attributes, Bytes messageHash, Bytes32 hashSignature, Bytes payload) {
    Bytes bytes = GossipCodec.encode(method, attributes, messageHash, hashSignature, payload);
    sendBytes(bytes);
  }

  public void replyHello(long requestId) {
    RPCCodec.encode(
        RPCMethod.HELLO,
        new HelloMessage(
            1,
            1,
            store.getFinalizedBlockRoot(),
            UInt64.valueOf(store.getFinalizedEpoch().longValue()),
            store.getBestBlockRoot(),
            UInt64.valueOf(store.getBestSlot().longValue())),
        requestId);
  }

  public void sendHello() {
    sendMessage(
        RPCMethod.HELLO,
        new HelloMessage(
            1,
            1,
            store.getFinalizedBlockRoot(),
            UInt64.valueOf(store.getFinalizedEpoch().longValue()),
            store.getBestBlockRoot(),
            UInt64.valueOf(store.getBestSlot().longValue())));
  }

  public void replyStatus(long requestId) {
    sendReply(
        RPCMethod.GET_STATUS,
        new GetStatusMessage(userAgent, Instant.now().toEpochMilli()),
        requestId);
  }

  public void sendStatus() {
    sendMessage(
        RPCMethod.GET_STATUS, new GetStatusMessage(userAgent, Instant.now().toEpochMilli()));
  }

  public Peer peer() {
    return peer;
  }

  private void handleError(Throwable t) {
    LOG.log(Level.ERROR, "P2P error: " + t.getMessage());
  }
}
