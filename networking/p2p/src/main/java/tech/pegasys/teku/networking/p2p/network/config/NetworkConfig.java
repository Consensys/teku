/*
 * Copyright ConsenSys Software Inc., 2022
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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.PortAvailability;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;

public class NetworkConfig {

  public interface PrivateKeySource {
    Bytes getPrivateKeyBytes();
  }

  private static final Logger LOG = LogManager.getLogger();

  public static final String DEFAULT_P2P_INTERFACE = "0.0.0.0";
  public static final int DEFAULT_P2P_PORT = 9000;

  private final GossipConfig gossipConfig;
  private final WireLogsConfig wireLogsConfig;

  private final boolean isEnabled;
  private final Optional<PrivateKeySource> privateKeySource;
  private final String networkInterface;
  private final Optional<String> advertisedIp;
  private final int listenPort;
  private final OptionalInt advertisedPort;

  private NetworkConfig(
      final boolean isEnabled,
      final GossipConfig gossipConfig,
      final WireLogsConfig wireLogsConfig,
      final Optional<PrivateKeySource> privateKeySource,
      final String networkInterface,
      final Optional<String> advertisedIp,
      final int listenPort,
      final OptionalInt advertisedPort) {

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
        return getSiteLocalAddress();
      } else {
        return ipAddress;
      }
    } catch (UnknownHostException err) {
      LOG.error(
          "Unable to start LibP2PNetwork due to failed attempt at obtaining host address", err);
      return ipAddress;
    }
  }

  private String getSiteLocalAddress() throws UnknownHostException {
    try {
      final InetAddress address = InetAddress.getLocalHost();
      if (address.isAnyLocalAddress()) {
        return address.getHostAddress();
      }
      final Enumeration<NetworkInterface> networkInterfaces =
          NetworkInterface.getNetworkInterfaces();
      while (networkInterfaces.hasMoreElements()) {
        NetworkInterface n = networkInterfaces.nextElement();
        final Enumeration<InetAddress> inetAddresses = n.getInetAddresses();
        while (inetAddresses.hasMoreElements()) {
          InetAddress i = inetAddresses.nextElement();
          if (i.isSiteLocalAddress()) {
            return i.getHostAddress();
          }
        }
      }
    } catch (SocketException e) {
      LOG.error("Failed to find site local address", e);
      throw new UnknownHostException(e.getMessage());
    }
    throw new UnknownHostException("Unable to determine local IP Address");
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
          advertisedPort);
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
  }

  @VisibleForTesting
  public static class FilePrivateKeySource implements PrivateKeySource {
    private final String fileName;

    public FilePrivateKeySource(String fileName) {
      this.fileName = fileName;
    }

    @Override
    public Bytes getPrivateKeyBytes() {
      try {
        return Bytes.fromHexString(Files.readString(Paths.get(fileName)));
      } catch (IOException e) {
        throw new RuntimeException("p2p private key file not found - " + fileName);
      }
    }

    public String getFileName() {
      return fileName;
    }
  }
}
