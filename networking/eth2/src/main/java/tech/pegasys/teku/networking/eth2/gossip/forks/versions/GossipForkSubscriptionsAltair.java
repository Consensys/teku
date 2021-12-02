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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.GossipPublisher;
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
  private final GossipPublisher<SignedContributionAndProof>
      signedContributionAndProofGossipPublisher;
  private final OperationProcessor<ValidateableSyncCommitteeMessage>
      syncCommitteeMessageOperationProcessor;
  private final GossipPublisher<ValidateableSyncCommitteeMessage>
      syncCommitteeMessageGossipPublisher;
  private SyncCommitteeMessageGossipManager syncCommitteeMessageGossipManager;

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
      final GossipPublisher<AttesterSlashing> attesterSlashingGossipPublisher,
      final OperationProcessor<ProposerSlashing> proposerSlashingProcessor,
      final GossipPublisher<ProposerSlashing> proposerSlashingGossipPublisher,
      final OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor,
      final GossipPublisher<SignedVoluntaryExit> voluntaryExitGossipPublisher,
      final OperationProcessor<SignedContributionAndProof>
          signedContributionAndProofOperationProcessor,
      final GossipPublisher<SignedContributionAndProof> signedContributionAndProofGossipPublisher,
      final OperationProcessor<ValidateableSyncCommitteeMessage>
          syncCommitteeMessageOperationProcessor,
      final GossipPublisher<ValidateableSyncCommitteeMessage> syncCommitteeMessageGossipPublisher) {
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
        voluntaryExitGossipPublisher);
    this.signedContributionAndProofOperationProcessor =
        signedContributionAndProofOperationProcessor;
    this.signedContributionAndProofGossipPublisher = signedContributionAndProofGossipPublisher;
    this.syncCommitteeMessageOperationProcessor = syncCommitteeMessageOperationProcessor;
    this.syncCommitteeMessageGossipPublisher = syncCommitteeMessageGossipPublisher;
  }

  @Override
  protected void addGossipManagers(final ForkInfo forkInfo) {
    super.addGossipManagers(forkInfo);
    final SchemaDefinitionsAltair schemaDefinitions =
        SchemaDefinitionsAltair.required(spec.atEpoch(getActivationEpoch()).getSchemaDefinitions());
    addGossipManager(
        new SignedContributionAndProofGossipManager(
            recentChainData,
            schemaDefinitions,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            signedContributionAndProofOperationProcessor,
            signedContributionAndProofGossipPublisher,
            getMessageMaxSize()));

    final SyncCommitteeSubnetSubscriptions syncCommitteeSubnetSubscriptions =
        new SyncCommitteeSubnetSubscriptions(
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
            syncCommitteeSubnetSubscriptions,
            syncCommitteeMessageGossipPublisher);
    addGossipManager(syncCommitteeMessageGossipManager);
  }

  @Override
  public void publishSyncCommitteeMessage(final ValidateableSyncCommitteeMessage message) {
    syncCommitteeMessageGossipManager.publish(message);
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
