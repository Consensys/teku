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

package tech.pegasys.teku.networking.p2p.network.config;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.net.InetAddresses.isInetAddress;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.io.PortAvailability;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

public class NetworkConfig {
  private static final Logger LOG = LogManager.getLogger();

  private final GossipConfig gossipConfig;
  private final WireLogsConfig wireLogsConfig;

  private final Optional<String> privateKeyFile;
  private final String networkInterface;
  private final Optional<String> advertisedIp;
  private final int listenPort;
  private final OptionalInt advertisedPort;
  private final List<String> staticPeers;
  private final List<String> bootnodes;

  private NetworkConfig(
      final GossipConfig gossipConfig,
      final WireLogsConfig wireLogsConfig,
      final Optional<String> privateKeyFile,
      final String networkInterface,
      final Optional<String> advertisedIp,
      final int listenPort,
      final OptionalInt advertisedPort,
      final List<String> staticPeers,
      final List<String> bootnodes) {

    this.privateKeyFile = privateKeyFile;
    this.networkInterface = networkInterface;

    this.advertisedIp = advertisedIp.filter(ip -> !ip.isBlank());
    if (this.advertisedIp.map(ip -> !isInetAddress(ip)).orElse(false)) {
      throw new InvalidConfigurationException(
          String.format(
              "Advertised ip (%s) is set incorrectly.", this.advertisedIp.orElse("EMPTY")));
    }

    this.listenPort = listenPort;
    this.advertisedPort = advertisedPort;
    this.staticPeers = staticPeers;
    this.bootnodes = bootnodes;
    this.gossipConfig = gossipConfig;
    this.wireLogsConfig = wireLogsConfig;
  }

  public static Builder builder() {
    return new Builder();
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

  public Optional<String> getPrivateKeyFile() {
    return privateKeyFile;
  }

  public String getNetworkInterface() {
    return networkInterface;
  }

  public String getAdvertisedIp() {
    return resolveAnyLocalAddress(advertisedIp.orElse(networkInterface));
  }

  public boolean hasUserExplicitlySetAdvertisedIp() {
    return advertisedIp.isPresent();
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

  public List<String> getBootnodes() {
    return bootnodes;
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

  public static class Builder {
    public static final int DEFAULT_P2P_PORT = 9000;

    private GossipConfig.Builder gossipConfigBuilder = GossipConfig.builder();
    private WireLogsConfig.Builder wireLogsConfig = WireLogsConfig.builder();

    private Optional<String> privateKeyFile = Optional.empty();
    private String networkInterface = "0.0.0.0";
    private Optional<String> advertisedIp = Optional.empty();
    private Integer listenPort = DEFAULT_P2P_PORT;
    private OptionalInt advertisedPort = OptionalInt.empty();
    private List<String> staticPeers = Collections.emptyList();
    private List<String> bootnodes = Collections.emptyList();

    private Builder() {}

    public NetworkConfig build() {
      return new NetworkConfig(
          gossipConfigBuilder.build(),
          wireLogsConfig.build(),
          privateKeyFile,
          networkInterface,
          advertisedIp,
          listenPort,
          advertisedPort,
          staticPeers,
          bootnodes);
    }

    public Builder gossipConfig(final Consumer<GossipConfig.Builder> consumer) {
      consumer.accept(gossipConfigBuilder);
      return this;
    }

    public Builder wireLogs(final Consumer<WireLogsConfig.Builder> consumer) {
      consumer.accept(wireLogsConfig);
      return this;
    }

    public Builder privateKeyFile(final String privateKeyFile) {
      checkNotNull(privateKeyFile);
      this.privateKeyFile = Optional.of(privateKeyFile).filter(f -> !f.isBlank());
      return this;
    }

    public Builder networkInterface(final String networkInterface) {
      checkNotNull(networkInterface);
      this.networkInterface = networkInterface;
      return this;
    }

    public Builder advertisedIp(final Optional<String> advertisedIp) {
      checkNotNull(advertisedIp);
      this.advertisedIp = advertisedIp;
      return this;
    }

    public Builder listenPort(final Integer listenPort) {
      checkNotNull(listenPort);
      this.listenPort = listenPort;
      return this;
    }

    public Builder advertisedPort(final OptionalInt advertisedPort) {
      checkNotNull(advertisedPort);
      this.advertisedPort = advertisedPort;
      return this;
    }

    public Builder staticPeers(final List<String> staticPeers) {
      checkNotNull(staticPeers);
      this.staticPeers = staticPeers;
      return this;
    }

    public Builder bootnodes(final List<String> bootnodes) {
      checkNotNull(bootnodes);
      this.bootnodes = bootnodes;
      return this;
    }
  }
}
