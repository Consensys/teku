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
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.operationsignatureverifiers.ProposerSlashingSignatureVerifier;
import tech.pegasys.teku.core.operationsignatureverifiers.VoluntaryExitSignatureVerifier;
import tech.pegasys.teku.core.operationvalidators.AttesterSlashingStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.ProposerSlashingStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.VoluntaryExitStateTransitionValidator;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.networking.eth2.gossip.AggregateGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.AttestationGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.AttestationSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.AttesterSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ProposerSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.VoluntaryExitGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipedOperationConsumer;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.gossip.topics.VerifiedBlockAttestationsSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttesterSlashingValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.BlockValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.ProposerSlashingValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.SignedAggregateAndProofValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.VoluntaryExitValidator;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.p2p.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.async.SafeFuture;

public class ActiveEth2Network extends DelegatingP2PNetwork<Eth2Peer> implements Eth2Network {

  private final DiscoveryNetwork<?> discoveryNetwork;
  private final Eth2PeerManager peerManager;
  private final EventBus eventBus;
  private final RecentChainData recentChainData;
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
  private final GossipEncoding gossipEncoding;
  private final AttestationSubnetService attestationSubnetService;
  private final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider;
  private final VerifiedBlockAttestationsSubscriptionProvider
      verifiedBlockAttestationsSubscriptionProvider;
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
  private final GossipedOperationConsumer<ValidateableAttestation> gossipedAttestationConsumer;
  private final GossipedOperationConsumer<AttesterSlashing> gossipedAttesterSlashingConsumer;
  private final GossipedOperationConsumer<ProposerSlashing> gossipedProposerSlashingConsumer;
  private final GossipedOperationConsumer<SignedVoluntaryExit> gossipedVoluntaryExitConsumer;

  public ActiveEth2Network(
      final DiscoveryNetwork<?> discoveryNetwork,
      final Eth2PeerManager peerManager,
      final EventBus eventBus,
      final RecentChainData recentChainData,
      final GossipEncoding gossipEncoding,
      final AttestationSubnetService attestationSubnetService,
      final GossipedOperationConsumer<ValidateableAttestation> gossipedAttestationConsumer,
      final GossipedOperationConsumer<AttesterSlashing> gossipedAttesterSlashingConsumer,
      final GossipedOperationConsumer<ProposerSlashing> gossipedProposerSlashingConsumer,
      final GossipedOperationConsumer<SignedVoluntaryExit> gossipedVoluntaryExitConsumer,
      final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider,
      final VerifiedBlockAttestationsSubscriptionProvider
          verifiedBlockAttestationsSubscriptionProvider) {
    super(discoveryNetwork);
    this.discoveryNetwork = discoveryNetwork;
    this.peerManager = peerManager;
    this.eventBus = eventBus;
    this.recentChainData = recentChainData;
    this.gossipEncoding = gossipEncoding;
    this.attestationSubnetService = attestationSubnetService;
    this.gossipedAttestationConsumer = gossipedAttestationConsumer;
    this.gossipedAttesterSlashingConsumer = gossipedAttesterSlashingConsumer;
    this.gossipedProposerSlashingConsumer = gossipedProposerSlashingConsumer;
    this.gossipedVoluntaryExitConsumer = gossipedVoluntaryExitConsumer;
    this.processedAttestationSubscriptionProvider = processedAttestationSubscriptionProvider;
    this.verifiedBlockAttestationsSubscriptionProvider =
        verifiedBlockAttestationsSubscriptionProvider;
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
    BlockValidator blockValidator = new BlockValidator(recentChainData, new StateTransition());
    AttestationValidator attestationValidator = new AttestationValidator(recentChainData);
    SignedAggregateAndProofValidator aggregateValidator =
        new SignedAggregateAndProofValidator(attestationValidator, recentChainData);
    final ForkInfo forkInfo = recentChainData.getHeadForkInfo().orElseThrow();
    VoluntaryExitValidator exitValidator =
        new VoluntaryExitValidator(
            recentChainData,
            new VoluntaryExitStateTransitionValidator(),
            new VoluntaryExitSignatureVerifier());

    ProposerSlashingValidator proposerSlashingValidator =
        new ProposerSlashingValidator(
            recentChainData,
            new ProposerSlashingStateTransitionValidator(),
            new ProposerSlashingSignatureVerifier());

    AttesterSlashingValidator attesterSlashingValidator =
        new AttesterSlashingValidator(
            recentChainData, new AttesterSlashingStateTransitionValidator());

    AttestationSubnetSubscriptions attestationSubnetSubscriptions =
        new AttestationSubnetSubscriptions(
            discoveryNetwork,
            gossipEncoding,
            attestationValidator,
            recentChainData,
            gossipedAttestationConsumer);

    blockGossipManager =
        new BlockGossipManager(
            discoveryNetwork, gossipEncoding, forkInfo, blockValidator, eventBus);

    attestationGossipManager =
        new AttestationGossipManager(gossipEncoding, attestationSubnetSubscriptions);

    aggregateGossipManager =
        new AggregateGossipManager(
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            aggregateValidator,
            gossipedAttestationConsumer);

    voluntaryExitGossipManager =
        new VoluntaryExitGossipManager(
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            exitValidator,
            gossipedVoluntaryExitConsumer);

    proposerSlashingGossipManager =
        new ProposerSlashingGossipManager(
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            proposerSlashingValidator,
            gossipedProposerSlashingConsumer);

    attesterSlashingGossipManager =
        new AttesterSlashingGossipManager(
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            attesterSlashingValidator,
            gossipedAttesterSlashingConsumer);

    discoveryNetworkAttestationSubnetsSubscription =
        attestationSubnetService.subscribeToUpdates(
            discoveryNetwork::setLongTermAttestationSubnetSubscriptions);

    pendingSubnetSubscriptions.forEach(this::subscribeToAttestationSubnetId);
    pendingSubnetSubscriptions.clear();

    processedAttestationSubscriptionProvider.subscribe(attestationGossipManager::onNewAttestation);
    processedAttestationSubscriptionProvider.subscribe(aggregateGossipManager::onNewAggregate);

    verifiedBlockAttestationsSubscriptionProvider.subscribe(
        (attestations) ->
            attestations.forEach(
                attestation ->
                    aggregateValidator.addSeenAggregate(
                        ValidateableAttestation.fromAttestation(attestation))));
  }

  @Override
  public synchronized void stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return;
    }
    blockGossipManager.shutdown();
    attestationGossipManager.shutdown();
    aggregateGossipManager.shutdown();
    voluntaryExitGossipManager.shutdown();
    proposerSlashingGossipManager.shutdown();
    attesterSlashingGossipManager.shutdown();
    attestationSubnetService.unsubscribe(discoveryNetworkAttestationSubnetsSubscription);
    super.stop();
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
    // TODO - look into keep separate collections for pending peers / validated peers so
    // we don't have to iterate over the peer list to get this count.
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
