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

package tech.pegasys.artemis.networking.eth2.gossip;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.networking.eth2.gossip.topics.AggregateTopicHandler;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.SignedAggregateAndProofValidator;
import tech.pegasys.artemis.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.artemis.networking.p2p.gossip.TopicChannel;
import tech.pegasys.artemis.ssz.SSZTypes.Bytes4;

public class AggregateGossipManager {
  private final EventBus eventBus;
  private final TopicChannel channel;
  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public AggregateGossipManager(
      final GossipNetwork gossipNetwork,
      final EventBus eventBus,
      final SignedAggregateAndProofValidator validator,
      final Bytes4 forkDigest) {
    final AggregateTopicHandler aggregateTopicHandler =
        new AggregateTopicHandler(eventBus, forkDigest, validator);
    this.eventBus = eventBus;
    channel = gossipNetwork.subscribe(aggregateTopicHandler.getTopic(), aggregateTopicHandler);
    eventBus.register(this);
  }

  @Subscribe
  public void onNewAggregate(final SignedAggregateAndProof aggregateAndProof) {
    final Bytes data = SimpleOffsetSerializer.serialize(aggregateAndProof);
    channel.gossip(data);
  }

  public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
      eventBus.unregister(this);
      // Close gossip channels
      channel.close();
    }
  }
}
