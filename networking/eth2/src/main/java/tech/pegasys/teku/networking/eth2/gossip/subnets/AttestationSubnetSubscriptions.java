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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.TopicNames;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.SingleAttestationTopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationSubnetSubscriptions implements AutoCloseable {

  private final AsyncRunner asyncRunner;
  private final GossipNetwork gossipNetwork;
  private final GossipEncoding gossipEncoding;
  private final RecentChainData recentChainData;
  private final OperationProcessor<ValidateableAttestation> processor;

  private final Map<Integer, TopicChannel> subnetIdToTopicChannel = new HashMap<>();

  public AttestationSubnetSubscriptions(
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final RecentChainData recentChainData,
      final OperationProcessor<ValidateableAttestation> processor) {
    this.asyncRunner = asyncRunner;
    this.gossipNetwork = gossipNetwork;
    this.gossipEncoding = gossipEncoding;
    this.recentChainData = recentChainData;
    this.processor = processor;
  }

  public SafeFuture<?> gossip(final Attestation attestation) {
    return computeSubnetForAttestation(attestation)
        .thenCompose(
            subnetId -> {
              if (subnetId.isEmpty()) {
                throw new IllegalStateException(
                    "Unable to calculate the subnet ID for attestation in slot "
                        + attestation.getData().getSlot()
                        + " because the state was not available");
              }
              final ForkInfo forkInfo = recentChainData.getHeadForkInfo().orElseThrow();
              final String topic =
                  TopicNames.getAttestationSubnetTopic(
                      forkInfo.getForkDigest(), subnetId.get(), gossipEncoding);
              return gossipNetwork.gossip(topic, gossipEncoding.encode(attestation));
            });
  }

  @VisibleForTesting
  SafeFuture<Optional<TopicChannel>> getChannel(final Attestation attestation) {
    return computeSubnetForAttestation(attestation)
        .thenApply(subnetId -> subnetId.flatMap(this::getChannelForSubnet));
  }

  private synchronized Optional<TopicChannel> getChannelForSubnet(final int subnetId) {
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
    final String topicName = TopicNames.getAttestationSubnetTopicName(subnetId);
    final SingleAttestationTopicHandler topicHandler =
        new SingleAttestationTopicHandler(
            asyncRunner, processor, gossipEncoding, forkInfo.getForkDigest(), topicName, subnetId);
    return gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
  }

  private SafeFuture<Optional<Integer>> computeSubnetForAttestation(final Attestation attestation) {
    return recentChainData
        .retrieveStateInEffectAtSlot(attestation.getData().getSlot())
        .thenApply(
            state -> state.map(s -> CommitteeUtil.computeSubnetForAttestation(s, attestation)));
  }

  @Override
  public synchronized void close() {
    // Close gossip channels
    subnetIdToTopicChannel.values().forEach(TopicChannel::close);
    subnetIdToTopicChannel.clear();
  }
}
