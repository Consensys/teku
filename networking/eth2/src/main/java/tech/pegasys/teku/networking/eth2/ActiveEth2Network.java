/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.AggregateGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.AttestationGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.AttesterSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.GossipPublisher;
import tech.pegasys.teku.networking.eth2.gossip.ProposerSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.VoluntaryExitGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.p2p.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ActiveEth2Network extends DelegatingP2PNetwork<Eth2Peer> implements Eth2Network {
  private static final Logger LOG = LogManager.getLogger();

  private final AsyncRunner asyncRunner;
  private final MetricsSystem metricsSystem;
  private final DiscoveryNetwork<?> discoveryNetwork;
  private final Eth2PeerManager peerManager;
  private final EventBus eventBus;
  private final RecentChainData recentChainData;
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
  private final GossipEncoding gossipEncoding;
  private final AttestationSubnetService attestationSubnetService;
  private final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider;
  private final Set<Integer> pendingSubnetSubscriptions = new HashSet<>();

  // Gossip managers
  private BlockGossipManager blockGossipManager;
  private AttestationGossipManager attestationGossipManager;
  private AggregateGossipManager aggregateGossipManager;
  private VoluntaryExitGossipManager voluntaryExitGossipManager;
  private ProposerSlashingGossipManager proposerSlashingGossipManager;
  private AttesterSlashingGossipManager attesterSlashingGossipManager;

  private long discoveryNetworkAttestationSubnetsSubscription;

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

  public ActiveEth2Network(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final DiscoveryNetwork<?> discoveryNetwork,
      final Eth2PeerManager peerManager,
      final EventBus eventBus,
      final RecentChainData recentChainData,
      final GossipEncoding gossipEncoding,
      final AttestationSubnetService attestationSubnetService,
      final OperationProcessor<SignedBeaconBlock> blockProcessor,
      final OperationProcessor<ValidateableAttestation> attestationProcessor,
      final OperationProcessor<ValidateableAttestation> aggregateProcessor,
      final OperationProcessor<AttesterSlashing> attesterSlashingProcessor,
      final GossipPublisher<AttesterSlashing> attesterSlashingGossipPublisher,
      final OperationProcessor<ProposerSlashing> proposerSlashingProcessor,
      final GossipPublisher<ProposerSlashing> proposerSlashingGossipPublisher,
      final OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor,
      final GossipPublisher<SignedVoluntaryExit> voluntaryExitGossipPublisher,
      final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider) {
    super(discoveryNetwork);
    this.asyncRunner = asyncRunner;
    this.metricsSystem = metricsSystem;
    this.discoveryNetwork = discoveryNetwork;
    this.peerManager = peerManager;
    this.eventBus = eventBus;
    this.recentChainData = recentChainData;
    this.gossipEncoding = gossipEncoding;
    this.attestationSubnetService = attestationSubnetService;
    this.blockProcessor = blockProcessor;
    this.attestationProcessor = attestationProcessor;
    this.aggregateProcessor = aggregateProcessor;
    this.attesterSlashingProcessor = attesterSlashingProcessor;
    this.attesterSlashingGossipPublisher = attesterSlashingGossipPublisher;
    this.proposerSlashingProcessor = proposerSlashingProcessor;
    this.proposerSlashingGossipPublisher = proposerSlashingGossipPublisher;
    this.voluntaryExitProcessor = voluntaryExitProcessor;
    this.voluntaryExitGossipPublisher = voluntaryExitGossipPublisher;
    this.processedAttestationSubscriptionProvider = processedAttestationSubscriptionProvider;
  }

  @Override
  public SafeFuture<?> start() {
    // Set the current fork info prior to discovery starting up.
    final ForkInfo currentForkInfo =
        recentChainData
            .getHeadForkInfo()
            .orElseThrow(
                () ->
                    new IllegalStateException("Can not start Eth2Network before genesis is known"));
    discoveryNetwork.setForkInfo(currentForkInfo, recentChainData.getNextFork());
    return super.start().thenAccept(r -> startup());
  }

  private synchronized void startup() {
    state.set(State.RUNNING);

    final ForkInfo forkInfo = recentChainData.getHeadForkInfo().orElseThrow();

    AttestationSubnetSubscriptions attestationSubnetSubscriptions =
        new AttestationSubnetSubscriptions(
            asyncRunner, discoveryNetwork, gossipEncoding, recentChainData, attestationProcessor);

    blockGossipManager =
        new BlockGossipManager(
            asyncRunner, discoveryNetwork, gossipEncoding, forkInfo, eventBus, blockProcessor);

    attestationGossipManager =
        new AttestationGossipManager(metricsSystem, attestationSubnetSubscriptions);

    aggregateGossipManager =
        new AggregateGossipManager(
            asyncRunner, discoveryNetwork, gossipEncoding, forkInfo, aggregateProcessor);

    voluntaryExitGossipManager =
        new VoluntaryExitGossipManager(
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            voluntaryExitProcessor,
            voluntaryExitGossipPublisher);

    proposerSlashingGossipManager =
        new ProposerSlashingGossipManager(
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            proposerSlashingProcessor,
            proposerSlashingGossipPublisher);

    attesterSlashingGossipManager =
        new AttesterSlashingGossipManager(
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            attesterSlashingProcessor,
            attesterSlashingGossipPublisher);

    discoveryNetworkAttestationSubnetsSubscription =
        attestationSubnetService.subscribeToUpdates(
            discoveryNetwork::setLongTermAttestationSubnetSubscriptions);

    pendingSubnetSubscriptions.forEach(this::subscribeToAttestationSubnetId);
    pendingSubnetSubscriptions.clear();

    processedAttestationSubscriptionProvider.subscribe(attestationGossipManager::onNewAttestation);
    processedAttestationSubscriptionProvider.subscribe(aggregateGossipManager::onNewAggregate);
  }

  @Override
  public synchronized SafeFuture<?> stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return SafeFuture.COMPLETE;
    }
    blockGossipManager.shutdown();
    attestationGossipManager.shutdown();
    aggregateGossipManager.shutdown();
    voluntaryExitGossipManager.shutdown();
    proposerSlashingGossipManager.shutdown();
    attesterSlashingGossipManager.shutdown();
    attestationSubnetService.unsubscribe(discoveryNetworkAttestationSubnetsSubscription);
    return peerManager
        .sendGoodbyeToPeers()
        .exceptionally(
            error -> {
              LOG.debug("Failed to send goodbye to peers on shutdown", error);
              return null;
            })
        .thenCompose(__ -> super.stop());
  }

  @Override
  public Optional<Eth2Peer> getPeer(final NodeId id) {
    return peerManager.getPeer(id);
  }

  @Override
  public Stream<Eth2Peer> streamPeers() {
    return peerManager.streamPeers();
  }

  @Override
  public int getPeerCount() {
    return Math.toIntExact(streamPeers().count());
  }

  @Override
  public long subscribeConnect(final PeerConnectedSubscriber<Eth2Peer> subscriber) {
    return peerManager.subscribeConnect(subscriber);
  }

  @Override
  public void unsubscribeConnect(final long subscriptionId) {
    peerManager.unsubscribeConnect(subscriptionId);
  }

  public BeaconChainMethods getBeaconChainMethods() {
    return peerManager.getBeaconChainMethods();
  }

  @Override
  public synchronized void subscribeToAttestationSubnetId(final int subnetId) {
    if (attestationGossipManager == null) {
      pendingSubnetSubscriptions.add(subnetId);
    } else {
      attestationGossipManager.subscribeToSubnetId(subnetId);
    }
  }

  @Override
  public synchronized void unsubscribeFromAttestationSubnetId(final int subnetId) {
    if (attestationGossipManager == null) {
      pendingSubnetSubscriptions.remove(subnetId);
    } else {
      attestationGossipManager.unsubscribeFromSubnetId(subnetId);
    }
  }

  @Override
  public void setLongTermAttestationSubnetSubscriptions(final Iterable<Integer> subnetIndices) {
    attestationSubnetService.updateSubscriptions(subnetIndices);
  }

  @Override
  public MetadataMessage getMetadata() {
    return peerManager.getMetadataMessage();
  }

  @VisibleForTesting
  Eth2PeerManager getPeerManager() {
    return peerManager;
  }
}
