/*
 * Copyright Consensys Software Inc., 2022
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

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.util.P2PDebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class AbstractGossipManager<T extends SszData> implements GossipManager {

  private final GossipNetwork gossipNetwork;
  private final GossipEncoding gossipEncoding;

  private final Eth2TopicHandler<T> topicHandler;

  private Optional<TopicChannel> channel = Optional.empty();

  protected AbstractGossipManager(
      final RecentChainData recentChainData,
      final GossipTopicName topicName,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<T> processor,
      final SszSchema<T> gossipType,
      final Function<T, UInt64> getEpochForMessage,
      final NetworkingSpecConfig networkingConfig,
      final P2PDebugDataDumper p2pDebugDataDumper) {
    this.gossipNetwork = gossipNetwork;
    this.topicHandler =
        new Eth2TopicHandler<>(
            recentChainData,
            asyncRunner,
            processor,
            gossipEncoding,
            forkInfo.getForkDigest(recentChainData.getSpec()),
            topicName,
            new OperationMilestoneValidator<>(
                recentChainData.getSpec(), forkInfo.getFork(), getEpochForMessage),
            gossipType,
            networkingConfig,
            p2pDebugDataDumper);
    this.gossipEncoding = gossipEncoding;
  }

  @VisibleForTesting
  public Eth2TopicHandler<T> getTopicHandler() {
    return topicHandler;
  }

  protected void publishMessage(final T message) {
    channel.ifPresent(c -> c.gossip(gossipEncoding.encode(message)));
  }

  @Override
  public void subscribe() {
    if (channel.isEmpty()) {
      channel = Optional.of(gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler));
    }
  }

  @Override
  public void unsubscribe() {
    if (channel.isPresent()) {
      channel.get().close();
      channel = Optional.empty();
    }
  }
}
