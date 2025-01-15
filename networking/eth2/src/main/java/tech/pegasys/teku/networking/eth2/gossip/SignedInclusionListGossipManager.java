/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7805;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SignedInclusionListGossipManager extends AbstractGossipManager<SignedInclusionList> {

  public SignedInclusionListGossipManager(
      final RecentChainData recentChainData,
      final SchemaDefinitionsEip7805 schemaDefinitions,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<SignedInclusionList> processor,
      final DebugDataDumper debugDataDumper) {
    super(
        recentChainData,
        GossipTopicName.INCLUSION_LIST,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        processor,
        schemaDefinitions.getSignedInclusionListSchema(),
        signedInclusionList -> Optional.of(signedInclusionList.getMessage().getSlot()),
        signedInclusionList -> spec.computeEpochAtSlot(signedInclusionList.getMessage().getSlot()),
        spec.getNetworkingConfig(),
        GossipFailureLogger.createNonSuppressing(GossipTopicName.INCLUSION_LIST.toString()),
        debugDataDumper);
  }

  public void publishSignedInclusionList(final SignedInclusionList message) {
    publishMessage(message);
  }
}
