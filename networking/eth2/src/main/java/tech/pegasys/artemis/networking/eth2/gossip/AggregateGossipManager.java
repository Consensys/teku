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
import tech.pegasys.artemis.datastructures.state.ForkInfo;
import tech.pegasys.artemis.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.artemis.networking.eth2.gossip.topics.AggregateTopicHandler;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.SignedAggregateAndProofValidator;
import tech.pegasys.artemis.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.artemis.networking.p2p.gossip.TopicChannel;

public class AggregateGossipManager {
  private final GossipEncoding gossipEncoding;
  private final TopicChannel channel;
  private final EventBus eventBus;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public AggregateGossipManager(
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final SignedAggregateAndProofValidator validator,
      final EventBus eventBus) {
    this.gossipEncoding = gossipEncoding;

    final AggregateTopicHandler aggregateTopicHandler =
        new AggregateTopicHandler(gossipEncoding, forkInfo, validator, eventBus);
    this.channel = gossipNetwork.subscribe(aggregateTopicHandler.getTopic(), aggregateTopicHandler);

    this.eventBus = eventBus;
    eventBus.register(this);
  }

  @Subscribe
  public void onNewAggregate(final SignedAggregateAndProof aggregateAndProof) {
    final Bytes data = gossipEncoding.encode(aggregateAndProof);
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
