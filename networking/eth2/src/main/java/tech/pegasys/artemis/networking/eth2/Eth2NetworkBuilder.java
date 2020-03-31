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
import tech.pegasys.artemis.networking.p2p.connection.ReputationManager;
import tech.pegasys.artemis.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.PeerHandler;
import tech.pegasys.artemis.networking.p2p.rpc.RpcMethod;
import tech.pegasys.artemis.storage.clientside.RecentChainData;
import tech.pegasys.artemis.storage.api.StorageQueryChannel;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.TimeProvider;

public class Eth2NetworkBuilder {

  private NetworkConfig config;
  private EventBus eventBus;
  private RecentChainData recentChainData;
  private StorageQueryChannel historicalChainData;
  private MetricsSystem metricsSystem;
  private List<RpcMethod> rpcMethods = new ArrayList<>();
  private List<PeerHandler> peerHandlers = new ArrayList<>();
  private TimeProvider timeProvider;

  private Eth2NetworkBuilder() {}

  public static Eth2NetworkBuilder create() {
    return new Eth2NetworkBuilder();
  }

  public P2PNetwork<Eth2Peer> build() {
    validate();

    // Setup eth2 handlers
    final Eth2PeerManager eth2PeerManager =
        Eth2PeerManager.create(recentChainData, historicalChainData, metricsSystem);
    final Collection<RpcMethod> eth2RpcMethods = eth2PeerManager.getBeaconChainMethods().all();
    rpcMethods.addAll(eth2RpcMethods);
    peerHandlers.add(eth2PeerManager);

    // Build core network and inject eth2 handlers
    final P2PNetwork<?> network = buildNetwork();

    return new Eth2Network(network, eth2PeerManager, eventBus, recentChainData);
  }

  protected P2PNetwork<?> buildNetwork() {
    final ReputationManager reputationManager =
        new ReputationManager(timeProvider, Constants.REPUTATION_MANAGER_CAPACITY);
    return DiscoveryNetwork.create(
        new LibP2PNetwork(config, reputationManager, metricsSystem, rpcMethods, peerHandlers),
        reputationManager,
        config);
  }

  private void validate() {
    assertNotNull("config", config);
    assertNotNull("eventBus", eventBus);
    assertNotNull("metricsSystem", metricsSystem);
    assertNotNull("chainStorageClient", recentChainData);
    assertNotNull("timeProvider", timeProvider);
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

  public Eth2NetworkBuilder historicalChainData(final StorageQueryChannel historicalChainData) {
    checkNotNull(historicalChainData);
    this.historicalChainData = historicalChainData;
    return this;
  }

  public Eth2NetworkBuilder recentChainData(final RecentChainData recentChainData) {
    checkNotNull(recentChainData);
    this.recentChainData = recentChainData;
    return this;
  }

  public Eth2NetworkBuilder metricsSystem(final MetricsSystem metricsSystem) {
    checkNotNull(metricsSystem);
    this.metricsSystem = metricsSystem;
    return this;
  }

  public Eth2NetworkBuilder timeProvider(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
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
