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

import io.libp2p.core.multiformats.Multiaddr;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.libp2p.MultiaddrUtil;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.util.async.SafeFuture;

public class DiscV5Service extends Service implements DiscoveryService {

  private final DiscoverySystem discoverySystem;
  private final Multiaddr advertisedAddr;

  private DiscV5Service(final DiscoverySystem discoverySystem, NetworkConfig config) {
    this.discoverySystem = discoverySystem;
    final byte[] nodeId = discoverySystem.getLocalNodeRecord().getNodeId().toArray();
    this.advertisedAddr = getAdvertisedAddr(config, nodeId);
  }

  public static DiscoveryService create(NetworkConfig p2pConfig) {
    final Bytes privateKey = Bytes.wrap(p2pConfig.getPrivateKey().raw());
    final String listenAddress = p2pConfig.getNetworkInterface();
    final int listenPort = p2pConfig.getListenPort();
    final String advertisedAddress = p2pConfig.getAdvertisedIp();
    final int advertisedPort = p2pConfig.getAdvertisedPort();
    final List<String> bootnodes = p2pConfig.getBootnodes();
    final DiscoverySystem discoveryManager =
        new DiscoverySystemBuilder()
            .listen(listenAddress, listenPort)
            .privateKey(privateKey)
            .bootnodes(bootnodes.toArray(new String[0]))
            .localNodeRecord(
                new NodeRecordBuilder()
                    .privateKey(privateKey)
                    .address(advertisedAddress, advertisedPort)
                    .build())
            .build();
    return new DiscV5Service(discoveryManager, p2pConfig);
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
    return Optional.of(advertisedAddr.toString());
  }

  private Multiaddr getAdvertisedAddr(NetworkConfig config, final byte[] nodeId) {
    try {
      final InetSocketAddress resolvedAddress = MultiaddrUtil.getResolvedInetSocketAddress(config);
      return MultiaddrUtil.fromInetSocketAddress(resolvedAddress, nodeId);
    } catch (UnknownHostException err) {
      throw new RuntimeException(
          "Unable to start discovery node due to failed attempt at obtaining host address", err);
    }
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
