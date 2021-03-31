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

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsPhase0 implements GossipForkSubscriptions {

  private final List<GossipManager> gossipManagers = new ArrayList<>();
  private final Fork fork;
  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final MetricsSystem metricsSystem;
  private final DiscoveryNetwork<?> discoveryNetwork;
  private final RecentChainData recentChainData;
  private final GossipEncoding gossipEncoding;

  // Upstream consumers
  private final OperationProcessor<SignedBeaconBlock> blockProcessor;
  private final OperationProcessor<ValidateableAttestation> attestationProcessor;
  private final OperationProcessor<ValidateableAttestation> aggregateProcessor;
  private final OperationProcessor<AttesterSlashing> attesterSlashingProcessor;
  private final GossipPublisher<AttesterSlashing> attesterSlashingGossipPublisher;
  private final OperationProcessor<ProposerSlashing> proposerSlashingProcessor;
  private final GossipPublisher<ProposerSlashing> proposerSlashingGossipPublisher;
  private final OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor;
  private final GossipPublisher<SignedVoluntaryExit> voluntaryExitGossipPublisher;

  private AttestationGossipManager attestationGossipManager;
  private AggregateGossipManager aggregateGossipManager;
  private BlockGossipManager blockGossipManager;

  public GossipForkSubscriptionsPhase0(
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
      final GossipPublisher<SignedVoluntaryExit> voluntaryExitGossipPublisher) {
    this.fork = fork;
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.metricsSystem = metricsSystem;
    this.discoveryNetwork = discoveryNetwork;
    this.recentChainData = recentChainData;
    this.gossipEncoding = gossipEncoding;
    this.blockProcessor = blockProcessor;
    this.attestationProcessor = attestationProcessor;
    this.aggregateProcessor = aggregateProcessor;
    this.attesterSlashingProcessor = attesterSlashingProcessor;
    this.attesterSlashingGossipPublisher = attesterSlashingGossipPublisher;
    this.proposerSlashingProcessor = proposerSlashingProcessor;
    this.proposerSlashingGossipPublisher = proposerSlashingGossipPublisher;
    this.voluntaryExitProcessor = voluntaryExitProcessor;
    this.voluntaryExitGossipPublisher = voluntaryExitGossipPublisher;
  }

  @Override
  public UInt64 getActivationEpoch() {
    return fork.getEpoch();
  }

  @Override
  public void startGossip(final Bytes32 genesisValidatorsRoot) {
    final ForkInfo forkInfo = new ForkInfo(fork, genesisValidatorsRoot);

    AttestationSubnetSubscriptions attestationSubnetSubscriptions =
        new AttestationSubnetSubscriptions(
            asyncRunner, discoveryNetwork, gossipEncoding, recentChainData, attestationProcessor);

    blockGossipManager =
        new BlockGossipManager(
            spec, asyncRunner, discoveryNetwork, gossipEncoding, forkInfo, blockProcessor);
    gossipManagers.add(blockGossipManager);

    attestationGossipManager =
        new AttestationGossipManager(metricsSystem, attestationSubnetSubscriptions);
    gossipManagers.add(attestationGossipManager);

    aggregateGossipManager =
        new AggregateGossipManager(
            asyncRunner, discoveryNetwork, gossipEncoding, forkInfo, aggregateProcessor);
    gossipManagers.add(aggregateGossipManager);

    gossipManagers.add(
        new VoluntaryExitGossipManager(
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            voluntaryExitProcessor,
            voluntaryExitGossipPublisher));

    gossipManagers.add(
        new ProposerSlashingGossipManager(
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            proposerSlashingProcessor,
            proposerSlashingGossipPublisher));

    gossipManagers.add(
        new AttesterSlashingGossipManager(
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            attesterSlashingProcessor,
            attesterSlashingGossipPublisher));
  }

  @Override
  public void stopGossip() {
    gossipManagers.forEach(GossipManager::shutdown);
  }

  @Override
  public void publishAttestation(final ValidateableAttestation attestation) {
    attestationGossipManager.onNewAttestation(attestation);
    aggregateGossipManager.onNewAggregate(attestation);
  }

  @Override
  public void publishBlock(final SignedBeaconBlock block) {
    blockGossipManager.publishBlock(block);
  }

  @Override
  public void subscribeToAttestationSubnetId(final int subnetId) {
    attestationGossipManager.subscribeToSubnetId(subnetId);
  }

  @Override
  public void unsubscribeFromAttestationSubnetId(final int subnetId) {
    attestationGossipManager.subscribeToSubnetId(subnetId);
  }
}
