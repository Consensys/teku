/*
 * Copyright Consensys Software Inc., 2025
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
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.PayloadAttestationMessageGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsGloas extends GossipForkSubscriptionsFulu {

  private final OperationProcessor<PayloadAttestationMessage> payloadAttestationProcessor;
  private Optional<PayloadAttestationMessageGossipManager> payloadAttestationMessageGossipManager =
      Optional.empty();

  public GossipForkSubscriptionsGloas(
      final Fork fork,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final DiscoveryNetwork<?> discoveryNetwork,
      final RecentChainData recentChainData,
      final GossipEncoding gossipEncoding,
      final OperationProcessor<SignedBeaconBlock> blockProcessor,
      final OperationProcessor<BlobSidecar> blobSidecarProcessor,
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
          signedBlsToExecutionChangeOperationProcessor,
      final OperationProcessor<DataColumnSidecar> dataColumnSidecarOperationProcessor,
      final OperationProcessor<PayloadAttestationMessage>
          payloadAttestationMessageOperationProcessor,
      final DebugDataDumper debugDataDumper,
      final DasGossipLogger dasGossipLogger) {
    super(
        fork,
        spec,
        asyncRunner,
        metricsSystem,
        discoveryNetwork,
        recentChainData,
        gossipEncoding,
        blockProcessor,
        blobSidecarProcessor,
        attestationProcessor,
        aggregateProcessor,
        attesterSlashingProcessor,
        proposerSlashingProcessor,
        voluntaryExitProcessor,
        signedContributionAndProofOperationProcessor,
        syncCommitteeMessageOperationProcessor,
        signedBlsToExecutionChangeOperationProcessor,
        dataColumnSidecarOperationProcessor,
        debugDataDumper,
        dasGossipLogger);

    this.payloadAttestationProcessor = payloadAttestationMessageOperationProcessor;
  }

  void addPayloadAttestationMessageGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atEpoch(getActivationEpoch()).getSchemaDefinitions());

    final PayloadAttestationMessageGossipManager gossipManager =
        new PayloadAttestationMessageGossipManager(
            recentChainData,
            schemaDefinitions,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            payloadAttestationProcessor,
            spec.getNetworkingConfig(),
            debugDataDumper);

    addGossipManager(gossipManager);
    this.payloadAttestationMessageGossipManager = Optional.of(gossipManager);
  }

  @Override
  protected void addGossipManagers(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    super.addGossipManagers(forkInfo, forkDigest);
    addPayloadAttestationMessageGossipManager(forkInfo, forkDigest);
  }

  @Override
  public void publishPayloadAttestationMessage(final PayloadAttestationMessage message) {
    payloadAttestationMessageGossipManager.ifPresent(
        gossipManager -> gossipManager.publish(message));
  }

  @VisibleForTesting
  void setPayloadAttestationMessageGossipManager(
      final Optional<PayloadAttestationMessageGossipManager>
          payloadAttestationMessageGossipManager) {
    this.payloadAttestationMessageGossipManager = payloadAttestationMessageGossipManager;
  }
}
