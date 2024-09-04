/*
 * Copyright Consensys Software Inc., 2022
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
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadHeaderManager;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadManager;
import tech.pegasys.teku.networking.eth2.gossip.PayloadAttestationManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsEip7732 extends GossipForkSubscriptionsElectra {

  private final OperationProcessor<SignedExecutionPayloadEnvelope> executionPayloadProcessor;
  private final OperationProcessor<PayloadAttestationMessage> payloadAttestationProcessor;
  private final OperationProcessor<SignedExecutionPayloadHeader> executionPayloadHeaderProcessor;

  private ExecutionPayloadManager executionPayloadManager;
  private PayloadAttestationManager payloadAttestationManager;
  private ExecutionPayloadHeaderManager executionPayloadHeaderManager;

  public GossipForkSubscriptionsEip7732(
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
      final OperationProcessor<SignedExecutionPayloadEnvelope> executionPayloadProcessor,
      final OperationProcessor<PayloadAttestationMessage> payloadAttestationProcessor,
      final OperationProcessor<SignedExecutionPayloadHeader> executionPayloadHeaderProcessor,
      final DebugDataDumper debugDataDumper) {
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
        debugDataDumper);
    this.executionPayloadProcessor = executionPayloadProcessor;
    this.payloadAttestationProcessor = payloadAttestationProcessor;
    this.executionPayloadHeaderProcessor = executionPayloadHeaderProcessor;
  }

  @Override
  protected void addGossipManagers(final ForkInfo forkInfo) {
    super.addGossipManagers(forkInfo);
    addExecutionPayloadManager(forkInfo);
    addPayloadAttestationManager(forkInfo);
    addExecutionPayloadHeaderManager(forkInfo);
  }

  void addExecutionPayloadManager(final ForkInfo forkInfo) {
    executionPayloadManager =
        new ExecutionPayloadManager(
            recentChainData,
            spec,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            executionPayloadProcessor,
            debugDataDumper);
    addGossipManager(executionPayloadManager);
  }

  void addPayloadAttestationManager(final ForkInfo forkInfo) {
    payloadAttestationManager =
        new PayloadAttestationManager(
            recentChainData,
            spec,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            payloadAttestationProcessor,
            debugDataDumper);
    addGossipManager(payloadAttestationManager);
  }

  void addExecutionPayloadHeaderManager(final ForkInfo forkInfo) {
    executionPayloadHeaderManager =
        new ExecutionPayloadHeaderManager(
            recentChainData,
            spec,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            executionPayloadHeaderProcessor,
            debugDataDumper);
    addGossipManager(executionPayloadHeaderManager);
  }

  @Override
  public void publishExecutionPayloadMessage(final SignedExecutionPayloadEnvelope message) {
    executionPayloadManager.publishExecutionPayload(message);
  }

  @Override
  public void publishPayloadAttestation(final PayloadAttestationMessage payloadAttestationMessage) {
    payloadAttestationManager.publishAttestationPayload(payloadAttestationMessage);
  }

  @Override
  public void publishExecutionPayloadHeaderMessage(final SignedExecutionPayloadHeader message) {
    executionPayloadHeaderManager.publishExecutionPayloadHeader(message);
  }
}
