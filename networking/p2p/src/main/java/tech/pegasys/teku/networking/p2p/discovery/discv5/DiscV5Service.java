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

import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.libp2p.MultiaddrUtil;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.storage.store.KeyValueStore;

public class DiscV5Service extends Service implements DiscoveryService {
  private static final String SEQ_NO_STORE_KEY = "local-enr-seqno";

  public static DiscoveryService create(
      NetworkConfig p2pConfig, KeyValueStore<String, Bytes> kvStore) {
    return new DiscV5Service(p2pConfig, kvStore);
  }

  private final DiscoverySystem discoverySystem;
  private final KeyValueStore<String, Bytes> kvStore;

  private DiscV5Service(NetworkConfig p2pConfig, KeyValueStore<String, Bytes> kvStore) {
    final Bytes privateKey = Bytes.wrap(p2pConfig.getPrivateKey().raw());
    final String listenAddress = p2pConfig.getNetworkInterface();
    final int listenPort = p2pConfig.getListenPort();
    final String advertisedAddress = p2pConfig.getAdvertisedIp();
    final int advertisedPort = p2pConfig.getAdvertisedPort();
    final List<String> bootnodes = p2pConfig.getBootnodes();
    final UInt64 seqNo =
        kvStore.get(SEQ_NO_STORE_KEY).map(UInt64::fromBytes).orElse(UInt64.ZERO).add(1);
    discoverySystem =
        new DiscoverySystemBuilder()
            .listen(listenAddress, listenPort)
            .privateKey(privateKey)
            .bootnodes(bootnodes.toArray(new String[0]))
            .localNodeRecord(
                new NodeRecordBuilder()
                    .privateKey(privateKey)
                    .address(advertisedAddress, advertisedPort)
                    .seq(seqNo)
                    .build())
            .localNodeRecordListener(this::localNodeRecordUpdated)
            .build();
    this.kvStore = kvStore;
  }

  private void localNodeRecordUpdated(NodeRecord oldRecord, NodeRecord newRecord) {
    kvStore.put(SEQ_NO_STORE_KEY, newRecord.getSeq().toBytes());
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.of(discoverySystem.start());
  }

  @Override
  protected SafeFuture<?> doStop() {
    discoverySystem.stop();
    return SafeFuture.completedFuture(null);
  }

  @Override
  public Stream<DiscoveryPeer> streamKnownPeers() {
    return activeNodes().map(NodeRecordConverter::convertToDiscoveryPeer).flatMap(Optional::stream);
  }

  @Override
  public SafeFuture<Void> searchForPeers() {
    return SafeFuture.of(discoverySystem.searchForNewPeers());
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
            new Bitvector(ATTESTATION_SUBNET_COUNT));

    return Optional.of(MultiaddrUtil.fromDiscoveryPeerAsUdp(discoveryPeer).toString());
  }

  @Override
  public void updateCustomENRField(String fieldName, Bytes value) {
    discoverySystem.updateCustomFieldValue(fieldName, value);
  }

  private Stream<NodeRecord> activeNodes() {
    return discoverySystem
        .streamKnownNodes()
        .filter(record -> record.getStatus() == NodeStatus.ACTIVE)
        .map(NodeRecordInfo::getNode);
  }
}
