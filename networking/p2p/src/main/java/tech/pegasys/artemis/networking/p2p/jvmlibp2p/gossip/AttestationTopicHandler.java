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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

public class AttestationTopicHandler extends GossipTopicHandler<Attestation> {

  private static final Topic ATTESTATIONS_TOPIC = new Topic("/eth2/beacon_attestation/ssz");

  protected AttestationTopicHandler(final PubsubPublisherApi publisher, final EventBus eventBus) {
    super(publisher, eventBus);
  }

  @Override
  public Topic getTopic() {
    return ATTESTATIONS_TOPIC;
  }

  @Subscribe
  public void onNewAttestation(final Attestation attestation) {
    gossip(attestation);
  }

  @Override
  public Optional<Attestation> processData(MessageApi message, Bytes bytes) throws SSZException {
    Attestation attestation = SimpleOffsetSerializer.deserialize(bytes, Attestation.class);
    return Optional.ofNullable(attestation);
  }
}
