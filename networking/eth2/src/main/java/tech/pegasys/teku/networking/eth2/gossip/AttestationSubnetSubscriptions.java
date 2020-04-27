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

package tech.pegasys.teku.networking.eth2.gossip;

import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.networking.eth2.gossip.topics.AttestationTopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationSubnetSubscriptions implements AutoCloseable {
  private final GossipNetwork gossipNetwork;
  private final RecentChainData recentChainData;
  private final EventBus eventBus;

  private final Map<UnsignedLong, Set<UnsignedLong>> subnetIdToCommittees = new HashMap<>();
  private final Map<UnsignedLong, TopicChannel> subnetIdToTopicChannel = new HashMap<>();

  public AttestationSubnetSubscriptions(
      final GossipNetwork gossipNetwork,
      final RecentChainData recentChainData,
      final EventBus eventBus) {
    this.gossipNetwork = gossipNetwork;
    this.recentChainData = recentChainData;
    this.eventBus = eventBus;
  }

  public synchronized Optional<TopicChannel> getChannel(final UnsignedLong committeeIndex) {
    final UnsignedLong subnetId = committeeIndexToSubnetId(committeeIndex);
    return Optional.ofNullable(subnetIdToTopicChannel.get(subnetId));
  }

  public synchronized void subscribeToCommitteeTopic(final UnsignedLong committeeIndex) {
    final UnsignedLong subnetId = committeeIndexToSubnetId(committeeIndex);
    final Set<UnsignedLong> subscribedCommittees =
        subnetIdToCommittees.computeIfAbsent(subnetId, __ -> new HashSet<>());
    subscribedCommittees.add(committeeIndex);
    subnetIdToTopicChannel.computeIfAbsent(subnetId, this::createChannelForSubnetId);
  }

  public synchronized void unsubscribeFromCommitteeTopic(final UnsignedLong committeeIndex) {
    final UnsignedLong subnetId = committeeIndexToSubnetId(committeeIndex);
    final Set<UnsignedLong> committees =
        subnetIdToCommittees.getOrDefault(subnetId, Collections.emptySet());
    committees.remove(committeeIndex);
    if (!committees.isEmpty()) {
      // We still have some subscribers, don't actually unsubscribe
      return;
    }
    subnetIdToCommittees.remove(subnetId);
    final TopicChannel topicChannel = subnetIdToTopicChannel.remove(subnetId);
    if (topicChannel != null) {
      topicChannel.close();
    }
  }

  private UnsignedLong committeeIndexToSubnetId(final UnsignedLong committeeIndex) {
    return committeeIndex.mod(UnsignedLong.valueOf(ATTESTATION_SUBNET_COUNT));
  }

  private TopicChannel createChannelForSubnetId(final UnsignedLong subnetId) {
    final AttestationTopicHandler topicHandler =
        new AttestationTopicHandler(eventBus, recentChainData, subnetId);
    return gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
  }

  @Override
  public synchronized void close() {
    // Close gossip channels
    subnetIdToTopicChannel.values().forEach(TopicChannel::close);
    subnetIdToCommittees.clear();
    subnetIdToTopicChannel.clear();
  }
}
