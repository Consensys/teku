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

package tech.pegasys.teku.networking.p2p.discovery;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5Service;
import tech.pegasys.teku.networking.p2p.discovery.noop.NoOpDiscoveryService;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.storage.store.KeyValueStore;

/**
 * CAUTION: this API is unstable and primarily intended for debugging and testing purposes this API
 * might be changed in any version in backward incompatible way
 */
public class DiscoveryNetworkBuilder {

  public static DiscoveryNetworkBuilder create() {
    return new DiscoveryNetworkBuilder();
  }

  protected MetricsSystem metricsSystem;
  protected AsyncRunner asyncRunner;
  protected KeyValueStore<String, Bytes> kvStore;
  protected P2PNetwork<?> p2pNetwork;
  protected PeerSelectionStrategy peerSelectionStrategy;
  protected DiscoveryConfig discoveryConfig;
  protected NetworkConfig p2pConfig;
  protected Spec spec;
  protected SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier;

  protected DiscoveryService discoveryService;
  protected ConnectionManager connectionManager;

  protected DiscoveryNetworkBuilder() {}

  protected void initMissingDefaults() {
    if (discoveryService == null) {
      discoveryService = createDiscoveryService();
    }

    if (connectionManager == null) {
      connectionManager = createConnectionManager();
    }
  }

  public DiscoveryNetwork<?> build() {
    initMissingDefaults();
    return new DiscoveryNetwork<>(
        p2pNetwork, discoveryService, connectionManager, spec, currentSchemaDefinitionsSupplier);
  }

  protected ConnectionManager createConnectionManager() {
    checkNotNull(metricsSystem);
    checkNotNull(discoveryService);
    checkNotNull(asyncRunner);
    checkNotNull(p2pNetwork);
    checkNotNull(peerSelectionStrategy);
    checkNotNull(discoveryConfig);

    return new ConnectionManager(
        metricsSystem,
        discoveryService,
        asyncRunner,
        p2pNetwork,
        peerSelectionStrategy,
        discoveryConfig.getStaticPeers().stream()
            .map(p2pNetwork::createPeerAddress)
            .collect(toList()));
  }

  protected DiscoveryService createDiscoveryService() {
    final DiscoveryService discoveryService;
    if (discoveryConfig.isDiscoveryEnabled()) {
      checkNotNull(metricsSystem);
      checkNotNull(asyncRunner);
      checkNotNull(discoveryConfig);
      checkNotNull(p2pConfig);
      checkNotNull(kvStore);
      checkNotNull(p2pNetwork);
      checkNotNull(currentSchemaDefinitionsSupplier);

      discoveryService =
          new DiscV5Service(
              metricsSystem,
              asyncRunner,
              discoveryConfig,
              p2pConfig,
              kvStore,
              p2pNetwork.getPrivateKey(),
              currentSchemaDefinitionsSupplier,
              DiscV5Service.createDefaultDiscoverySystemBuilder(),
              DiscV5Service.DEFAULT_NODE_RECORD_CONVERTER);
    } else {
      discoveryService = new NoOpDiscoveryService();
    }
    return discoveryService;
  }

  public DiscoveryNetworkBuilder metricsSystem(MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public DiscoveryNetworkBuilder asyncRunner(AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
    return this;
  }

  public DiscoveryNetworkBuilder kvStore(KeyValueStore<String, Bytes> kvStore) {
    this.kvStore = kvStore;
    return this;
  }

  public DiscoveryNetworkBuilder p2pNetwork(P2PNetwork<?> p2pNetwork) {
    this.p2pNetwork = p2pNetwork;
    return this;
  }

  public DiscoveryNetworkBuilder peerSelectionStrategy(
      PeerSelectionStrategy peerSelectionStrategy) {
    this.peerSelectionStrategy = peerSelectionStrategy;
    return this;
  }

  public DiscoveryNetworkBuilder discoveryConfig(DiscoveryConfig discoveryConfig) {
    this.discoveryConfig = discoveryConfig;
    return this;
  }

  public DiscoveryNetworkBuilder p2pConfig(NetworkConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
    return this;
  }

  public DiscoveryNetworkBuilder spec(Spec spec) {
    this.spec = spec;
    return this;
  }

  public DiscoveryNetworkBuilder currentSchemaDefinitionsSupplier(
      SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier) {
    this.currentSchemaDefinitionsSupplier = currentSchemaDefinitionsSupplier;
    return this;
  }

  public DiscoveryNetworkBuilder discoveryService(DiscoveryService discoveryService) {
    this.discoveryService = discoveryService;
    return this;
  }

  public DiscoveryNetworkBuilder connectionManager(ConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
    return this;
  }
}
