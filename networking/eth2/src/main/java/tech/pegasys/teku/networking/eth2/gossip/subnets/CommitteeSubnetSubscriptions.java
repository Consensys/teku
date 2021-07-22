/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.client.RecentChainData;

abstract class CommitteeSubnetSubscriptions implements AutoCloseable {

  protected final Spec spec;
  protected final RecentChainData recentChainData;
  protected final GossipNetwork gossipNetwork;
  protected final GossipEncoding gossipEncoding;

  private final Map<Integer, TopicChannel> subnetIdToTopicChannel = new HashMap<>();

  protected CommitteeSubnetSubscriptions(
      final RecentChainData recentChainData,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding) {
    this.spec = recentChainData.getSpec();
    this.recentChainData = recentChainData;
    this.gossipNetwork = gossipNetwork;
    this.gossipEncoding = gossipEncoding;
  }

  protected synchronized Optional<TopicChannel> getChannelForSubnet(final int subnetId) {
    return Optional.ofNullable(subnetIdToTopicChannel.get(subnetId));
  }

  public synchronized void subscribeToSubnetId(final int subnetId) {
    subnetIdToTopicChannel.computeIfAbsent(subnetId, this::createChannelForSubnetId);
  }

  public synchronized void unsubscribeFromSubnetId(final int subnetId) {
    final TopicChannel topicChannel = subnetIdToTopicChannel.remove(subnetId);
    if (topicChannel != null) {
      topicChannel.close();
    }
  }

  private TopicChannel createChannelForSubnetId(final int subnetId) {
    final Eth2TopicHandler<?> topicHandler = createTopicHandler(subnetId);
    return gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
  }

  protected abstract Eth2TopicHandler<?> createTopicHandler(final int subnetId);

  @Override
  public synchronized void close() {
    // Close gossip channels
    subnetIdToTopicChannel.values().forEach(TopicChannel::close);
    subnetIdToTopicChannel.clear();
  }
}
