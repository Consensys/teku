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
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.util.alogger.ALogger;

public class MothraHandler {
  private static final ALogger STDOUT = new ALogger("stdout");
  public static String BLOCK_TOPIC = "beacon_block";
  public static String ATTESTATION_TOPIC = "beacon_attestation";
  protected final EventBus eventBus;
  protected final ChainStorageClient store;
  protected ConcurrentHashSet<String> receivedMessages;

  public MothraHandler(EventBus eventBus, ChainStorageClient store) {
    this.eventBus = eventBus;
    this.store = store;
    eventBus.register(this);
    receivedMessages = new ConcurrentHashSet<>();
  }

  public void gossipMessage(String topic, Bytes data) {
    mothra.SendGossip(topic.getBytes(UTF_8), data.toArray());
  }

  public synchronized Boolean handleGossipMessage(String topic, byte[] message) {
    Bytes messageBytes = Bytes.wrap(message);
    STDOUT.log(Level.DEBUG, "Received " + messageBytes.size() + " bytes");
    String key = messageBytes.toHexString();
    if (!receivedMessages.contains(key)) {
      receivedMessages.add(key);
      if (topic.equalsIgnoreCase(ATTESTATION_TOPIC)) {
        STDOUT.log(Level.DEBUG, "Received Attestation");
        Attestation attestation = Attestation.fromBytes(messageBytes);
        this.eventBus.post(attestation);
      } else if (topic.equalsIgnoreCase(BLOCK_TOPIC)) {
        STDOUT.log(Level.DEBUG, "Received Block");
        BeaconBlock block = BeaconBlock.fromBytes(messageBytes);
        this.eventBus.post(block);
      }
    }
    return true;
  }

  public synchronized Boolean handleDiscoveryMessage(String peer) {
    // Disable HELLO for now
    // HelloMessage msg = buildHelloMessage();
    // sendHello(peer, msg);
    return true;
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    Bytes bytes = SimpleOffsetSerializer.serialize(block);
    if (!this.receivedMessages.contains(bytes.toHexString())) {
      this.receivedMessages.add(bytes.toHexString());
      STDOUT.log(
          Level.DEBUG,
          "Gossiping new block with state root: " + block.getState_root().toHexString());
      this.gossipMessage(BLOCK_TOPIC, bytes);
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
      this.gossipMessage(ATTESTATION_TOPIC, bytes);
    }
  }
}
