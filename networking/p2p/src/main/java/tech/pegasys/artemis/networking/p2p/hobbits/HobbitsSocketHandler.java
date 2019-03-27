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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.units.bigints.UInt64;
import tech.pegasys.artemis.data.TimeSeriesRecord;

/** TCP persistent connection handler for hobbits messages. */
public final class HobbitsSocketHandler {

  private final NetSocket netSocket;
  private final String userAgent;
  private final Peer peer;
  private TimeSeriesRecord chainData;
  private final Set<Long> pendingResponses = new HashSet<>();
  private final AtomicBoolean status = new AtomicBoolean(true);

  public HobbitsSocketHandler(
      NetSocket netSocket, String userAgent, Peer peer, TimeSeriesRecord chainData) {
    this.netSocket = netSocket;
    this.userAgent = userAgent;
    this.peer = peer;
    this.chainData = chainData;
    netSocket.handler(this::handleMessage);
    netSocket.closeHandler(this::closed);
  }

  private void closed(@Nullable Void nothing) {
    if (status.compareAndSet(true, false)) {
      peer.setInactive();
    }
  }

  private Bytes buffer = Bytes.EMPTY;

  private void handleMessage(Buffer message) {
    Bytes messageBytes = Bytes.wrapBuffer(message);
    buffer = Bytes.concatenate(buffer, messageBytes);
    while (!buffer.isEmpty()) {
      RPCMessage rpcMessage = RPCCodec.decode(buffer);
      if (rpcMessage == null) {
        return;
      }
      buffer = buffer.slice(rpcMessage.length());
      handleRPCMessage(rpcMessage);
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

  private void sendReply(RPCMethod method, Object payload, long requestId) {
    sendBytes(RPCCodec.encode(method, payload, requestId));
  }

  private void sendMessage(RPCMethod method, Object payload) {
    sendBytes(RPCCodec.encode(method, payload, pendingResponses));
  }

  private void sendBytes(Bytes bytes) {
    netSocket.write(Buffer.buffer(bytes.toArrayUnsafe()));
  }

  public void disconnect() {
    if (status.get()) {
      netSocket.write(Buffer.buffer(RPCCodec.createGoodbye().toArrayUnsafe()));
      netSocket.close();
    }
  }

  public void replyHello(long requestId) {
    sendReply(
        RPCMethod.HELLO,
        new Hello(
            1,
            1,
            Bytes32.fromHexString(chainData.getFinalizedBlockRoot()),
            UInt64.valueOf(chainData.getEpoch()),
            Bytes32.fromHexString(chainData.getHeadBlockRoot()),
            UInt64.valueOf(chainData.getSlot())),
        requestId);
  }

  public void sendHello() {
    sendMessage(
        RPCMethod.HELLO,
        new Hello(
            1,
            1,
            Bytes32.fromHexString(chainData.getFinalizedBlockRoot()),
            UInt64.valueOf(chainData.getEpoch()),
            Bytes32.fromHexString(chainData.getHeadBlockRoot()),
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
