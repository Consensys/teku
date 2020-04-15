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

import static java.lang.StrictMath.toIntExact;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.vertx.core.impl.ConcurrentHashSet;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tech.pegasys.artemis.networking.eth2.gossip.topics.AttestationTopicHandler;
import tech.pegasys.artemis.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.artemis.networking.p2p.gossip.TopicChannel;
import tech.pegasys.artemis.storage.client.RecentChainData;

class AttestationSubnetSubscriptions implements AutoCloseable {
  public static final int ATTESTATION_SUBNET_COUNT = 64;

  private final GossipNetwork gossipNetwork;
  private final RecentChainData recentChainData;
  private final EventBus eventBus;

  private final Map<Integer, Set<UnsignedLong>> subnetIdToCommittees = new ConcurrentHashMap<>();
  private final Map<Integer, TopicChannel> subnetIdToTopicChannel = new ConcurrentHashMap<>();

  AttestationSubnetSubscriptions(
      final GossipNetwork gossipNetwork,
      final RecentChainData recentChainData,
      final EventBus eventBus) {
    this.gossipNetwork = gossipNetwork;
    this.recentChainData = recentChainData;
    this.eventBus = eventBus;
  }

  public Optional<TopicChannel> getChannel(final UnsignedLong committeeIndex) {
    final int subnetId = committeeIndexToSubnetId(committeeIndex);
    if (!subnetIdToCommittees
        .computeIfAbsent(subnetId, __ -> Collections.emptySet())
        .contains(committeeIndex)) {
      // We're not subscribed to this committee subnet
      return Optional.empty();
    }
    return Optional.ofNullable(subnetIdToTopicChannel.get(subnetId));
  }

  public synchronized void subscribeToCommitteeTopic(final UnsignedLong committeeIndex) {
    final int subnetId = committeeIndexToSubnetId(committeeIndex);
    final Set<UnsignedLong> subscribedCommittees =
        subnetIdToCommittees.computeIfAbsent(subnetId, __ -> new ConcurrentHashSet<>());
    subscribedCommittees.add(committeeIndex);
    subnetIdToTopicChannel.computeIfAbsent(subnetId, this::createChannelForSubnetId);
  }

  public synchronized void unsubscribeFromCommitteeTopic(final UnsignedLong committeeIndex) {
    final int subnetId = committeeIndexToSubnetId(committeeIndex);
    final Set<UnsignedLong> subscribedCommittees =
        subnetIdToCommittees.compute(
            subnetId,
            (subnet, committees) -> {
              if (committees == null) {
                return null;
              }
              committees.remove(committeeIndex);
              if (committees.isEmpty()) {
                return null;
              }
              return committees;
            });

    if (subscribedCommittees != null) {
      // We still have some subscribers, don't actually unsubscribe
      return;
    }

    subnetIdToTopicChannel.computeIfPresent(
        subnetId,
        (index, channel) -> {
          channel.close();
          return null;
        });
  }

  private int committeeIndexToSubnetId(final UnsignedLong committeeIndex) {
    final UnsignedLong subnetId =
        committeeIndex.mod(UnsignedLong.valueOf(ATTESTATION_SUBNET_COUNT));
    return toIntExact(subnetId.longValue());
  }

  private TopicChannel createChannelForSubnetId(final int subnetId) {
    final AttestationTopicHandler topicHandler =
        new AttestationTopicHandler(eventBus, recentChainData, subnetId);
    return gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
  }

  @Override
  public void close() {
    // Close gossip channels
    subnetIdToTopicChannel.values().forEach(TopicChannel::close);
    subnetIdToCommittees.clear();
    subnetIdToTopicChannel.clear();
  }
}
