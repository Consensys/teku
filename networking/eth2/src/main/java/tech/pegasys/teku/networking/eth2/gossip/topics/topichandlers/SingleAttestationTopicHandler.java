/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers;

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SingleAttestationTopicHandler {

  public static Eth2TopicHandler<?> createHandler(
      final RecentChainData recentChainData,
      final AsyncRunner asyncRunner,
      final OperationProcessor<ValidateableAttestation> operationProcessor,
      final GossipEncoding gossipEncoding,
      final Bytes4 forkDigest,
      final String topicName,
      final int subnetId,
      final int maxMessageSize) {

    OperationProcessor<Attestation> convertingProcessor =
        attMessage ->
            operationProcessor.process(
                ValidateableAttestation.fromNetwork(
                    recentChainData.getSpec(), attMessage, subnetId));

    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        convertingProcessor,
        gossipEncoding,
        forkDigest,
        topicName,
        Attestation.SSZ_SCHEMA,
        maxMessageSize);
  }
}
