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

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.gossip.SignedBlsToExecutionChangeGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsCapella extends GossipForkSubscriptionsBellatrix {

  private final boolean blsToExecutionChangesSubnetEnabled;
  private final OperationProcessor<SignedBlsToExecutionChange>
      signedBlsToExecutionChangeOperationProcessor;
  private Optional<SignedBlsToExecutionChangeGossipManager>
      signedBlsToExecutionChangeGossipManager = Optional.empty();

  public GossipForkSubscriptionsCapella(
      final Fork fork,
      final Spec spec,
      final P2PConfig p2PConfig,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final DiscoveryNetwork<?> discoveryNetwork,
      final RecentChainData recentChainData,
      final GossipEncoding gossipEncoding,
      final OperationProcessor<SignedBeaconBlock> blockProcessor,
      final OperationProcessor<ValidatableAttestation> attestationProcessor,
      final OperationProcessor<ValidatableAttestation> aggregateProcessor,
      final OperationProcessor<AttesterSlashing> attesterSlashingProcessor,
      final OperationProcessor<ProposerSlashing> proposerSlashingProcessor,
      final OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor,
      final OperationProcessor<SignedContributionAndProof>
          signedContributionAndProofOperationProcessor,
      final OperationProcessor<ValidatableSyncCommitteeMessage>
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
        syncCommitteeMessageOperationProcessor);

    this.blsToExecutionChangesSubnetEnabled = p2PConfig.isBlsToExecutionChangesSubnetEnabled();
    this.signedBlsToExecutionChangeOperationProcessor =
        signedBlsToExecutionChangeOperationProcessor;
  }

  void addSignedBlsToExecutionChangeGossipManager(final ForkInfo forkInfo) {
    if (blsToExecutionChangesSubnetEnabled) {
      final SchemaDefinitionsCapella schemaDefinitions =
          SchemaDefinitionsCapella.required(
              spec.atEpoch(getActivationEpoch()).getSchemaDefinitions());

      final SignedBlsToExecutionChangeGossipManager gossipManager =
          new SignedBlsToExecutionChangeGossipManager(
              recentChainData,
              schemaDefinitions,
              asyncRunner,
              discoveryNetwork,
              gossipEncoding,
              forkInfo,
              signedBlsToExecutionChangeOperationProcessor,
              spec.getNetworkingConfig());

      addGossipManager(gossipManager);
      this.signedBlsToExecutionChangeGossipManager = Optional.of(gossipManager);
    }
  }

  @Override
  protected void addGossipManagers(final ForkInfo forkInfo) {
    super.addGossipManagers(forkInfo);
    addSignedBlsToExecutionChangeGossipManager(forkInfo);
  }

  @Override
  public void publishSignedBlsToExecutionChangeMessage(final SignedBlsToExecutionChange message) {
    signedBlsToExecutionChangeGossipManager.ifPresent(
        gossipManager -> gossipManager.publish(message));
  }

  @VisibleForTesting
  Optional<SignedBlsToExecutionChangeGossipManager> getSignedBlsToExecutionChangeGossipManager() {
    return signedBlsToExecutionChangeGossipManager;
  }

  @VisibleForTesting
  void setSignedBlsToExecutionChangeGossipManager(
      final Optional<SignedBlsToExecutionChangeGossipManager>
          signedBlsToExecutionChangeGossipManager) {
    this.signedBlsToExecutionChangeGossipManager = signedBlsToExecutionChangeGossipManager;
  }
}
