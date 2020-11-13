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

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class SingleAttestationTopicHandler extends Eth2TopicHandler<ValidateableAttestation> {
  private final int subnetId;

  public SingleAttestationTopicHandler(
      final AsyncRunner asyncRunner,
      final OperationProcessor<ValidateableAttestation> operationProcessor,
      final GossipEncoding gossipEncoding,
      final Bytes4 forkDigest,
      final String topicName,
      final int subnetId) {
    super(
        asyncRunner,
        operationProcessor,
        gossipEncoding,
        forkDigest,
        topicName,
        ValidateableAttestation.class);
    this.subnetId = subnetId;
  }

  @Override
  public PreparedGossipMessage prepareMessage(Bytes payload) {
    return getGossipEncoding().prepareMessage(payload, Attestation.class);
  }

  @Override
  public ValidateableAttestation deserialize(PreparedGossipMessage message)
      throws DecodingException {
    Attestation attestation = getGossipEncoding().decodeMessage(message, Attestation.class);
    return ValidateableAttestation.fromNetwork(attestation, subnetId);
  }
}
