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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipedOperationConsumer;
import tech.pegasys.teku.networking.eth2.gossip.topics.SingleAttestationTopicHandler;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationSubnetSubscriptions implements AutoCloseable {
  private final GossipNetwork gossipNetwork;
  private final GossipEncoding gossipEncoding;
  private final AttestationValidator attestationValidator;
  private final RecentChainData recentChainData;
  private final GossipedOperationConsumer<ValidateableAttestation> gossipedAttestationConsumer;

  private final Map<Integer, TopicChannel> subnetIdToTopicChannel = new HashMap<>();

  public AttestationSubnetSubscriptions(
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final AttestationValidator attestationValidator,
      final RecentChainData recentChainData,
      final GossipedOperationConsumer<ValidateableAttestation> gossipedAttestationConsumer) {
    this.gossipNetwork = gossipNetwork;
    this.gossipEncoding = gossipEncoding;
    this.recentChainData = recentChainData;
    this.attestationValidator = attestationValidator;
    this.gossipedAttestationConsumer = gossipedAttestationConsumer;
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
    final ForkInfo forkInfo = recentChainData.getHeadForkInfo().orElseThrow();
    final SingleAttestationTopicHandler topicHandler =
        new SingleAttestationTopicHandler(
            gossipEncoding, forkInfo, subnetId, attestationValidator, gossipedAttestationConsumer);
    return gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
  }

  @Override
  public synchronized void close() {
    // Close gossip channels
    subnetIdToTopicChannel.values().forEach(TopicChannel::close);
    subnetIdToTopicChannel.clear();
  }
}
