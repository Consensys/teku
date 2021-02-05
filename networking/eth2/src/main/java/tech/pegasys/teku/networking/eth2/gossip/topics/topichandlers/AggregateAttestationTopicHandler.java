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

import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class AggregateAttestationTopicHandler {

  public static String TOPIC_NAME = "beacon_aggregate_and_proof";

  public static Eth2TopicHandler<?> createHandler(
      final AsyncRunner asyncRunner,
      final OperationProcessor<ValidateableAttestation> operationProcessor,
      final GossipEncoding gossipEncoding,
      final Bytes4 forkDigest) {

    OperationProcessor<SignedAggregateAndProof> convertingProcessor =
        proofMessage ->
            operationProcessor.process(
                ValidateableAttestation.aggregateFromValidator(proofMessage));
    return new Eth2TopicHandler<>(
        asyncRunner,
        convertingProcessor,
        gossipEncoding,
        forkDigest,
        TOPIC_NAME,
        SignedAggregateAndProof.TYPE);
  }
}
