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

package tech.pegasys.teku.networking.p2p.network.config;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.net.InetAddresses.isInetAddress;

import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.PortAvailability;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipPeerScoringConfig.DirectPeerManager;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class NetworkConfig {

  private static final Logger LOG = LogManager.getLogger();

  public static final String DEFAULT_P2P_INTERFACE = "0.0.0.0";
  public static final int DEFAULT_P2P_PORT = 9000;
  public static final boolean DEFAULT_YAMUX_ENABLED = false;

  private final GossipConfig gossipConfig;
  private final WireLogsConfig wireLogsConfig;

  private final boolean isEnabled;
  private final Optional<PrivateKeySource> privateKeySource;
  private final String networkInterface;
  private final Optional<String> advertisedIp;
  private final int listenPort;
  private final OptionalInt advertisedPort;
  private final boolean yamuxEnabled;

  private NetworkConfig(
      final boolean isEnabled,
      final GossipConfig gossipConfig,
      final WireLogsConfig wireLogsConfig,
      final Optional<PrivateKeySource> privateKeySource,
      final String networkInterface,
      final Optional<String> advertisedIp,
      final int listenPort,
      final OptionalInt advertisedPort,
      final boolean yamuxEnabled) {

    this.privateKeySource = privateKeySource;
    this.networkInterface = networkInterface;

    this.advertisedIp = advertisedIp.filter(ip -> !ip.isBlank());
    this.isEnabled = isEnabled;
    if (this.advertisedIp.map(ip -> !isInetAddress(ip)).orElse(false)) {
      throw new InvalidConfigurationException(
          String.format(
              "Advertised ip (%s) is set incorrectly.", this.advertisedIp.orElse("EMPTY")));
    }

    this.listenPort = listenPort;
    this.advertisedPort = advertisedPort;
    this.yamuxEnabled = yamuxEnabled;
    this.gossipConfig = gossipConfig;
    this.wireLogsConfig = wireLogsConfig;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public Optional<PrivateKeySource> getPrivateKeySource() {
    return privateKeySource;
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

  public boolean isYamuxEnabled() {
    return yamuxEnabled;
  }

  public GossipConfig getGossipConfig() {
    return gossipConfig;
  }

  public WireLogsConfig getWireLogsConfig() {
    return wireLogsConfig;
  }

  @SuppressWarnings("AddressSelection")
  private String resolveAnyLocalAddress(final String ipAddress) {
    try {
      final InetAddress advertisedAddress = InetAddress.getByName(ipAddress);
      if (advertisedAddress.isAnyLocalAddress()) {
        final boolean useIPV6 = advertisedAddress instanceof Inet6Address;
        return getSiteLocalOrUniqueLocalAddress(useIPV6);
      } else {
        return ipAddress;
      }
    } catch (final UnknownHostException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private String getSiteLocalOrUniqueLocalAddress(final boolean useIPV6)
      throws UnknownHostException {
    try {
      final Enumeration<NetworkInterface> networkInterfaces =
          NetworkInterface.getNetworkInterfaces();
      while (networkInterfaces.hasMoreElements()) {
        final NetworkInterface networkInterface = networkInterfaces.nextElement();
        final Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
        while (inetAddresses.hasMoreElements()) {
          final InetAddress inetAddress = inetAddresses.nextElement();
          // IPv4 (include only site local addresses)
          if (!useIPV6 && inetAddress instanceof Inet4Address && inetAddress.isSiteLocalAddress()) {
            return inetAddress.getHostAddress();
          }
          // IPv6 (include only site local addresses or ULAs)
          if (useIPV6
              && inetAddress instanceof Inet6Address
              && (inetAddress.isSiteLocalAddress() || isUniqueLocalAddress(inetAddress))) {
            return inetAddress.getHostAddress();
          }
        }
      }
    } catch (final SocketException ex) {
      LOG.error("Failed to find site local or unique local address", ex);
      throw new UnknownHostException(ex.getMessage());
    }
    throw new UnknownHostException(
        String.format("Unable to determine local %s Address", useIPV6 ? "IPv6" : "IPv4"));
  }

  private boolean isUniqueLocalAddress(final InetAddress inetAddress) {
    // Check the first byte to determine if it's in the fc00::/7 range
    // Unique local IPv6 addresses start with 0xfc or 0xfd
    final int firstByte = inetAddress.getAddress()[0] & 0xff; // Convert to unsigned
    return (firstByte == 0xfc || firstByte == 0xfd);
  }

  public static class Builder {

    private final GossipConfig.Builder gossipConfigBuilder = GossipConfig.builder();
    private final WireLogsConfig.Builder wireLogsConfig = WireLogsConfig.builder();

    private Boolean isEnabled = true;
    private Optional<String> privateKeyFile = Optional.empty();
    private Optional<PrivateKeySource> privateKeySource = Optional.empty();
    private String networkInterface = DEFAULT_P2P_INTERFACE;
    private Optional<String> advertisedIp = Optional.empty();
    private int listenPort = DEFAULT_P2P_PORT;
    private OptionalInt advertisedPort = OptionalInt.empty();
    private boolean yamuxEnabled = DEFAULT_YAMUX_ENABLED;

    private Builder() {}

    public NetworkConfig build() {
      return new NetworkConfig(
          isEnabled,
          gossipConfigBuilder.build(),
          wireLogsConfig.build(),
          privateKeySource.or(this::createFileKeySource),
          networkInterface,
          advertisedIp,
          listenPort,
          advertisedPort,
          yamuxEnabled);
    }

    private Optional<PrivateKeySource> createFileKeySource() {
      return privateKeyFile.map(FilePrivateKeySource::new);
    }

    public Builder isEnabled(final Boolean enabled) {
      checkNotNull(enabled);
      isEnabled = enabled;
      return this;
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

    public Builder setPrivateKeySource(PrivateKeySource privateKeySource) {
      checkNotNull(privateKeySource);
      this.privateKeySource = Optional.of(privateKeySource);
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

    public Builder listenPort(final int listenPort) {
      if (!PortAvailability.isPortValid(listenPort)) {
        throw new InvalidConfigurationException(
            String.format("Invalid listenPort: %d", listenPort));
      }
      this.listenPort = listenPort;
      return this;
    }

    public Builder advertisedPort(final OptionalInt advertisedPort) {
      checkNotNull(advertisedPort);
      if (advertisedPort.isPresent()) {
        if (!PortAvailability.isPortValid(advertisedPort.getAsInt())) {
          throw new InvalidConfigurationException(
              String.format("Invalid advertisedPort: %d", advertisedPort.getAsInt()));
        }
      }
      this.advertisedPort = advertisedPort;
      return this;
    }

    public Builder yamuxEnabled(final boolean yamuxEnabled) {
      this.yamuxEnabled = yamuxEnabled;
      return this;
    }

    public Builder directPeers(final List<NodeId> directPeers) {
      checkNotNull(directPeers);
      final DirectPeerManager directPeerManager = directPeers::contains;
      this.gossipConfigBuilder.directPeerManager(directPeerManager);
      return this;
    }
  }
}
