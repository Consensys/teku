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

package tech.pegasys.artemis.networking.p2p.mothra;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.vertx.core.impl.ConcurrentHashSet;
import java.math.BigInteger;
import java.util.Date;
import net.p2p.mothra;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.hobbits.Message;
import org.apache.tuweni.hobbits.Protocol;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.networking.p2p.mothra.gossip.GossipCodec;
import tech.pegasys.artemis.networking.p2p.mothra.gossip.GossipMessage;
import tech.pegasys.artemis.util.alogger.ALogger;

public class MothraHandler {
  private static final ALogger STDOUT = new ALogger("stdout");
  protected final EventBus eventBus;
  protected ConcurrentHashSet<String> receivedMessages;

  public MothraHandler(EventBus eventBus) {
    this.eventBus = eventBus;
    eventBus.register(this);
    receivedMessages = new ConcurrentHashSet<>();
  }

  public void gossipMessage(
      int method, String topic, long timestamp, Bytes messageHash, Bytes body) {
    Bytes bytes =
        GossipCodec.encode(
                method, topic, BigInteger.valueOf(timestamp), messageHash.toArray(), body.toArray())
            .toBytes();
    mothra.SendGossip(bytes.toArray());
  }

  public synchronized Boolean handleMessage(byte[] message) {
    Bytes messageBytes = Bytes.wrap(message);
    STDOUT.log(Level.DEBUG, "Received " + messageBytes.size() + " bytes");
    Message hobbitsMessage = Message.readMessage(messageBytes);
    if (hobbitsMessage != null) {
      Protocol protocol = hobbitsMessage.getProtocol();
      if (protocol == Protocol.RPC) {
        // RPCMessage rpcMessage = RPCCodec.decode(hobbitsMessage);
        // checkArgument(rpcMessage != null, "Unable to decode RPC message");
        // handleRPCMessage(rpcMessage);
      } else if (protocol == Protocol.GOSSIP) {
        GossipMessage gossipMessage = GossipCodec.decode(hobbitsMessage);
        checkArgument(gossipMessage != null, "Unable to decode GOSSIP message");
        handleGossipMessage(gossipMessage);
      }
    } else {
      return false;
    }
    return true;
  }

  // protected void handleRPCMessage(RPCMessage rpcMessage) {
  //  throw new UnsupportedOperationException();
  // }

  protected void handleGossipMessage(GossipMessage gossipMessage) {
    Bytes body = Bytes.wrap(gossipMessage.body());
    String key = body.toHexString();
    if (!receivedMessages.contains(key)) {
      receivedMessages.add(key);
      if (gossipMessage.getTopic().equalsIgnoreCase("ATTESTATION")) {
        Attestation attestation = Attestation.fromBytes(body);
        this.eventBus.post(attestation);
      } else if (gossipMessage.getTopic().equalsIgnoreCase("BLOCK")) {
        BeaconBlock block = BeaconBlock.fromBytes(body);
        this.eventBus.post(block);
      }
    }
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    Bytes bytes = block.toBytes();
    if (!this.receivedMessages.contains(bytes.toHexString())) {
      this.receivedMessages.add(bytes.toHexString());
      STDOUT.log(
          Level.DEBUG,
          "Gossiping new block with state root: " + block.getState_root().toHexString());
      int method = 0;
      String topic = "BLOCK";
      long timestamp = new Date().getTime();
      this.gossipMessage(method, topic, timestamp, Bytes32.random(), bytes);
    }
  }

  @Subscribe
  public void onNewUnprocessedAttestation(Attestation attestation) {
    Bytes bytes = attestation.toBytes();
    if (!this.receivedMessages.contains(bytes.toHexString())) {
      this.receivedMessages.add(bytes.toHexString());
      STDOUT.log(
          Level.DEBUG,
          "Gossiping new attestation for block root: "
              + attestation.getData().getBeacon_block_root());
      int method = 0;
      String topic = "ATTESTATION";
      long timestamp = new Date().getTime();
      this.gossipMessage(method, topic, timestamp, Bytes32.random(), bytes);
    }
  }
}
