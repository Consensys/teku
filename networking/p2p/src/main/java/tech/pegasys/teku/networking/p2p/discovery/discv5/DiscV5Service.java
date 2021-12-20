/*
 * Copyright 2020 ConsenSys AG.
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
import static java.util.stream.Collectors.toList;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
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
  public static NodeRecordConverter DEFAULT_NODE_RECORD_CONVERTER = new NodeRecordConverter();

  public static DiscoverySystemBuilder createDefaultDiscoverySystemBuilder() {
    return new DiscoverySystemBuilder();
  }

  private final AsyncRunner asyncRunner;
  private final Bytes localNodePrivateKey;
  private final SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier;
  private final NodeRecordConverter nodeRecordConverter;

  private final DiscoverySystem discoverySystem;
  private final KeyValueStore<String, Bytes> kvStore;
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
    this.localNodePrivateKey = privateKey;
    this.currentSchemaDefinitionsSupplier = currentSchemaDefinitionsSupplier;
    this.nodeRecordConverter = nodeRecordConverter;
    final String listenAddress = p2pConfig.getNetworkInterface();
    final int listenUdpPort = discoConfig.getListenUdpPort();
    final String advertisedAddress = p2pConfig.getAdvertisedIp();
    final int advertisedTcpPort = p2pConfig.getAdvertisedPort();
    final int advertisedUdpPort = discoConfig.getAdvertisedUdpPort();
    final UInt64 seqNo =
        kvStore.get(SEQ_NO_STORE_KEY).map(UInt64::fromBytes).orElse(UInt64.ZERO).add(1);
    final NewAddressHandler maybeUpdateNodeRecordHandler =
        maybeUpdateNodeRecord(p2pConfig.hasUserExplicitlySetAdvertisedIp(), advertisedTcpPort);
    this.bootnodes =
        discoConfig.getBootnodes().stream()
            .map(NodeRecordFactory.DEFAULT::fromEnr)
            .collect(toList());
    final NodeRecordBuilder nodeRecordBuilder =
        new NodeRecordBuilder().privateKey(privateKey).seq(seqNo);
    if (p2pConfig.hasUserExplicitlySetAdvertisedIp()) {
      nodeRecordBuilder.address(advertisedAddress, advertisedUdpPort, advertisedTcpPort);
    }
    final NodeRecord localNodeRecord = nodeRecordBuilder.build();
    this.discoverySystem =
        discoverySystemBuilder
            .listen(listenAddress, listenUdpPort)
            .privateKey(privateKey)
            .bootnodes(bootnodes)
            .localNodeRecord(localNodeRecord)
            .newAddressHandler(maybeUpdateNodeRecordHandler)
            .localNodeRecordListener(this::localNodeRecordUpdated)
            .build();
    this.kvStore = kvStore;
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.DISCOVERY,
        "live_nodes_current",
        "Current number of live nodes tracked by the discovery system",
        () -> discoverySystem.getBucketStats().getTotalLiveNodeCount());
  }

  private NewAddressHandler maybeUpdateNodeRecord(
      boolean userExplicitlySetAdvertisedIpOrPort, final int advertisedTcpPort) {
    if (userExplicitlySetAdvertisedIpOrPort) {
      return (oldRecord, newAddress) -> Optional.of(oldRecord);
    } else {
      return (oldRecord, newAddress) ->
          Optional.of(
              oldRecord.withNewAddress(
                  newAddress, Optional.of(advertisedTcpPort), localNodePrivateKey));
    }
  }

  private void localNodeRecordUpdated(NodeRecord oldRecord, NodeRecord newRecord) {
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
            node -> nodeRecordConverter.convertToDiscoveryPeer(node, schemaDefinitions).stream());
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
                nodeRecordConverter.convertToDiscoveryPeer(nodeRecord, schemaDefinitions).stream())
        .collect(toList());
  }

  @Override
  public Optional<String> getEnr() {
    return Optional.of(discoverySystem.getLocalNodeRecord().asEnr());
  }

  @Override
  public Optional<String> getDiscoveryAddress() {
    final NodeRecord nodeRecord = discoverySystem.getLocalNodeRecord();
    if (nodeRecord.getUdpAddress().isEmpty()) {
      return Optional.empty();
    }
    final DiscoveryPeer discoveryPeer =
        new DiscoveryPeer(
            (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1),
            nodeRecord.getUdpAddress().get(),
            Optional.empty(),
            currentSchemaDefinitionsSupplier.getAttnetsENRFieldSchema().getDefault(),
            currentSchemaDefinitionsSupplier.getSyncnetsENRFieldSchema().getDefault());

    return Optional.of(MultiaddrUtil.fromDiscoveryPeerAsUdp(discoveryPeer).toString());
  }

  @Override
  public void updateCustomENRField(String fieldName, Bytes value) {
    discoverySystem.updateCustomFieldValue(fieldName, value);
  }

  private Stream<NodeRecord> activeNodes() {
    return discoverySystem.streamLiveNodes();
  }
}
