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

package tech.pegasys.teku.networking.eth2.gossip;

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.ssz.schema.SszSchema;

public class SignedContributionAndProofGossipManager
    extends AbstractGossipManager<SignedContributionAndProof> {

  public static String TOPIC_NAME = "sync_committee_contribution_and_proof";

  private final SszSchema<SignedContributionAndProof> gossipType;

  public SignedContributionAndProofGossipManager(
      final SchemaDefinitionsAltair schemaDefinitions,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<SignedContributionAndProof> processor,
      final GossipPublisher<SignedContributionAndProof> publisher) {
    super(TOPIC_NAME, asyncRunner, gossipNetwork, gossipEncoding, forkInfo, processor, publisher);
    gossipType = schemaDefinitions.getSignedContributionAndProofSchema();
  }

  @Override
  protected SszSchema<SignedContributionAndProof> getGossipType() {
    return gossipType;
  }
}
