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

package tech.pegasys.teku.networking.p2p.network;

import static com.google.common.net.InetAddresses.isInetAddress;

import io.libp2p.core.crypto.PrivKey;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;

public class NetworkConfig {

  private final PrivKey privateKey;
  private final String networkInterface;
  private final Optional<String> advertisedIp;
  private final int listenPort;
  private final OptionalInt advertisedPort;
  private final List<String> staticPeers;
  private final boolean isDiscoveryEnabled;
  private final List<String> bootnodes;
  private final TargetPeerRange targetPeerRange;
  private final GossipConfig gossipConfig;
  private final WireLogsConfig wireLogsConfig;

  public NetworkConfig(
      final PrivKey privateKey,
      final String networkInterface,
      final Optional<String> advertisedIp,
      final int listenPort,
      final OptionalInt advertisedPort,
      final List<String> staticPeers,
      final boolean isDiscoveryEnabled,
      final List<String> bootnodes,
      final TargetPeerRange targetPeerRange) {
    this(
        privateKey,
        networkInterface,
        advertisedIp,
        listenPort,
        advertisedPort,
        staticPeers,
        isDiscoveryEnabled,
        bootnodes,
        targetPeerRange,
        GossipConfig.DEFAULT_CONFIG,
        WireLogsConfig.DEFAULT_CONFIG);
  }

  public NetworkConfig(
      final PrivKey privateKey,
      final String networkInterface,
      final Optional<String> advertisedIp,
      final int listenPort,
      final OptionalInt advertisedPort,
      final List<String> staticPeers,
      final boolean isDiscoveryEnabled,
      final List<String> bootnodes,
      final TargetPeerRange targetPeerRange,
      final GossipConfig gossipConfig,
      final WireLogsConfig wireLogsConfig) {

    this.privateKey = privateKey;
    this.networkInterface = networkInterface;

    this.advertisedIp = advertisedIp.filter(ip -> !ip.isBlank());
    if (this.advertisedIp.map(ip -> !isInetAddress(ip)).orElse(false)) {
      throw new IllegalArgumentException("Advertised ip is set incorrectly.");
    }

    this.listenPort = listenPort;
    this.advertisedPort = advertisedPort;
    this.staticPeers = staticPeers;
    this.isDiscoveryEnabled = isDiscoveryEnabled;
    this.bootnodes = bootnodes;
    this.targetPeerRange = targetPeerRange;
    this.gossipConfig = gossipConfig;
    this.wireLogsConfig = wireLogsConfig;
  }

  public PrivKey getPrivateKey() {
    return privateKey;
  }

  public String getNetworkInterface() {
    return networkInterface;
  }

  public String getAdvertisedIp() {
    return advertisedIp.orElse(networkInterface);
  }

  public int getListenPort() {
    return listenPort;
  }

  public int getAdvertisedPort() {
    return advertisedPort.orElse(listenPort);
  }

  public List<String> getStaticPeers() {
    return staticPeers;
  }

  public boolean isDiscoveryEnabled() {
    return isDiscoveryEnabled;
  }

  public List<String> getBootnodes() {
    return bootnodes;
  }

  public TargetPeerRange getTargetPeerRange() {
    return targetPeerRange;
  }

  public GossipConfig getGossipConfig() {
    return gossipConfig;
  }

  public WireLogsConfig getWireLogsConfig() {
    return wireLogsConfig;
  }
}
