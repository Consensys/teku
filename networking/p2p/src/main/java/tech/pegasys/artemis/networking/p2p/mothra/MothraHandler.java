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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.vertx.core.impl.ConcurrentHashSet;
import net.p2p.mothra;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.mothra.rpc.HelloMessage;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.networking.p2p.mothra.rpc.RPCCodec;
import tech.pegasys.artemis.networking.p2p.mothra.rpc.RPCMethod;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.alogger.ALogger;

public class MothraHandler {
  private static final ALogger STDOUT = new ALogger("stdout");
  public static String BLOCK_TOPIC = "/eth2/beacon_block/ssz";
  public static String ATTESTATION_TOPIC = "/eth2/beacon_attestation/ssz";
  public static int RPC_REQUEST = 0;
  public static int RPC_RESPONSE = 1;
  protected final EventBus eventBus;
  protected final ChainStorageClient store;
  protected ConcurrentHashSet<String> receivedMessages;

  public MothraHandler(EventBus eventBus, ChainStorageClient store) {
    this.eventBus = eventBus;
    this.store = store;
    eventBus.register(this);
    receivedMessages = new ConcurrentHashSet<>();
  }

  public void sendGossipMessage(String topic, Bytes data) {
    mothra.SendGossip(topic.getBytes(UTF_8), data.toArray());
  }

  public void sendRPCMessage(String method, int reqResponse, String peer, Bytes data) {
    mothra.SendRPC(method.getBytes(UTF_8), reqResponse, peer.getBytes(UTF_8), data.toArray());
  }

  public synchronized Boolean handleDiscoveryMessage(String peer) {
    // Disable HELLO for now
    // HelloMessage msg = buildHelloMessage();
    // sendHello(peer, msg);
    return true;
  }

  public synchronized Boolean handleGossipMessage(String topic, byte[] message) {
    Bytes messageBytes = Bytes.wrap(message);
    STDOUT.log(Level.INFO, "Received " + messageBytes.size() + " bytes");
    String key = messageBytes.toHexString();
    if (!receivedMessages.contains(key)) {
      receivedMessages.add(key);
      if (topic.equalsIgnoreCase(ATTESTATION_TOPIC)) {
        STDOUT.log(Level.INFO, "Received Attestation");
        Attestation attestation =
            SimpleOffsetSerializer.deserialize(messageBytes, Attestation.class);
        this.eventBus.post(attestation);
      } else if (topic.equalsIgnoreCase(BLOCK_TOPIC)) {
        STDOUT.log(Level.INFO, "Received Block");
        BeaconBlock block = SimpleOffsetSerializer.deserialize(messageBytes, BeaconBlock.class);
        this.eventBus.post(block);
      }
    }
    return true;
  }

  public synchronized Boolean handleRPCMessage(
      String method, int reqResponse, String peer, byte[] message) {
    Bytes messageBytes = Bytes.wrap(message);
    STDOUT.log(Level.DEBUG, "Received " + messageBytes.size() + " bytes");
    String key = messageBytes.toHexString();
    if (method.toUpperCase().equals(RPCMethod.HELLO.name())) {
      if (reqResponse == RPC_REQUEST) {
        STDOUT.log(Level.INFO, "Received HELLO from: " + peer);
        HelloMessage msg = buildHelloMessage();
        replyHello(peer, msg);
      }
    }
    return true;
  }

  public void sendHello(String peer, HelloMessage msg) {
    STDOUT.log(Level.INFO, "Send hello to: " + peer);
    Bytes data = RPCCodec.encode(msg);
    sendRPCMessage(RPCMethod.HELLO.name(), RPC_REQUEST, peer, data);
  }

  public void replyHello(String peer, HelloMessage msg) {
    STDOUT.log(Level.INFO, "Send reply hello to: " + peer);
    Bytes data = RPCCodec.encode(msg);
    sendRPCMessage(RPCMethod.HELLO.name(), RPC_RESPONSE, peer, data);
  }

  protected HelloMessage buildHelloMessage() {
    Bytes4 forkVersion = new Bytes4(Bytes.of(4));
    return new HelloMessage(
        forkVersion,
        store.getFinalizedBlockRoot(),
        store.getFinalizedEpoch(),
        store.getBestBlockRoot(),
        store.getBestSlot());
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    Bytes bytes = SimpleOffsetSerializer.serialize(block);
    if (!this.receivedMessages.contains(bytes.toHexString())) {
      this.receivedMessages.add(bytes.toHexString());
      STDOUT.log(
          Level.DEBUG,
          "Gossiping new block with state root: " + block.getState_root().toHexString());
      this.sendGossipMessage(BLOCK_TOPIC, bytes);
    }
  }

  @Subscribe
  public void onNewUnprocessedAttestation(Attestation attestation) {
    Bytes bytes = SimpleOffsetSerializer.serialize(attestation);
    if (!this.receivedMessages.contains(bytes.toHexString())) {
      this.receivedMessages.add(bytes.toHexString());
      STDOUT.log(
          Level.DEBUG,
          "Gossiping new attestation for block root: "
              + attestation.getData().getBeacon_block_root());
      this.sendGossipMessage(ATTESTATION_TOPIC, bytes);
    }
  }
}
