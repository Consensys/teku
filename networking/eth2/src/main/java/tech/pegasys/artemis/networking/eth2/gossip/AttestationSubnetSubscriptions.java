/*
 * Copyright 2020 ConsenSys AG.
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.artemis.networking.eth2.gossip.topics.AttestationTopicHandler;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.artemis.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.artemis.networking.p2p.gossip.TopicChannel;
import tech.pegasys.artemis.storage.client.RecentChainData;

public class AttestationSubnetSubscriptions implements AutoCloseable {
  private final GossipNetwork gossipNetwork;
  private final RecentChainData recentChainData;
  private final AttestationValidator attestationValidator;
  private final EventBus eventBus;

  private final Map<Integer, TopicChannel> subnetIdToTopicChannel = new HashMap<>();

  public AttestationSubnetSubscriptions(
      final GossipNetwork gossipNetwork,
      final RecentChainData recentChainData,
      final AttestationValidator attestationValidator,
      final EventBus eventBus) {
    this.gossipNetwork = gossipNetwork;
    this.recentChainData = recentChainData;
    this.attestationValidator = attestationValidator;
    this.eventBus = eventBus;
  }

  public synchronized Optional<TopicChannel> getChannel(final int subnetId) {
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
    final AttestationTopicHandler topicHandler =
        new AttestationTopicHandler(
            eventBus,
            attestationValidator,
            subnetId,
            recentChainData.getCurrentForkInfo().orElseThrow());
    return gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
  }

  @Override
  public synchronized void close() {
    // Close gossip channels
    subnetIdToTopicChannel.values().forEach(TopicChannel::close);
    subnetIdToTopicChannel.clear();
  }
}
