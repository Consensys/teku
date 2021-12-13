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
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
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
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;
import tech.pegasys.teku.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
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
    queueGossipStart();
  }

  private void queueGossipStart() {
    LOG.debug("Check if gossip should be started");
    final UInt64 slotsBehind = recentChainData.getChainHeadSlotsBehind().orElseThrow();
    if (slotsBehind.isLessThanOrEqualTo(500)) {
      // Start gossip if we're "close enough" to the chain head
      // Note: we don't want to be too strict here, otherwise we could end up with our sync logic
      // inactive because our chain is almost caught up to the chainhead, but gossip inactive so
      // that our node slowly falls behind because no gossip is propagating.  However, if we're too
      // aggressive, our node could be down-scored for subscribing to topics that it can't yet
      // validate or propagate.
      startGossip();
    } else {
      // Schedule a future check
      asyncRunner.runAfterDelay(this::queueGossipStart, Duration.ofSeconds(10)).reportExceptions();
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
    processedAttestationSubscriptionProvider.subscribe(gossipForkManager::publishAttestation);
    eventChannels.subscribe(BlockGossipChannel.class, gossipForkManager::publishBlock);

    setTopicScoringParams();
  }

  private void setTopicScoringParams() {
    final GossipTopicsScoringConfig topicConfig =
        gossipConfigurator.configureAllTopics(getEth2Context());
    discoveryNetwork.updateGossipTopicScoring(topicConfig);

    gossipUpdateTask =
        asyncRunner.runWithFixedDelay(
            this::updateDynamicTopicScoring,
            Duration.ofMinutes(1),
            (err) ->
                LOG.error(
                    "Encountered error while attempting to updating gossip topic scoring", err));
  }

  private void updateDynamicTopicScoring() {
    LOG.trace("Update dynamic topic scoring");
    final GossipTopicsScoringConfig topicConfig =
        gossipConfigurator.configureDynamicTopics(getEth2Context());
    discoveryNetwork.updateGossipTopicScoring(topicConfig);
  }

  private Eth2Context getEth2Context() {
    final StateAndBlockSummary chainHead = recentChainData.getChainHead().orElseThrow();
    final Bytes4 forkDigest = chainHead.getState().getForkInfo().getForkDigest(spec);
    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);

    final UInt64 activeValidatorsEpoch =
        spec.getMaxLookaheadEpoch(chainHead.getState()).min(currentEpoch);
    final int activeValidators =
        spec.countActiveValidators(chainHead.getState(), activeValidatorsEpoch);

    return Eth2Context.builder()
        .currentSlot(currentSlot)
        .activeValidatorCount(activeValidators)
        .forkDigest(forkDigest)
        .gossipEncoding(gossipEncoding)
        .build();
  }

  @Override
  public synchronized SafeFuture<?> stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return SafeFuture.COMPLETE;
    }

    if (gossipStarted.get()) {
      gossipUpdateTask.cancel();
      gossipForkManager.stopGossip();
      attestationSubnetService.unsubscribe(discoveryNetworkAttestationSubnetsSubscription);
      syncCommitteeSubnetService.unsubscribe(discoveryNetworkSyncCommitteeSubnetsSubscription);
    }

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
