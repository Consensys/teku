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

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeSubnetSubscriptions extends CommitteeSubnetSubscriptions {

  private final SchemaDefinitionsAltair schemaDefinitions;
  private final AsyncRunner asyncRunner;
  private final OperationProcessor<ValidateableSyncCommitteeSignature> processor;
  private final ForkInfo forkInfo;

  public SyncCommitteeSubnetSubscriptions(
      final RecentChainData recentChainData,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final SchemaDefinitionsAltair schemaDefinitions,
      final AsyncRunner asyncRunner,
      final OperationProcessor<ValidateableSyncCommitteeSignature> processor,
      final ForkInfo forkInfo) {
    super(recentChainData, gossipNetwork, gossipEncoding);
    this.schemaDefinitions = schemaDefinitions;
    this.asyncRunner = asyncRunner;
    this.processor = processor;
    this.forkInfo = forkInfo;
  }

  public SafeFuture<?> gossip(final SyncCommitteeSignature signature, final int subnetId) {
    return gossipNetwork.gossip(
        GossipTopics.getSyncCommitteeSubnetTopic(
            forkInfo.getForkDigest(), subnetId, gossipEncoding),
        gossipEncoding.encode(signature));
  }

  @Override
  protected Eth2TopicHandler<?> createTopicHandler(final int subnetId) {
    final OperationProcessor<SyncCommitteeSignature> convertingProcessor =
        message ->
            processor.process(ValidateableSyncCommitteeSignature.fromNetwork(message, subnetId));
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        convertingProcessor,
        gossipEncoding,
        forkInfo.getForkDigest(),
        GossipTopicName.getSyncCommitteeSubnetTopicName(subnetId),
        schemaDefinitions.getSyncCommitteeSignatureSchema());
  }
}
