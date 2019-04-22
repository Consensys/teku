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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.plumtree.MessageSender;
import net.consensys.cava.plumtree.State;
import net.consensys.cava.units.bigints.UInt64;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.networking.p2p.hobbits.Codec.ProtocolType;
import tech.pegasys.artemis.util.alogger.ALogger;

/** TCP persistent connection handler for hobbits messages. */
public final class HobbitsSocketHandler {
  private static final ALogger LOG = new ALogger(HobbitsSocketHandler.class.getName());
  private final EventBus eventBus;
  private final String userAgent;
  private final Peer peer;
  private TimeSeriesRecord chainData;
  private final Set<Long> pendingResponses = new HashSet<>();
  private final AtomicBoolean status = new AtomicBoolean(true);
  private final Consumer<Bytes> messageSender;
  private final Runnable handlerTermination;
  private final State p2pState;
  private final ConcurrentHashMap<String, Boolean> receivedMessages;

  public HobbitsSocketHandler(
      EventBus eventBus,
      NetSocket netSocket,
      String userAgent,
      Peer peer,
      TimeSeriesRecord chainData,
      State p2pState,
      ConcurrentHashMap<String, Boolean> receivedMessages) {
    this(
        eventBus,
        userAgent,
        peer,
        chainData,
        (bytes) -> netSocket.write(Buffer.buffer(bytes.toArrayUnsafe())),
        netSocket::close,
        p2pState,
        receivedMessages);
    netSocket.handler(this::handleMessage);
    netSocket.closeHandler(this::closed);
  }

  public HobbitsSocketHandler(
      EventBus eventBus,
      String userAgent,
      Peer peer,
      TimeSeriesRecord chainData,
      Consumer<Bytes> messageSender,
      Runnable handlerTermination,
      State state,
      ConcurrentHashMap<String, Boolean> receivedMessages) {
    this.userAgent = userAgent;
    this.peer = peer;
    this.chainData = chainData;
    this.eventBus = eventBus;
    this.eventBus.register(this);
    this.messageSender = messageSender;
    this.handlerTermination = handlerTermination;
    this.p2pState = state;
    this.receivedMessages = receivedMessages;
  }

  private void closed(@Nullable Void nothing) {
    if (status.compareAndSet(true, false)) {
      peer.setInactive();
    }
  }

  private Bytes buffer = Bytes.EMPTY;

  public void handleMessage(Buffer message) {
    Bytes messageBytes = Bytes.wrapBuffer(message);

    buffer = Bytes.concatenate(buffer, messageBytes);
    while (!buffer.isEmpty()) {
      ProtocolType protocolType = Codec.protocolType(buffer);
      if (protocolType == ProtocolType.GOSSIP) {
        GossipMessage gossipMessage = GossipCodec.decode(buffer);
        if (gossipMessage == null) {
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
      peer.setPeerHello(rpcMessage.bodyAs(Hello.class));
    } else if (RPCMethod.GET_STATUS.equals(rpcMessage.method())) {
      if (!pendingResponses.remove(rpcMessage.requestId())) {
        replyStatus(rpcMessage.requestId());
      }
      peer.setPeerStatus(rpcMessage.bodyAs(GetStatus.class));
    } else if (RPCMethod.REQUEST_BLOCK_ROOTS.equals(rpcMessage.method())) {
      // TODO provide data
      sendReply(RPCMethod.BLOCK_ROOTS, new BlockRoots(new ArrayList<>()), rpcMessage.requestId());
    } else if (RPCMethod.REQUEST_BLOCK_HEADERS.equals(rpcMessage.method())) {
      // TODO provide data
      sendReply(RPCMethod.BLOCK_HEADERS, null, rpcMessage.requestId());
    } else if (RPCMethod.REQUEST_BLOCK_BODIES.equals(rpcMessage.method())) {
      // TODO provide data
      sendReply(RPCMethod.BLOCK_BODIES, null, rpcMessage.requestId());
    }
  }

  private void handleGossipMessage(GossipMessage gossipMessage) {
    if (!receivedMessages.containsKey(gossipMessage.messageHash().toHexString())) {
      receivedMessages.put(gossipMessage.messageHash().toHexString(), true);
      LOG.log(Level.INFO, "Received new gossip message from peer: " + peer.uri());
      if (GossipMethod.GOSSIP.equals(gossipMessage.method())) {
        Bytes bytes = gossipMessage.body();
        peer.setPeerGossip(bytes);
        this.eventBus.post(bytes);
        p2pState.receiveGossipMessage(peer, gossipMessage.body());
      } else if (GossipMethod.PRUNE.equals(gossipMessage.method())) {
        p2pState.receivePruneMessage(peer);
      } else if (GossipMethod.GRAFT.equals(gossipMessage.method())) {
        p2pState.receiveGraftMessage(peer, gossipMessage.messageHash());
      } else if (GossipMethod.IHAVE.equals(gossipMessage.method())) {
        p2pState.receiveIHaveMessage(peer, gossipMessage.messageHash());
      }
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
      MessageSender.Verb method, Bytes messageHash, Bytes32 hashSignature, Bytes payload) {
    Bytes bytes = GossipCodec.encode(method, messageHash, hashSignature, payload);
    sendBytes(bytes);
  }

  public void replyHello(long requestId) {
    RPCCodec.encode(
        RPCMethod.HELLO,
        new Hello(
            1,
            1,
            Bytes32.fromHexString(chainData.getLastFinalizedBlockRoot()),
            UInt64.valueOf(chainData.getEpoch()),
            Bytes32.fromHexString(chainData.getBlock_root()),
            UInt64.valueOf(chainData.getSlot())),
        requestId);
  }

  public void sendHello() {
    sendMessage(
        RPCMethod.HELLO,
        new Hello(
            1,
            1,
            Bytes32.fromHexString(chainData.getLastFinalizedBlockRoot()),
            UInt64.valueOf(chainData.getEpoch()),
            Bytes32.fromHexString(chainData.getBlock_root()),
            UInt64.valueOf(chainData.getSlot())));
  }

  public void replyStatus(long requestId) {
    sendReply(
        RPCMethod.GET_STATUS, new GetStatus(userAgent, Instant.now().toEpochMilli()), requestId);
  }

  public void sendStatus() {
    sendMessage(RPCMethod.GET_STATUS, new GetStatus(userAgent, Instant.now().toEpochMilli()));
  }

  public Peer peer() {
    return peer;
  }
}
