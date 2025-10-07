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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadBidGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipManager;
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
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsGloas extends GossipForkSubscriptionsFulu {

  private final OperationProcessor<SignedExecutionPayloadEnvelope> executionPayloadProcessor;
  private final OperationProcessor<PayloadAttestationMessage> payloadAttestationMessageProcessor;
  private final OperationProcessor<SignedExecutionPayloadBid> executionPayloadBidProcessor;

  private ExecutionPayloadGossipManager executionPayloadGossipManager;
  private PayloadAttestationMessageGossipManager payloadAttestationMessageGossipManager;
  private ExecutionPayloadBidGossipManager executionPayloadBidGossipManager;

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
      final OperationProcessor<SignedExecutionPayloadEnvelope> executionPayloadOperationProcessor,
      final OperationProcessor<PayloadAttestationMessage>
          payloadAttestationMessageOperationProcessor,
      final OperationProcessor<SignedExecutionPayloadBid> executionPayloadBidOperationProcessor,
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
    this.executionPayloadProcessor = executionPayloadOperationProcessor;
    this.payloadAttestationMessageProcessor = payloadAttestationMessageOperationProcessor;
    this.executionPayloadBidProcessor = executionPayloadBidOperationProcessor;
  }

  @Override
  protected void addGossipManagers(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    super.addGossipManagers(forkInfo, forkDigest);
    addExecutionPayloadGossipManager(forkInfo, forkDigest);
    addPayloadAttestationMessageGossipManager(forkInfo, forkDigest);
    addExecutionPayloadBidGossipManager(forkInfo, forkDigest);
  }

  void addExecutionPayloadGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    this.executionPayloadGossipManager =
        new ExecutionPayloadGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            executionPayloadProcessor,
            spec.getNetworkingConfig(),
            debugDataDumper);
    addGossipManager(executionPayloadGossipManager);
  }

  void addPayloadAttestationMessageGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    this.payloadAttestationMessageGossipManager =
        new PayloadAttestationMessageGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            payloadAttestationMessageProcessor,
            spec.getNetworkingConfig(),
            debugDataDumper);
    addGossipManager(payloadAttestationMessageGossipManager);
  }

  void addExecutionPayloadBidGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    this.executionPayloadBidGossipManager =
        new ExecutionPayloadBidGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            executionPayloadBidProcessor,
            spec.getNetworkingConfig(),
            debugDataDumper);
    addGossipManager(executionPayloadBidGossipManager);
  }

  @Override
  public void publishExecutionPayload(final SignedExecutionPayloadEnvelope message) {
    executionPayloadGossipManager.publish(message);
  }

  @Override
  public void publishPayloadAttestationMessage(final PayloadAttestationMessage message) {
    payloadAttestationMessageGossipManager.publish(message);
  }

  @Override
  public void publishExecutionPayloadBid(final SignedExecutionPayloadBid message) {
    executionPayloadBidGossipManager.publish(message);
  }
}
