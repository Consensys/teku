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

package tech.pegasys.teku.networking.eth2;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.config.Eth2Context;
import tech.pegasys.teku.networking.eth2.gossip.config.GossipConfigurator;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkManager;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ActiveEth2P2PNetwork extends DelegatingP2PNetwork<Eth2Peer> implements Eth2P2PNetwork {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final DiscoveryNetwork<?> discoveryNetwork;
  private final Eth2PeerManager peerManager;
  private final EventChannels eventChannels;
  private final RecentChainData recentChainData;
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
  private final GossipEncoding gossipEncoding;
  private final GossipConfigurator gossipConfigurator;
  private final SubnetSubscriptionService attestationSubnetService;
  private final SubnetSubscriptionService syncCommitteeSubnetService;
  private final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider;
  private final AtomicBoolean gossipStarted = new AtomicBoolean(false);

  private final GossipForkManager gossipForkManager;

  private long discoveryNetworkAttestationSubnetsSubscription;
  private long discoveryNetworkSyncCommitteeSubnetsSubscription;

  private volatile Cancellable gossipUpdateTask;
  private ForkInfo currentForkInfo;

  public ActiveEth2P2PNetwork(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final DiscoveryNetwork<?> discoveryNetwork,
      final Eth2PeerManager peerManager,
      final GossipForkManager gossipForkManager,
      final EventChannels eventChannels,
      final RecentChainData recentChainData,
      final SubnetSubscriptionService attestationSubnetService,
      final SubnetSubscriptionService syncCommitteeSubnetService,
      final GossipEncoding gossipEncoding,
      final GossipConfigurator gossipConfigurator,
      final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider) {
    super(discoveryNetwork);
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.discoveryNetwork = discoveryNetwork;
    this.peerManager = peerManager;
    this.gossipForkManager = gossipForkManager;
    this.eventChannels = eventChannels;
    this.recentChainData = recentChainData;
    this.gossipEncoding = gossipEncoding;
    this.gossipConfigurator = gossipConfigurator;
    this.attestationSubnetService = attestationSubnetService;
    this.syncCommitteeSubnetService = syncCommitteeSubnetService;
    this.processedAttestationSubscriptionProvider = processedAttestationSubscriptionProvider;
  }

  @Override
  public SafeFuture<?> start() {
    if (recentChainData.isPreGenesis() || recentChainData.isPreForkChoice()) {
      throw new IllegalStateException(
          getClass().getSimpleName()
              + " should only be started after "
              + recentChainData.getClass().getSimpleName()
              + " is fully initialized.");
    }
    // Set the current fork info prior to discovery starting up.
    final ForkInfo currentForkInfo = recentChainData.getCurrentForkInfo().orElseThrow();
    updateForkInfo(currentForkInfo);
    return super.start().thenAccept(r -> startup());
  }

  private synchronized void startup() {
    state.set(State.RUNNING);
    processedAttestationSubscriptionProvider.subscribe(gossipForkManager::publishAttestation);
    eventChannels.subscribe(BlockGossipChannel.class, gossipForkManager::publishBlock);
    if (isCloseToInSync()) {
      startGossip();
    }
  }

  private synchronized void startGossip() {
    if (!gossipStarted.compareAndSet(false, true)) {
      return;
    }

    LOG.info("Starting eth2 gossip");

    discoveryNetworkAttestationSubnetsSubscription =
        attestationSubnetService.subscribeToUpdates(
            discoveryNetwork::setLongTermAttestationSubnetSubscriptions);
    discoveryNetworkSyncCommitteeSubnetsSubscription =
        syncCommitteeSubnetService.subscribeToUpdates(
            discoveryNetwork::setSyncCommitteeSubnetSubscriptions);

    gossipForkManager.configureGossipForEpoch(recentChainData.getCurrentEpoch().orElseThrow());

    setTopicScoringParams();
  }

  private synchronized void stopGossip() {
    if (gossipStarted.compareAndSet(true, false)) {
      LOG.warn("Stopping eth2 gossip while node is syncing");
      gossipUpdateTask.cancel();
      gossipForkManager.stopGossip();
      attestationSubnetService.unsubscribe(discoveryNetworkAttestationSubnetsSubscription);
      syncCommitteeSubnetService.unsubscribe(discoveryNetworkSyncCommitteeSubnetsSubscription);
    }
  }

  @Override
  public void onSyncStateChanged(final boolean isInSync, final boolean isOptimistic) {
    if (state.get() != State.RUNNING) {
      return;
    }
    if (isInSync || isCloseToInSync()) {
      startGossip();
    } else {
      stopGossip();
    }
    gossipForkManager.onOptimisticHeadChanged(isOptimistic);
  }

  private boolean isCloseToInSync() {
    return recentChainData
        .getChainHeadSlotsBehind()
        .orElse(UInt64.MAX_VALUE)
        .isLessThanOrEqualTo(500);
  }

  private void setTopicScoringParams() {
    gossipUpdateTask =
        asyncRunner.runWithFixedDelay(
            this::updateDynamicTopicScoring,
            Duration.ZERO,
            Duration.ofMinutes(1),
            (err) ->
                LOG.error(
                    "Encountered error while attempting to updating gossip topic scoring", err));
  }

  private SafeFuture<?> updateDynamicTopicScoring() {
    LOG.trace("Update dynamic topic scoring");
    return getEth2Context()
        .thenApply(gossipConfigurator::configureDynamicTopics)
        .thenAccept(discoveryNetwork::updateGossipTopicScoring);
  }

  private SafeFuture<Eth2Context> getEth2Context() {
    final ChainHead chainHead = recentChainData.getChainHead().orElseThrow();
    final Bytes4 forkDigest =
        recentChainData.getCurrentForkInfo().orElseThrow().getForkDigest(spec);
    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);

    return chainHead
        .getState()
        .thenApply(
            chainHeadState -> {
              final UInt64 activeValidatorsEpoch =
                  spec.getMaxLookaheadEpoch(chainHeadState).min(currentEpoch);
              final int activeValidators =
                  spec.countActiveValidators(chainHeadState, activeValidatorsEpoch);

              return Eth2Context.builder()
                  .currentSlot(currentSlot)
                  .activeValidatorCount(activeValidators)
                  .forkDigest(forkDigest)
                  .gossipEncoding(gossipEncoding)
                  .build();
            });
  }

  @Override
  public synchronized SafeFuture<?> stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return SafeFuture.COMPLETE;
    }

    stopGossip();

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
  public void onEpoch(final UInt64 epoch) {
    if (gossipStarted.get()) {
      gossipForkManager.configureGossipForEpoch(epoch);
    }

    recentChainData.getForkInfo(epoch).ifPresent(this::updateForkInfo);
  }

  @Override
  public synchronized void subscribeToAttestationSubnetId(final int subnetId) {
    gossipForkManager.subscribeToAttestationSubnetId(subnetId);
  }

  @Override
  public synchronized void unsubscribeFromAttestationSubnetId(final int subnetId) {
    gossipForkManager.unsubscribeFromAttestationSubnetId(subnetId);
  }

  @Override
  public void setLongTermAttestationSubnetSubscriptions(final Iterable<Integer> subnetIndices) {
    attestationSubnetService.setSubscriptions(subnetIndices);
  }

  @Override
  public void subscribeToSyncCommitteeSubnetId(final int subnetId) {
    gossipForkManager.subscribeToSyncCommitteeSubnetId(subnetId);
    syncCommitteeSubnetService.addSubscription(subnetId);
  }

  @Override
  public void unsubscribeFromSyncCommitteeSubnetId(final int subnetId) {
    gossipForkManager.unsubscribeFromSyncCommitteeSubnetId(subnetId);
    syncCommitteeSubnetService.removeSubscription(subnetId);
  }

  @Override
  public MetadataMessage getMetadata() {
    return peerManager.getMetadataMessage();
  }

  @Override
  public void publishSyncCommitteeMessage(final ValidateableSyncCommitteeMessage message) {
    gossipForkManager.publishSyncCommitteeMessage(message);
  }

  @Override
  public void publishSyncCommitteeContribution(
      final SignedContributionAndProof signedContributionAndProof) {
    gossipForkManager.publishSyncCommitteeContribution(signedContributionAndProof);
  }

  @Override
  public void publishProposerSlashing(final ProposerSlashing proposerSlashing) {
    gossipForkManager.publishProposerSlashing(proposerSlashing);
  }

  @Override
  public void publishAttesterSlashing(final AttesterSlashing attesterSlashing) {
    gossipForkManager.publishAttesterSlashing(attesterSlashing);
  }

  @Override
  public void publishVoluntaryExit(final SignedVoluntaryExit signedVoluntaryExit) {
    gossipForkManager.publishVoluntaryExit(signedVoluntaryExit);
  }

  @Override
  public void publishSignedBlsToExecutionChange(
      final SignedBlsToExecutionChange signedBlsToExecutionChange) {
    gossipForkManager.publishSignedBlsToExecutionChanges(signedBlsToExecutionChange);
  }

  @VisibleForTesting
  Eth2PeerManager getPeerManager() {
    return peerManager;
  }

  private synchronized void updateForkInfo(final ForkInfo forkInfo) {
    if (currentForkInfo != null
        && (currentForkInfo.equals(forkInfo) || forkInfo.isPriorTo(currentForkInfo))) {
      return;
    }

    currentForkInfo = forkInfo;
    final Optional<Fork> nextFork = recentChainData.getNextFork(forkInfo.getFork());
    discoveryNetwork.setForkInfo(forkInfo, nextFork);
  }

  @Override
  public Optional<DiscoveryNetwork<?>> getDiscoveryNetwork() {
    return Optional.of(discoveryNetwork);
  }
}
