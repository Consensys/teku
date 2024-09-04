/*
 * Copyright Consensys Software Inc., 2024
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

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadHeaderEip7732;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionPayloadHeaderManager
    extends AbstractGossipManager<SignedExecutionPayloadHeader> {

  public ExecutionPayloadHeaderManager(
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<SignedExecutionPayloadHeader> processor,
      final DebugDataDumper debugDataDumper) {
    super(
        recentChainData,
        GossipTopicName.EXECUTION_PAYLOAD_HEADER,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        processor,
        SchemaDefinitionsEip7732.required(
                spec.atEpoch(forkInfo.getFork().getEpoch()).getSchemaDefinitions())
            .getSignedExecutionPayloadHeaderSchema(),
        executionPayloadHeader -> {
          final UInt64 slot =
              executionPayloadHeader
                  .getMessage()
                  .toVersionEip7732()
                  .map(ExecutionPayloadHeaderEip7732::getSlot)
                  .orElse(UInt64.ZERO);
          return spec.computeEpochAtSlot(slot);
        },
        spec.getNetworkingConfig(),
        debugDataDumper);
  }

  public void publishExecutionPayloadHeader(final SignedExecutionPayloadHeader message) {
    publishMessage(message);
  }
}
