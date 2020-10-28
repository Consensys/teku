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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.statetransition.events.block.ProposedBlockEvent;

public class BlockGossipManager {
  public static String TOPIC_NAME = "beacon_block";

  private final GossipEncoding gossipEncoding;
  private final TopicChannel channel;
  private final EventBus eventBus;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public BlockGossipManager(
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final EventBus eventBus,
      final OperationProcessor<SignedBeaconBlock> processor) {
    this.gossipEncoding = gossipEncoding;

    final Eth2TopicHandler<SignedBeaconBlock> topicHandler =
        new Eth2TopicHandler<>(
            asyncRunner,
            processor,
            gossipEncoding,
            forkInfo.getForkDigest(),
            TOPIC_NAME,
            SignedBeaconBlock.class);
    this.channel = gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);

    this.eventBus = eventBus;
    eventBus.register(this);
  }

  @Subscribe
  @SuppressWarnings("unused")
  void onBlockProposed(final ProposedBlockEvent blockProposedEvent) {
    final Bytes data = gossipEncoding.encode(blockProposedEvent.getBlock());
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
