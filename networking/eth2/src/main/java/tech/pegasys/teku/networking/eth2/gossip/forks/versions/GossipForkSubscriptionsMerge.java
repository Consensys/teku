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

package tech.pegasys.teku.networking.eth2.gossip.forks.versions;

import static tech.pegasys.teku.util.config.Constants.GOSSIP_MAX_SIZE_MERGE;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.GossipPublisher;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsMerge extends GossipForkSubscriptionsAltair {

  public GossipForkSubscriptionsMerge(
      Fork fork,
      Spec spec,
      AsyncRunner asyncRunner,
      MetricsSystem metricsSystem,
      DiscoveryNetwork<?> discoveryNetwork,
      RecentChainData recentChainData,
      GossipEncoding gossipEncoding,
      OperationProcessor<SignedBeaconBlock> blockProcessor,
      OperationProcessor<ValidateableAttestation> attestationProcessor,
      OperationProcessor<ValidateableAttestation> aggregateProcessor,
      OperationProcessor<AttesterSlashing> attesterSlashingProcessor,
      GossipPublisher<AttesterSlashing> attesterSlashingGossipPublisher,
      OperationProcessor<ProposerSlashing> proposerSlashingProcessor,
      GossipPublisher<ProposerSlashing> proposerSlashingGossipPublisher,
      OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor,
      GossipPublisher<SignedVoluntaryExit> voluntaryExitGossipPublisher,
      OperationProcessor<SignedContributionAndProof> signedContributionAndProofOperationProcessor,
      GossipPublisher<SignedContributionAndProof> signedContributionAndProofGossipPublisher,
      OperationProcessor<ValidateableSyncCommitteeMessage> syncCommitteeMessageOperationProcessor,
      GossipPublisher<ValidateableSyncCommitteeMessage> syncCommitteeMessageGossipPublisher) {
    super(
        fork,
        spec,
        asyncRunner,
        metricsSystem,
        discoveryNetwork,
        recentChainData,
        gossipEncoding,
        blockProcessor,
        attestationProcessor,
        aggregateProcessor,
        attesterSlashingProcessor,
        attesterSlashingGossipPublisher,
        proposerSlashingProcessor,
        proposerSlashingGossipPublisher,
        voluntaryExitProcessor,
        voluntaryExitGossipPublisher,
        signedContributionAndProofOperationProcessor,
        signedContributionAndProofGossipPublisher,
        syncCommitteeMessageOperationProcessor,
        syncCommitteeMessageGossipPublisher);
  }

  @Override
  protected int getMessageMaxSize() {
    return GOSSIP_MAX_SIZE_MERGE;
  }
}
