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
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class PayloadAttestationMessageGossipManager
    extends AbstractGossipManager<PayloadAttestationMessage> {

  public PayloadAttestationMessageGossipManager(
      final RecentChainData recentChainData,
      final SchemaDefinitionsGloas schemaDefinitions,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<PayloadAttestationMessage> processor,
      final NetworkingSpecConfig networkingConfig,
      final DebugDataDumper debugDataDumper) {
    super(
        recentChainData,
        GossipTopicName.PAYLOAD_ATTESTATION_MESSAGE,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        processor,
        schemaDefinitions.getPayloadAttestationMessageSchema(),
        message -> Optional.of(message.getData().getSlot()),
        message -> recentChainData.getSpec().computeEpochAtSlot(message.getData().getSlot()),
        networkingConfig,
        GossipFailureLogger.createSuppressing(
            GossipTopicName.PAYLOAD_ATTESTATION_MESSAGE.toString()),
        debugDataDumper);
  }

  public void publish(final PayloadAttestationMessage message) {
    publishMessage(message);
  }
}
