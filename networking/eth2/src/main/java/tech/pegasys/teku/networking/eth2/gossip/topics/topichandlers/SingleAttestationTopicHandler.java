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
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.TopicNames;

public class SingleAttestationTopicHandler
    extends Eth2TopicHandler<Attestation, ValidateableAttestation> {

  private final int subnetId;

  public SingleAttestationTopicHandler(
      final AsyncRunner asyncRunner,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final int subnetId,
      final OperationProcessor<ValidateableAttestation> gossipedAttestationProcessor) {
    super(asyncRunner, gossipedAttestationProcessor, gossipEncoding, forkInfo.getForkDigest());
    this.subnetId = subnetId;
  }

  @Override
  protected ValidateableAttestation wrapMessage(Attestation deserialized) {
    return ValidateableAttestation.fromNetwork(deserialized, subnetId);
  }

  @Override
  public String getTopicName() {
    return TopicNames.getAttestationSubnetTopicName(subnetId);
  }

  @Override
  public Class<Attestation> getValueType() {
    return Attestation.class;
  }
}
