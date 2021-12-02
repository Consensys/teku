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
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeSubnetSubscriptions extends CommitteeSubnetSubscriptions {

  private final SchemaDefinitionsAltair schemaDefinitions;
  private final AsyncRunner asyncRunner;
  private final OperationProcessor<ValidateableSyncCommitteeMessage> processor;
  private final ForkInfo forkInfo;
  private final int maxMessageSize;

  public SyncCommitteeSubnetSubscriptions(
      final RecentChainData recentChainData,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final SchemaDefinitionsAltair schemaDefinitions,
      final AsyncRunner asyncRunner,
      final OperationProcessor<ValidateableSyncCommitteeMessage> processor,
      final ForkInfo forkInfo,
      final int maxMessageSize) {
    super(recentChainData, gossipNetwork, gossipEncoding);
    this.schemaDefinitions = schemaDefinitions;
    this.asyncRunner = asyncRunner;
    this.processor = processor;
    this.forkInfo = forkInfo;
    this.maxMessageSize = maxMessageSize;
  }

  public SafeFuture<?> gossip(final SyncCommitteeMessage message, final int subnetId) {
    return gossipNetwork.gossip(
        GossipTopics.getSyncCommitteeSubnetTopic(
            forkInfo.getForkDigest(spec), subnetId, gossipEncoding),
        gossipEncoding.encode(message));
  }

  @Override
  protected Eth2TopicHandler<?> createTopicHandler(final int subnetId) {
    final OperationProcessor<SyncCommitteeMessage> convertingProcessor =
        message ->
            processor.process(ValidateableSyncCommitteeMessage.fromNetwork(message, subnetId));
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        convertingProcessor,
        gossipEncoding,
        forkInfo.getForkDigest(spec),
        GossipTopicName.getSyncCommitteeSubnetTopicName(subnetId),
        schemaDefinitions.getSyncCommitteeMessageSchema(),
        maxMessageSize);
  }
}
