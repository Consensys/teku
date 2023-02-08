/*
 * Copyright ConsenSys Software Inc., 2022
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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.BlockAndBlobsSidecarGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsDeneb extends GossipForkSubscriptionsCapella {

  private final OperationProcessor<SignedBeaconBlockAndBlobsSidecar> gossipedBlockAndBlobsProcessor;

  private BlockAndBlobsSidecarGossipManager blockAndBlobsSidecarGossipManager;

  public GossipForkSubscriptionsDeneb(
      final Fork fork,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final DiscoveryNetwork<?> discoveryNetwork,
      final RecentChainData recentChainData,
      final GossipEncoding gossipEncoding,
      final OperationProcessor<SignedBeaconBlock> blockProcessor,
      final OperationProcessor<SignedBeaconBlockAndBlobsSidecar> gossipedBlockAndBlobsProcessor,
      final OperationProcessor<ValidateableAttestation> attestationProcessor,
      final OperationProcessor<ValidateableAttestation> aggregateProcessor,
      final OperationProcessor<AttesterSlashing> attesterSlashingProcessor,
      final OperationProcessor<ProposerSlashing> proposerSlashingProcessor,
      final OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor,
      final OperationProcessor<SignedContributionAndProof>
          signedContributionAndProofOperationProcessor,
      final OperationProcessor<ValidateableSyncCommitteeMessage>
          syncCommitteeMessageOperationProcessor,
      final OperationProcessor<SignedBlsToExecutionChange>
          signedBlsToExecutionChangeOperationProcessor) {
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
        proposerSlashingProcessor,
        voluntaryExitProcessor,
        signedContributionAndProofOperationProcessor,
        syncCommitteeMessageOperationProcessor,
        signedBlsToExecutionChangeOperationProcessor);

    this.gossipedBlockAndBlobsProcessor = gossipedBlockAndBlobsProcessor;
  }

  @Override
  protected void addGossipManagers(final ForkInfo forkInfo) {
    // Phase0 without BlockGossipManager
    addAttestationGossipManager(forkInfo);
    addAggregateGossipManager(forkInfo);
    addVoluntaryExitGossipManager(forkInfo);
    addProposerSlashingGossipManager(forkInfo);
    addAttesterSlashingGossipManager(forkInfo);

    // Altair
    addSignedContributionAndProofGossipManager(forkInfo);
    addSyncCommitteeMessageGossipManager(forkInfo);

    // Capella
    addSignedBlsToExecutionChangeGossipManager(forkInfo);

    // Deneb
    addBlockAndBlobsSidecarGossipManager(forkInfo);
  }

  void addBlockAndBlobsSidecarGossipManager(final ForkInfo forkInfo) {
    blockAndBlobsSidecarGossipManager =
        new BlockAndBlobsSidecarGossipManager(
            recentChainData,
            spec,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            gossipedBlockAndBlobsProcessor,
            getMessageMaxSize());
    addGossipManager(blockAndBlobsSidecarGossipManager);
  }

  @Override
  public void publishBlockAndBlobsSidecar(
      final SignedBeaconBlockAndBlobsSidecar blockAndBlobsSidecar) {
    blockAndBlobsSidecarGossipManager.publishBlockAndBlobsSidecar(blockAndBlobsSidecar);
  }
}
