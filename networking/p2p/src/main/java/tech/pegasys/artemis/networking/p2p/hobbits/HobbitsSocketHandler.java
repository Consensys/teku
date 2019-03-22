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
import java.util.HashSet;
import java.util.Set;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.units.bigints.UInt64;

/** TCP persistent connection handler for hobbits messages. */
public final class HobbitsSocketHandler {

  private final NetSocket netSocket;
  private final String userAgent;
  private final Peer peer;
  private final Set<Long> pendingResponses = new HashSet<>();

  public HobbitsSocketHandler(NetSocket netSocket, String userAgent, Peer peer) {
    this.netSocket = netSocket;
    this.userAgent = userAgent;
    this.peer = peer;
    netSocket.handler(this::handleMessage);
  }

  private Bytes buffer = Bytes.EMPTY;

  private void handleMessage(Buffer message) {
    Bytes messageBytes = Bytes.wrapBuffer(message);
    RPCMessage rpcMessage = RPCCodec.decode(messageBytes);
    if (rpcMessage == null) {
      buffer = Bytes.concatenate(buffer, messageBytes);
      return;
    }

    if (RPCMethod.GOODBYE.equals(rpcMessage.method())) {
      peer.setInactive();
      netSocket.close();
    } else if (RPCMethod.HELLO.equals(rpcMessage.method())) {
      if (!pendingResponses.remove(rpcMessage.requestId())) {
        sendHello();
      }
      peer.setPeerHello(rpcMessage.bodyAs(Hello.class));
    } else if (RPCMethod.GET_STATUS.equals(rpcMessage.method())) {
      if (!pendingResponses.remove(rpcMessage.requestId())) {
        sendStatus();
      }
      peer.setPeerStatus(rpcMessage.bodyAs(GetStatus.class));
    }
  }

  private void sendMessage(RPCMethod method, Object payload) {
    netSocket.write(
        Buffer.buffer(RPCCodec.encode(method, payload, pendingResponses).toArrayUnsafe()));
  }

  public void disconnect() {
    netSocket.write(Buffer.buffer(RPCCodec.createGoodbye().toArrayUnsafe()));
    netSocket.close();
  }

  public void sendHello() {
    // TODO connect to data
    sendMessage(
        RPCMethod.HELLO,
        new Hello(1, 1, Bytes32.random(), UInt64.valueOf(0), Bytes32.random(), UInt64.valueOf(0)));
  }

  public void sendStatus() {
    sendMessage(RPCMethod.GET_STATUS, new GetStatus(userAgent, Instant.now().toEpochMilli()));
  }

  public Peer peer() {
    return peer;
  }
}
