/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.p2p.discovery.discv5;

import static java.util.Collections.emptyList;

import com.google.common.base.Preconditions;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.AddressAccessPolicy;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.NewAddressHandler;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.io.IPVersionResolver;
import tech.pegasys.teku.infrastructure.io.IPVersionResolver.IPVersion;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.libp2p.MultiaddrUtil;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.storage.store.KeyValueStore;

public class DiscV5Service extends Service implements DiscoveryService {
  private static final Logger LOG = LogManager.getLogger();
  private static final String SEQ_NO_STORE_KEY = "local-enr-seqno";
  private static final Duration BOOTNODE_REFRESH_DELAY = Duration.ofMinutes(2);
  public static final NodeRecordConverter DEFAULT_NODE_RECORD_CONVERTER = new NodeRecordConverter();

  public static DiscoverySystemBuilder createDefaultDiscoverySystemBuilder() {
    return new DiscoverySystemBuilder();
  }

  private final AsyncRunner asyncRunner;
  private final SecretKey localNodePrivateKey;
  private final SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier;
  private final NodeRecordConverter nodeRecordConverter;

  private final DiscoverySystem discoverySystem;
  private final KeyValueStore<String, Bytes> kvStore;
  private final boolean supportsIpv6;
  private final List<NodeRecord> bootnodes;
  private volatile Cancellable bootnodeRefreshTask;

