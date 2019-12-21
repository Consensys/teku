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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import tech.pegasys.artemis.networking.eth2.discovery.Eth2DiscoveryManager;
import tech.pegasys.artemis.networking.eth2.discovery.Eth2DiscoveryManagerBuilder;
import tech.pegasys.artemis.networking.eth2.gossip.AggregateGossipManager;
import tech.pegasys.artemis.networking.eth2.gossip.AttestationGossipManager;
import tech.pegasys.artemis.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.artemis.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class Eth2Network extends DelegatingP2PNetwork implements P2PNetwork {
  private final P2PNetwork network;
  private final Eth2PeerManager peerManager;
  private final EventBus eventBus;
  private final ChainStorageClient chainStorageClient;
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

  private BlockGossipManager blockGossipManager;
  private AttestationGossipManager attestationGossipManager;
  private AggregateGossipManager aggregateGossipManager;

  private final NetworkConfig discoveryNetworkConfig;
  private Eth2DiscoveryManager eth2DiscoveryManager;

  public Eth2Network(
      final P2PNetwork network,
      final Eth2PeerManager peerManager,
      final EventBus eventBus,
      final ChainStorageClient chainStorageClient,
      final NetworkConfig discoveryNetworkConfig) {
    super(network);
    this.network = network;
    this.peerManager = peerManager;
    this.eventBus = eventBus;
    this.chainStorageClient = chainStorageClient;
    this.discoveryNetworkConfig = discoveryNetworkConfig;
  }

  @Override
  public CompletableFuture<?> start() {
    return super.start().thenAccept(r -> startup());
  }

  private void startup() {
    state.set(State.RUNNING);
    blockGossipManager = new BlockGossipManager(network, eventBus, chainStorageClient);
    attestationGossipManager = new AttestationGossipManager(network, eventBus, chainStorageClient);
    aggregateGossipManager = new AggregateGossipManager(network, eventBus, chainStorageClient);

    Eth2DiscoveryManagerBuilder discoveryManagerBuilder = new Eth2DiscoveryManagerBuilder();
    eth2DiscoveryManager =
        discoveryManagerBuilder
            .eventBus(Optional.of(eventBus))
            .network(Optional.of(network))
            .networkInterface(discoveryNetworkConfig.getNetworkInterface())
            .port(discoveryNetworkConfig.getListenPort())
            .privateKey(discoveryNetworkConfig.getPrivateKey())
            .peers(discoveryNetworkConfig.getPeers())
            .build();

    eth2DiscoveryManager.start();
  }

  @Override
  public void stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return;
    }
    blockGossipManager.shutdown();
    attestationGossipManager.shutdown();
    aggregateGossipManager.shutdown();
    eth2DiscoveryManager.stop();
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
  public long getPeerCount() {
    // TODO - look into keep separate collections for pending peers / validated peers so
    // we don't have to iterate over the peer list to get this count.
    return streamPeers().count();
  }
}
