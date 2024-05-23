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
import org.web3j.utils.Strings;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.PortAvailability;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipPeerScoringConfig.DirectPeerManager;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class NetworkConfig {

  private static final Logger LOG = LogManager.getLogger();

  public static final List<String> DEFAULT_P2P_INTERFACE = List.of("0.0.0.0");
  public static final int DEFAULT_P2P_PORT = 9000;
  public static final int DEFAULT_P2P_PORT_IPV6 = 9090;
  public static final boolean DEFAULT_YAMUX_ENABLED = false;

  private final GossipConfig gossipConfig;
  private final WireLogsConfig wireLogsConfig;

  private final boolean isEnabled;
  private final Optional<PrivateKeySource> privateKeySource;
  private final List<String> networkInterfaces;
  private final Optional<List<String>> advertisedIps;
  private final int listenPort;
  private final int listenPortIpv6;
  private final OptionalInt advertisedPort;
  private final OptionalInt advertisedPortIpv6;
  private final boolean yamuxEnabled;

  private NetworkConfig(
      final boolean isEnabled,
      final GossipConfig gossipConfig,
      final WireLogsConfig wireLogsConfig,
      final Optional<PrivateKeySource> privateKeySource,
      final List<String> networkInterfaces,
      final Optional<List<String>> advertisedIps,
      final int listenPort,
      final int listenPortIpv6,
      final OptionalInt advertisedPort,
      final OptionalInt advertisedPortIpv6,
      final boolean yamuxEnabled) {

    this.privateKeySource = privateKeySource;
    this.networkInterfaces = networkInterfaces;

    this.advertisedIps = advertisedIps.filter(ips -> ips.stream().anyMatch(Strings::isBlank));
    this.isEnabled = isEnabled;
    this.advertisedIps.ifPresent(
        ips ->
            ips.forEach(
                ip -> {
                  if (!isInetAddress(ip)) {
                    throw new InvalidConfigurationException(
                        String.format("Advertised ip (%s) is set incorrectly.", ip));
                  }
                }));
    this.listenPort = listenPort;
    this.listenPortIpv6 = listenPortIpv6;
    this.advertisedPort = advertisedPort;
    this.advertisedPortIpv6 = advertisedPortIpv6;
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

  public List<String> getNetworkInterfaces() {
    return networkInterfaces;
  }

  public List<String> getAdvertisedIps() {
    return advertisedIps.orElse(networkInterfaces).stream()
        .map(this::resolveAnyLocalAddress)
        .toList();
  }

  public boolean hasUserExplicitlySetAdvertisedIp() {
    return advertisedIps.isPresent();
  }

  public int getListenPort() {
    return listenPort;
  }

  public int getListenPortIpv6() {
    return listenPortIpv6;
  }

  public int getAdvertisedPort() {
    return advertisedPort.orElse(listenPort);
  }

  public int getAdvertisedPortIpv6() {
    return advertisedPortIpv6.orElse(listenPortIpv6);
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
        return getLocalAddress(useIPV6);
      } else {
        return ipAddress;
      }
    } catch (final UnknownHostException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private String getLocalAddress(final boolean useIPV6) throws UnknownHostException {
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
          // IPv6 (include site local or unique local addresses)
          if (useIPV6
              && inetAddress instanceof Inet6Address
              && (inetAddress.isSiteLocalAddress() || isUniqueLocalAddress(inetAddress))) {
            return inetAddress.getHostAddress();
          }
        }
      }
    } catch (final SocketException ex) {
      LOG.error("Failed to find local address", ex);
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
    private List<String> networkInterfaces = DEFAULT_P2P_INTERFACE;
    private Optional<List<String>> advertisedIps = Optional.empty();
    private int listenPort = DEFAULT_P2P_PORT;
    private int listenPortIpv6 = DEFAULT_P2P_PORT_IPV6;
    private OptionalInt advertisedPort = OptionalInt.empty();
    private OptionalInt advertisedPortIpv6 = OptionalInt.empty();
    private boolean yamuxEnabled = DEFAULT_YAMUX_ENABLED;

    private Builder() {}

    public NetworkConfig build() {
      return new NetworkConfig(
          isEnabled,
          gossipConfigBuilder.build(),
          wireLogsConfig.build(),
          privateKeySource.or(this::createFileKeySource),
          networkInterfaces,
          advertisedIps,
          listenPort,
          listenPortIpv6,
          advertisedPort,
          advertisedPortIpv6,
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

    public Builder setPrivateKeySource(final PrivateKeySource privateKeySource) {
      checkNotNull(privateKeySource);
      this.privateKeySource = Optional.of(privateKeySource);
      return this;
    }

    public Builder networkInterfaces(final List<String> networkInterfaces) {
      checkNotNull(networkInterfaces);
      this.networkInterfaces = networkInterfaces;
      return this;
    }

    public Builder advertisedIps(final Optional<List<String>> advertisedIps) {
      checkNotNull(advertisedIps);
      this.advertisedIps = advertisedIps;
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

    public Builder listenPortIpv6(final int listenPortIpv6) {
      if (!PortAvailability.isPortValid(listenPortIpv6)) {
        throw new InvalidConfigurationException(
            String.format("Invalid IPv6 listenPort: %d", listenPortIpv6));
      }
      this.listenPortIpv6 = listenPortIpv6;
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

    public Builder advertisedPortIpv6(final OptionalInt advertisedPortIpv6) {
      checkNotNull(advertisedPortIpv6);
      if (advertisedPortIpv6.isPresent()) {
        if (!PortAvailability.isPortValid(advertisedPortIpv6.getAsInt())) {
          throw new InvalidConfigurationException(
              String.format("Invalid advertisedPort: %d", advertisedPortIpv6.getAsInt()));
        }
      }
      this.advertisedPortIpv6 = advertisedPortIpv6;
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
