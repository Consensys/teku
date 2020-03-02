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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.artemis.networking.p2p.DiscoveryNetwork;
import tech.pegasys.artemis.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.PeerHandler;
import tech.pegasys.artemis.networking.p2p.rpc.RpcMethod;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;

public class Eth2NetworkBuilder {
  protected NetworkConfig config;
  protected EventBus eventBus;
  protected ChainStorageClient chainStorageClient;
  protected MetricsSystem metricsSystem;
  protected List<RpcMethod> rpcMethods = new ArrayList<>();
  protected List<PeerHandler> peerHandlers = new ArrayList<>();

  private Eth2NetworkBuilder() {}

  public static Eth2NetworkBuilder create() {
    return new Eth2NetworkBuilder();
  }

  public P2PNetwork<Eth2Peer> build() {
    validate();

    // Setup eth2 handlers
    final HistoricalChainData historicalChainData = new HistoricalChainData(eventBus);
    final Eth2PeerManager eth2PeerManager =
        Eth2PeerManager.create(chainStorageClient, historicalChainData, metricsSystem);
    final Collection<RpcMethod> eth2RpcMethods = eth2PeerManager.getBeaconChainMethods().all();
    rpcMethods.addAll(eth2RpcMethods);
    peerHandlers.add(eth2PeerManager);

    // Build core network and inject eth2 handlers
    final P2PNetwork<?> network = buildNetwork();

    return DiscoveryNetwork.create(
        new Eth2Network(network, eth2PeerManager, eventBus, chainStorageClient), config);
  }

  protected P2PNetwork<?> buildNetwork() {
    return new LibP2PNetwork(config, metricsSystem, rpcMethods, peerHandlers);
  }

  private void validate() {
    assertNotNull("config", config);
    assertNotNull("eventBus", eventBus);
    assertNotNull("metricsSystem", metricsSystem);
    assertNotNull("chainStorageClient", chainStorageClient);
  }

  private void assertNotNull(String fieldName, Object fieldValue) {
    checkState(fieldValue != null, "Field " + fieldName + " must be set.");
  }

  public Eth2NetworkBuilder config(final NetworkConfig config) {
    checkNotNull(config);
    this.config = config;
    return this;
  }

  public Eth2NetworkBuilder eventBus(final EventBus eventBus) {
    checkNotNull(eventBus);
    this.eventBus = eventBus;
    return this;
  }

  public Eth2NetworkBuilder chainStorageClient(final ChainStorageClient chainStorageClient) {
    checkNotNull(chainStorageClient);
    this.chainStorageClient = chainStorageClient;
    return this;
  }

  public Eth2NetworkBuilder metricsSystem(final MetricsSystem metricsSystem) {
    checkNotNull(metricsSystem);
    this.metricsSystem = metricsSystem;
    return this;
  }

  public Eth2NetworkBuilder rpcMethod(final RpcMethod rpcMethod) {
    checkNotNull(rpcMethod);
    rpcMethods.add(rpcMethod);
    return this;
  }

  public Eth2NetworkBuilder peerHandler(final PeerHandler peerHandler) {
    checkNotNull(peerHandler);
    peerHandlers.add(peerHandler);
    return this;
  }
}
