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

package tech.pegasys.artemis.networking.eth2;

import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.networking.eth2.gossip.AggregateGossipManager;
import tech.pegasys.artemis.networking.eth2.gossip.AttestationGossipManager;
import tech.pegasys.artemis.networking.eth2.gossip.AttestationSubnetSubscriptions;
import tech.pegasys.artemis.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.BlockValidator;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.p2p.DiscoveryNetwork;
import tech.pegasys.artemis.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.async.SafeFuture;

public class ActiveEth2Network extends DelegatingP2PNetwork<Eth2Peer> implements Eth2Network {
  private static final Logger LOG = LogManager.getLogger();

  private final DiscoveryNetwork<?> discoveryNetwork;
  private final Eth2PeerManager peerManager;
  private final EventBus eventBus;
  private final RecentChainData recentChainData;
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

  private volatile BlockGossipManager blockGossipManager;
  private volatile AttestationGossipManager attestationGossipManager;
  private volatile AggregateGossipManager aggregateGossipManager;

  public ActiveEth2Network(
      final DiscoveryNetwork<?> discoveryNetwork,
      final Eth2PeerManager peerManager,
      final EventBus eventBus,
      final RecentChainData recentChainData) {
    super(discoveryNetwork);
    this.discoveryNetwork = discoveryNetwork;
    this.peerManager = peerManager;
    this.eventBus = eventBus;
    this.recentChainData = recentChainData;
  }

  @Override
  public SafeFuture<?> start() {
    return super.start().thenAccept(r -> startup());
  }

  private void startup() {
    state.set(State.RUNNING);
    recentChainData.subscribeBestBlockInitialized(this::initGossipManagers);
  }

  public void initGossipManagers() {
    BlockValidator blockValidator = new BlockValidator(recentChainData, new StateTransition());
    AttestationSubnetSubscriptions attestationSubnetSubscriptions =
        new AttestationSubnetSubscriptions(discoveryNetwork, recentChainData, eventBus);
    blockGossipManager =
        new BlockGossipManager(discoveryNetwork, eventBus, blockValidator, recentChainData);
    attestationGossipManager =
        new AttestationGossipManager(eventBus, attestationSubnetSubscriptions);
    aggregateGossipManager =
        new AggregateGossipManager(discoveryNetwork, eventBus, recentChainData);
  }

  @Override
  public void stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return;
    }
    if (blockGossipManager != null) {
      blockGossipManager.shutdown();
    }
    if (attestationGossipManager != null) {
      attestationGossipManager.shutdown();
    }
    if (aggregateGossipManager != null) {
      aggregateGossipManager.shutdown();
    }
    super.stop();
  }

  @Override
  public NetworkConfig getConfig() {
    return discoveryNetwork.getConfig();
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
  public void subscribeToAttestationCommitteeTopic(final int committeeIndex) {
    if (aggregateGossipManager == null) {
      LOG.warn(
          "Attestation committee can not be subscribed due to gossip manager not being initialized");
      return;
    }
    attestationGossipManager.subscribeToCommitteeTopic(committeeIndex);
  }

  @Override
  public void unsubscribeFromAttestationCommitteeTopic(final int committeeIndex) {
    if (aggregateGossipManager == null) {
      LOG.warn(
          "Attestation committee can not be unsubscribed due to gossip manager not being initialized");
      return;
    }
    attestationGossipManager.unsubscribeFromCommitteeTopic(committeeIndex);
  }

  @Override
  public void setLongTermAttestationSubnetSubscriptions(final Iterable<Integer> subnetIndices) {
    discoveryNetwork.setLongTermAttestationSubnetSubscriptions(subnetIndices);
  }
}
