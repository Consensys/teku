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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.util.config.PortAvailability;

public class NetworkConfig {
  private static final Logger LOG = LogManager.getLogger();

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
  private final int targetSubnetSubscriberCount;

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
      final int targetSubnetSubscriberCount) {
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
        targetSubnetSubscriberCount,
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
      final int targetSubnetSubscriberCount,
      final GossipConfig gossipConfig,
      final WireLogsConfig wireLogsConfig) {

    this.privateKey = privateKey;
    this.networkInterface = networkInterface;

    this.advertisedIp = advertisedIp.filter(ip -> !ip.isBlank());
    this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
    if (this.advertisedIp.map(ip -> !isInetAddress(ip)).orElse(false)) {
      throw new InvalidConfigurationException(
          String.format(
              "Advertised ip (%s) is set incorrectly.", this.advertisedIp.orElse("EMPTY")));
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

  public void validateListenPortAvailable() {
    if (listenPort != 0 && !PortAvailability.isPortAvailable(listenPort)) {
      throw new InvalidConfigurationException(
          String.format(
              "P2P Port %d (TCP/UDP) is already in use. "
                  + "Check for other processes using this port.",
              listenPort));
    }
  }

  public PrivKey getPrivateKey() {
    return privateKey;
  }

  public String getNetworkInterface() {
    return networkInterface;
  }

  public String getAdvertisedIp() {
    return resolveAnyLocalAddress(advertisedIp.orElse(networkInterface));
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

  public int getTargetSubnetSubscriberCount() {
    return targetSubnetSubscriberCount;
  }

  public GossipConfig getGossipConfig() {
    return gossipConfig;
  }

  public WireLogsConfig getWireLogsConfig() {
    return wireLogsConfig;
  }

  private String resolveAnyLocalAddress(final String ipAddress) {
    try {
      final InetAddress advertisedAddress = InetAddress.getByName(ipAddress);
      if (advertisedAddress.isAnyLocalAddress()) {
        return InetAddress.getLocalHost().getHostAddress();
      } else {
        return ipAddress;
      }
    } catch (UnknownHostException err) {
      LOG.error(
          "Unable to start LibP2PNetwork due to failed attempt at obtaining host address", err);
      return ipAddress;
    }
  }
}