  public DiscV5Service(
      final MetricsSystem metricsSystem,
      final AsyncRunner asyncRunner,
      final DiscoveryConfig discoConfig,
      final NetworkConfig p2pConfig,
      final KeyValueStore<String, Bytes> kvStore,
      final Bytes privateKey,
      final SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier,
      final DiscoverySystemBuilder discoverySystemBuilder,
      final NodeRecordConverter nodeRecordConverter) {
    this.asyncRunner = asyncRunner;
    this.localNodePrivateKey = SecretKeyParser.fromLibP2pPrivKey(privateKey);
    this.currentSchemaDefinitionsSupplier = currentSchemaDefinitionsSupplier;
    this.nodeRecordConverter = nodeRecordConverter;
    final List<String> networkInterfaces = p2pConfig.getNetworkInterfaces();
    Preconditions.checkState(
        networkInterfaces.size() == 1 || networkInterfaces.size() == 2,
        "The configured network interfaces must be either 1 or 2");
    if (networkInterfaces.size() == 1) {
      final String listenAddress = networkInterfaces.get(0);
      discoverySystemBuilder.listen(listenAddress, discoConfig.getListenUdpPort());
      this.supportsIpv6 = IPVersionResolver.resolve(listenAddress) == IPVersion.IP_V6;
    } else {
      // IPv4 and IPv6 (dual-stack)
      final InetSocketAddress[] listenAddresses =
          networkInterfaces.stream()
              .map(
                  networkInterface -> {
                    final int listenUdpPort =
                        switch (IPVersionResolver.resolve(networkInterface)) {
                          case IP_V4 -> discoConfig.getListenUdpPort();
                          case IP_V6 -> discoConfig.getListenUpdPortIpv6();
                        };
                    return new InetSocketAddress(networkInterface, listenUdpPort);
                  })
              .toArray(InetSocketAddress[]::new);
      discoverySystemBuilder.listen(listenAddresses);
      this.supportsIpv6 = true;
    }
    final UInt64 seqNo =
        kvStore.get(SEQ_NO_STORE_KEY).map(UInt64::fromBytes).orElse(UInt64.ZERO).add(1);
    final NewAddressHandler maybeUpdateNodeRecordHandler = maybeUpdateNodeRecord(p2pConfig);
    this.bootnodes =
        discoConfig.getBootnodes().stream().map(NodeRecordFactory.DEFAULT::fromEnr).toList();
    final NodeRecordBuilder nodeRecordBuilder =
        new NodeRecordBuilder().secretKey(localNodePrivateKey).seq(seqNo);
    if (p2pConfig.hasUserExplicitlySetAdvertisedIps()) {
      final List<String> advertisedIps = p2pConfig.getAdvertisedIps();
      Preconditions.checkState(
          advertisedIps.size() == 1 || advertisedIps.size() == 2,
          "The configured advertised IPs must be either 1 or 2");
      if (advertisedIps.size() == 1) {
        nodeRecordBuilder.address(
            advertisedIps.get(0),
            discoConfig.getAdvertisedUdpPort(),
            p2pConfig.getAdvertisedPort());
      } else {
        // IPv4 and IPv6 (dual-stack)
        advertisedIps.forEach(
            advertisedIp -> {
              final IPVersion ipVersion = IPVersionResolver.resolve(advertisedIp);
              final int advertisedUdpPort =
                  switch (ipVersion) {
                    case IP_V4 -> discoConfig.getAdvertisedUdpPort();
                    case IP_V6 -> discoConfig.getAdvertisedUdpPortIpv6();
                  };
              final int advertisedTcpPort =
                  switch (ipVersion) {
                    case IP_V4 -> p2pConfig.getAdvertisedPort();
                    case IP_V6 -> p2pConfig.getAdvertisedPortIpv6();
                  };
              nodeRecordBuilder.address(advertisedIp, advertisedUdpPort, advertisedTcpPort);
            });
      }
    }
    final NodeRecord localNodeRecord = nodeRecordBuilder.build();
    this.discoverySystem =
        discoverySystemBuilder
            .secretKey(localNodePrivateKey)
            .bootnodes(bootnodes)
            .localNodeRecord(localNodeRecord)
            .newAddressHandler(maybeUpdateNodeRecordHandler)
            .localNodeRecordListener(this::localNodeRecordUpdated)
            .addressAccessPolicy(
                discoConfig.areSiteLocalAddressesEnabled()
                    ? AddressAccessPolicy.ALLOW_ALL
                    : address -> !address.getAddress().isSiteLocalAddress())
            .build();
    this.kvStore = kvStore;
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.DISCOVERY,
        "live_nodes_current",
        "Current number of live nodes tracked by the discovery system",
        () -> discoverySystem.getBucketStats().getTotalLiveNodeCount());
  }

  private NewAddressHandler maybeUpdateNodeRecord(final NetworkConfig p2pConfig) {
    if (p2pConfig.hasUserExplicitlySetAdvertisedIps()) {
      return (oldRecord, newAddress) -> Optional.of(oldRecord);
    } else {
      return (oldRecord, newAddress) -> {
        final int newTcpPort;
        if (p2pConfig.getNetworkInterfaces().size() == 1) {
          newTcpPort = p2pConfig.getAdvertisedPort();
        } else {
          // IPv4 and IPv6 (dual-stack)
          newTcpPort =
              switch (IPVersionResolver.resolve(newAddress)) {
                case IP_V4 -> p2pConfig.getAdvertisedPort();
                case IP_V6 -> p2pConfig.getAdvertisedPortIpv6();
              };
        }
        return Optional.of(
            oldRecord.withNewAddress(newAddress, Optional.of(newTcpPort), localNodePrivateKey));
      };
    }
  }

  private void localNodeRecordUpdated(final NodeRecord oldRecord, final NodeRecord newRecord) {
    kvStore.put(SEQ_NO_STORE_KEY, newRecord.getSeq().toBytes());
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.of(discoverySystem.start())
        .thenRun(
            () ->
                this.bootnodeRefreshTask =
                    asyncRunner.runWithFixedDelay(
                        this::pingBootnodes,
                        BOOTNODE_REFRESH_DELAY,
                        error -> LOG.error("Failed to contact discovery bootnodes", error)));
  }

  private void pingBootnodes() {
    bootnodes.forEach(
        bootnode ->
            SafeFuture.of(discoverySystem.ping(bootnode))
                .finish(error -> LOG.debug("Bootnode {} is unresponsive", bootnode)));
  }

  @Override
  protected SafeFuture<?> doStop() {
    final Cancellable refreshTask = this.bootnodeRefreshTask;
    this.bootnodeRefreshTask = null;
    if (refreshTask != null) {
      refreshTask.cancel();
    }
    discoverySystem.stop();
    return SafeFuture.completedFuture(null);
  }

  @Override
  public Stream<DiscoveryPeer> streamKnownPeers() {
    final SchemaDefinitions schemaDefinitions =
        currentSchemaDefinitionsSupplier.getSchemaDefinitions();
    return activeNodes()
        .flatMap(
            node ->
                nodeRecordConverter
                    .convertToDiscoveryPeer(node, supportsIpv6, schemaDefinitions)
                    .stream());
  }

  @Override
  public SafeFuture<Collection<DiscoveryPeer>> searchForPeers() {
    return SafeFuture.of(discoverySystem.searchForNewPeers())
        // Current version of discovery doesn't return the found peers but next version will
        .<Collection<NodeRecord>>thenApply(__ -> emptyList())
        .thenApply(this::convertToDiscoveryPeers);
  }

  private List<DiscoveryPeer> convertToDiscoveryPeers(final Collection<NodeRecord> foundNodes) {
    LOG.debug("Found {} nodes prior to filtering", foundNodes.size());
    final SchemaDefinitions schemaDefinitions =
        currentSchemaDefinitionsSupplier.getSchemaDefinitions();
    return foundNodes.stream()
        .flatMap(
            nodeRecord ->
                nodeRecordConverter
                    .convertToDiscoveryPeer(nodeRecord, supportsIpv6, schemaDefinitions)
                    .stream())
        .toList();
  }

  @Override
  public Optional<String> getEnr() {
    return Optional.of(discoverySystem.getLocalNodeRecord().asEnr());
  }

  @Override
  public Optional<Bytes> getNodeId() {
    return Optional.of(discoverySystem.getLocalNodeRecord().getNodeId());
  }

  @Override
  public Optional<List<String>> getDiscoveryAddresses() {
    final NodeRecord nodeRecord = discoverySystem.getLocalNodeRecord();
    final List<InetSocketAddress> updAddresses = new ArrayList<>();
    nodeRecord.getUdpAddress().ifPresent(updAddresses::add);
    nodeRecord.getUdp6Address().ifPresent(updAddresses::add);
    if (updAddresses.isEmpty()) {
      return Optional.empty();
    }
    final List<String> discoveryAddresses =
        updAddresses.stream()
            .map(
                updAddress -> {
                  final DiscoveryPeer discoveryPeer =
                      new DiscoveryPeer(
                          (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1),
                          nodeRecord.getNodeId(),
                          updAddress,
                          Optional.empty(),
                          currentSchemaDefinitionsSupplier.getAttnetsENRFieldSchema().getDefault(),
                          currentSchemaDefinitionsSupplier.getSyncnetsENRFieldSchema().getDefault(),
                          Optional.empty());
                  return MultiaddrUtil.fromDiscoveryPeerAsUdp(discoveryPeer).toString();
                })
            .toList();
    return Optional.of(discoveryAddresses);
  }

  @Override
  public void updateCustomENRField(final String fieldName, final Bytes value) {
    discoverySystem.updateCustomFieldValue(fieldName, value);
  }

  @Override
  public Optional<String> lookupEnr(final UInt256 nodeId) {
    final Optional<NodeRecord> maybeNodeRecord = discoverySystem.lookupNode(nodeId.toBytes());
    return maybeNodeRecord.map(NodeRecord::asEnr);
  }

  private Stream<NodeRecord> activeNodes() {
    return discoverySystem.streamLiveNodes();
  }
}
