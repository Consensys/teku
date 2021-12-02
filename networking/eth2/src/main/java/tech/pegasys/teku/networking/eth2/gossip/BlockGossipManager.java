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
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockGossipManager implements GossipManager {

  private final GossipEncoding gossipEncoding;
  private final TopicChannel channel;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public BlockGossipManager(
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<SignedBeaconBlock> processor,
      final int maxMessageSize) {
    this.gossipEncoding = gossipEncoding;

    // Gossip topics are specific to a fork so use the schema for the current fork.
    final SignedBeaconBlockSchema signedBeaconBlockSchema =
        spec.atEpoch(forkInfo.getFork().getEpoch())
            .getSchemaDefinitions()
            .getSignedBeaconBlockSchema();
    final Eth2TopicHandler<SignedBeaconBlock> topicHandler =
        new Eth2TopicHandler<>(
            recentChainData,
            asyncRunner,
            processor,
            gossipEncoding,
            forkInfo.getForkDigest(spec),
            GossipTopicName.BEACON_BLOCK,
            signedBeaconBlockSchema,
            maxMessageSize);
    this.channel = gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
  }

  public void publishBlock(final SignedBeaconBlock block) {
    final Bytes data = gossipEncoding.encode(block);
    channel.gossip(data);
  }

  @Override
  public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
      // Close gossip channels
      channel.close();
    }
  }
}
