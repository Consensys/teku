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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.gossip;

import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.pubsub.gossip.Gossip;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.bls.BLSVerify;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class GossipMessageHandler implements Consumer<MessageApi> {
  private static final Logger LOG = LogManager.getLogger();
  private static final ALogger STDOUT = new ALogger("stdout");
  private static final int MAX_SENT_MESSAGES = 2048;

  private static final Topic BLOCKS_TOPIC = new Topic("/eth2/beacon_block/ssz");
  private static final Topic ATTESTATIONS_TOPIC = new Topic("/eth2/beacon_attestation/ssz");
  private final ChainStorageClient chainStorageClient;
  private EventBus eventBus;
  private final PubsubPublisherApi publisher;
  private final Set<Bytes> sentMessages =
      Collections.synchronizedSet(
          Collections.newSetFromMap(
              new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(final Entry<Bytes, Boolean> eldest) {
                  return size() > MAX_SENT_MESSAGES;
                }
              }));

  public GossipMessageHandler(
      final PubsubPublisherApi publisher,
      final EventBus eventBus,
      final ChainStorageClient chainStorageClient) {
    this.publisher = publisher;
    this.eventBus = eventBus;
    this.chainStorageClient = chainStorageClient;
  }

  public static void init(
      final Gossip gossip,
      final PrivKey privateKey,
      final EventBus eventBus,
      final ChainStorageClient chainStorageClient) {
    final PubsubPublisherApi publisher =
        gossip.createPublisher(privateKey, new Random().nextLong());
    final GossipMessageHandler handler =
        new GossipMessageHandler(publisher, eventBus, chainStorageClient);
    gossip.subscribe(handler, BLOCKS_TOPIC, ATTESTATIONS_TOPIC);
    eventBus.register(handler);
  }

  @Override
  public void accept(MessageApi msg) {
    STDOUT.log(Level.DEBUG, "Gossip Message Received: " + msg.getTopics());
    if (msg.getTopics().contains(BLOCKS_TOPIC)) {
      Bytes bytes = Bytes.wrapByteBuf(msg.getData());
      STDOUT.log(Level.DEBUG, "Block received: " + bytes.size() + " bytes");
      BeaconBlock block = SimpleOffsetSerializer.deserialize(bytes, BeaconBlock.class);
      final BLSSignature signature = block.getSignature();
      final BeaconState state = chainStorageClient.getStore().getBlockState(block.getParent_root());
      final Validator proposer = state.getValidators().get(get_beacon_proposer_index(state));
      final Bytes domain = get_domain(state, DOMAIN_BEACON_PROPOSER);
      final boolean validSignature =
          BLSVerify.bls_verify(
              proposer.getPubkey(), block.signing_root("signature"), signature, domain);
      if (!validSignature) {
        LOG.debug("{} received invalid block: {}", this.getClass().getSimpleName(), block);
        return;
      }
      if (this.sentMessages.add(bytes)) {
        eventBus.post(block);
      }
    } else if (msg.getTopics().contains(ATTESTATIONS_TOPIC)) {
      Bytes bytes = Bytes.wrapByteBuf(msg.getData());
      STDOUT.log(Level.DEBUG, "Attestation received: " + bytes.size() + " bytes");
      Attestation attestation = SimpleOffsetSerializer.deserialize(bytes, Attestation.class);
      if (this.sentMessages.add(bytes)) {
        eventBus.post(attestation);
      }
    }
  }

  @Subscribe
  public void onNewBlock(final BeaconBlock block) {
    gossip(block, BLOCKS_TOPIC);
  }

  @Subscribe
  public void onNewAttestation(final Attestation attestation) {
    gossip(attestation, ATTESTATIONS_TOPIC);
  }

  private void gossip(final SimpleOffsetSerializable data, final Topic topic) {
    Bytes bytes = SimpleOffsetSerializer.serialize(data);
    if (this.sentMessages.add(bytes)) {
      STDOUT.log(Level.DEBUG, "GOSSIPING " + topic.getTopic() + ": " + bytes.size() + " bytes");
      publisher.publish(Unpooled.wrappedBuffer(bytes.toArrayUnsafe()), topic);
    }
  }
}
