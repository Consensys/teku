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

package tech.pegasys.teku.networking.eth2.gossip;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.AggregateAttestationTopicHandler;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipedAttestationConsumer;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.SignedAggregateAndProofValidator;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;

public class AggregateGossipManager {
  private final GossipEncoding gossipEncoding;
  private final TopicChannel channel;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public AggregateGossipManager(
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final SignedAggregateAndProofValidator validator,
      final GossipedAttestationConsumer gossipedAttestationConsumer) {
    this.gossipEncoding = gossipEncoding;
    final AggregateAttestationTopicHandler aggregateAttestationTopicHandler =
        new AggregateAttestationTopicHandler(
            gossipEncoding, forkInfo, validator, gossipedAttestationConsumer);
    this.channel =
        gossipNetwork.subscribe(
            aggregateAttestationTopicHandler.getTopic(), aggregateAttestationTopicHandler);
  }

  public void onNewAggregate(final ValidateableAttestation validateableAttestation) {
    if (!validateableAttestation.isAggregate() || !validateableAttestation.markGossiped()) {
      return;
    }
    final Bytes data = gossipEncoding.encode(validateableAttestation.getSignedAggregateAndProof());
    channel.gossip(data);
  }

  public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
      // Close gossip channels
      channel.close();
    }
  }
}
