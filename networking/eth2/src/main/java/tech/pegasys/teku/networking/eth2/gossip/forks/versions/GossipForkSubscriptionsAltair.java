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
import tech.pegasys.teku.networking.eth2.gossip.SignedContributionAndProofGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.SyncCommitteeMessageGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubnetSubscriptions;
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
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeStateUtils;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsAltair extends GossipForkSubscriptionsPhase0 {

  private final OperationProcessor<SignedContributionAndProof>
      signedContributionAndProofOperationProcessor;
  private final OperationProcessor<ValidateableSyncCommitteeMessage>
      syncCommitteeMessageOperationProcessor;
  private SyncCommitteeMessageGossipManager syncCommitteeMessageGossipManager;
  private SignedContributionAndProofGossipManager syncCommitteeContributionGossipManager;

  public GossipForkSubscriptionsAltair(
      final Fork fork,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final DiscoveryNetwork<?> discoveryNetwork,
      final RecentChainData recentChainData,
      final GossipEncoding gossipEncoding,
      final OperationProcessor<SignedBeaconBlock> blockProcessor,
      final OperationProcessor<ValidateableAttestation> attestationProcessor,
      final OperationProcessor<ValidateableAttestation> aggregateProcessor,
      final OperationProcessor<AttesterSlashing> attesterSlashingProcessor,
      final OperationProcessor<ProposerSlashing> proposerSlashingProcessor,
      final OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor,
      final OperationProcessor<SignedContributionAndProof>
          signedContributionAndProofOperationProcessor,
      final OperationProcessor<ValidateableSyncCommitteeMessage>
          syncCommitteeMessageOperationProcessor) {
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
        voluntaryExitProcessor);
    this.signedContributionAndProofOperationProcessor =
        signedContributionAndProofOperationProcessor;
    this.syncCommitteeMessageOperationProcessor = syncCommitteeMessageOperationProcessor;
  }

  void addSignedContributionAndProofGossipManager(final ForkInfo forkInfo) {
    final SchemaDefinitionsAltair schemaDefinitions =
        SchemaDefinitionsAltair.required(spec.atEpoch(getActivationEpoch()).getSchemaDefinitions());
    syncCommitteeContributionGossipManager =
        new SignedContributionAndProofGossipManager(
            recentChainData,
            schemaDefinitions,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            signedContributionAndProofOperationProcessor,
            getMessageMaxSize());
    addGossipManager(syncCommitteeContributionGossipManager);
  }

  void addSyncCommitteeMessageGossipManager(final ForkInfo forkInfo) {
    final SchemaDefinitionsAltair schemaDefinitions =
        SchemaDefinitionsAltair.required(spec.atEpoch(getActivationEpoch()).getSchemaDefinitions());
    final SyncCommitteeSubnetSubscriptions syncCommitteeSubnetSubscriptions =
        new SyncCommitteeSubnetSubscriptions(
            spec,
            recentChainData,
            discoveryNetwork,
            gossipEncoding,
            schemaDefinitions,
            asyncRunner,
            syncCommitteeMessageOperationProcessor,
            forkInfo,
            getMessageMaxSize());
    syncCommitteeMessageGossipManager =
        new SyncCommitteeMessageGossipManager(
            metricsSystem,
            spec,
            new SyncCommitteeStateUtils(spec, recentChainData),
            syncCommitteeSubnetSubscriptions);
    addGossipManager(syncCommitteeMessageGossipManager);
  }

  @Override
  protected void addGossipManagers(final ForkInfo forkInfo) {
    super.addGossipManagers(forkInfo);
    addSignedContributionAndProofGossipManager(forkInfo);
    addSyncCommitteeMessageGossipManager(forkInfo);
  }

  @Override
  public void publishSyncCommitteeMessage(final ValidateableSyncCommitteeMessage message) {
    syncCommitteeMessageGossipManager.publish(message);
  }

  @Override
  public void publishSyncCommitteeContribution(final SignedContributionAndProof message) {
    syncCommitteeContributionGossipManager.publishContribution(message);
  }

  @Override
  public void subscribeToSyncCommitteeSubnet(final int subnetId) {
    syncCommitteeMessageGossipManager.subscribeToSubnetId(subnetId);
  }

  @Override
  public void unsubscribeFromSyncCommitteeSubnet(final int subnetId) {
    syncCommitteeMessageGossipManager.unsubscribeFromSubnetId(subnetId);
  }
}
