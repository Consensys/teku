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
import tech.pegasys.artemis.networking.eth2.gossip.AggregateGossipManager;
import tech.pegasys.artemis.networking.eth2.gossip.AttestationGossipManager;
import tech.pegasys.artemis.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.async.SafeFuture;

public class Eth2Network extends DelegatingP2PNetwork<Eth2Peer> implements P2PNetwork<Eth2Peer> {
  private final P2PNetwork<?> network;
  private final Eth2PeerManager peerManager;
  private final EventBus eventBus;
  private final ChainStorageClient chainStorageClient;
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

  private BlockGossipManager blockGossipManager;
  private AttestationGossipManager attestationGossipManager;
  private AggregateGossipManager aggregateGossipManager;

  public Eth2Network(
      final P2PNetwork<?> network,
      final Eth2PeerManager peerManager,
      final EventBus eventBus,
      final ChainStorageClient chainStorageClient) {
    super(network);
    this.network = network;
    this.peerManager = peerManager;
    this.eventBus = eventBus;
    this.chainStorageClient = chainStorageClient;
  }

  @Override
  public SafeFuture<?> start() {
    return super.start().thenAccept(r -> startup());
  }

  private void startup() {
    state.set(State.RUNNING);
    blockGossipManager = new BlockGossipManager(network, eventBus, chainStorageClient);
    attestationGossipManager = new AttestationGossipManager(network, eventBus, chainStorageClient);
    aggregateGossipManager = new AggregateGossipManager(network, eventBus, chainStorageClient);
  }

  @Override
  public void stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return;
    }
    blockGossipManager.shutdown();
    attestationGossipManager.shutdown();
    aggregateGossipManager.shutdown();
    super.stop();
  }

  @Override
  public NetworkConfig getConfig() {
    return network.getConfig();
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
}
