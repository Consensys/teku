/*
 * Copyright ConsenSys Software Inc., 2022
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
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.SingleAttestationTopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationSubnetSubscriptions extends CommitteeSubnetSubscriptions {

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final OperationProcessor<ValidateableAttestation> processor;
  private final ForkInfo forkInfo;
  private final int maxMessageSize;
  private final AttestationSchema attestationSchema;

  public AttestationSubnetSubscriptions(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final RecentChainData recentChainData,
      final OperationProcessor<ValidateableAttestation> processor,
      final ForkInfo forkInfo,
      final int maxMessageSize) {
    super(gossipNetwork, gossipEncoding);
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.processor = processor;
    this.forkInfo = forkInfo;
    this.maxMessageSize = maxMessageSize;
    attestationSchema =
        spec.atEpoch(forkInfo.getFork().getEpoch()).getSchemaDefinitions().getAttestationSchema();
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
              final String topic =
                  GossipTopics.getAttestationSubnetTopic(
                      forkInfo.getForkDigest(spec), subnetId.get(), gossipEncoding);
              return gossipNetwork.gossip(topic, gossipEncoding.encode(attestation));
            });
  }

  @VisibleForTesting
  SafeFuture<Optional<TopicChannel>> getChannel(final Attestation attestation) {
    return computeSubnetForAttestation(attestation)
        .thenApply(subnetId -> subnetId.flatMap(this::getChannelForSubnet));
  }

  @Override
  protected Eth2TopicHandler<?> createTopicHandler(final int subnetId) {
    final String topicName = GossipTopicName.getAttestationSubnetTopicName(subnetId);
    return SingleAttestationTopicHandler.createHandler(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkInfo,
        topicName,
        attestationSchema,
        subnetId,
        maxMessageSize);
  }

  private SafeFuture<Optional<Integer>> computeSubnetForAttestation(final Attestation attestation) {
    return recentChainData
        .retrieveStateInEffectAtSlot(attestation.getData().getSlot())
        .thenApply(
            state ->
                state.map(
                    s -> recentChainData.getSpec().computeSubnetForAttestation(s, attestation)));
  }
}
