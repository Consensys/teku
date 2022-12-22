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

package tech.pegasys.teku.networking.p2p.discovery;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.networking.p2p.network.config.NetworkConfig.DEFAULT_P2P_PORT;

import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.PortAvailability;

public class DiscoveryConfig {

  public static final boolean DEFAULT_P2P_DISCOVERY_ENABLED = true;
  public static final int DEFAULT_P2P_PEERS_LOWER_BOUND = 64;
  public static final int DEFAULT_P2P_PEERS_UPPER_BOUND = 100;
  public static final boolean DEFAULT_SITE_LOCAL_ADDRESSES_ENABLED = true;

  private final boolean isDiscoveryEnabled;
  private final int listenUdpPort;
  private final OptionalInt advertisedUdpPort;
  private final List<String> staticPeers;
  private final List<String> bootnodes;
  private final int minPeers;
  private final int maxPeers;
  private final int minRandomlySelectedPeers;
  private final boolean siteLocalAddressesEnabled;

  private DiscoveryConfig(
      final boolean isDiscoveryEnabled,
      final int listenUdpPort,
      final OptionalInt advertisedUdpPort,
      final List<String> staticPeers,
      final List<String> bootnodes,
      final int minPeers,
      final int maxPeers,
      final int minRandomlySelectedPeers,
      final boolean siteLocalAddressesEnabled) {
    this.isDiscoveryEnabled = isDiscoveryEnabled;
    this.listenUdpPort = listenUdpPort;
    this.advertisedUdpPort = advertisedUdpPort;
    this.staticPeers = staticPeers;
    this.bootnodes = bootnodes;
    this.minPeers = minPeers;
    this.maxPeers = maxPeers;
    this.minRandomlySelectedPeers = minRandomlySelectedPeers;
    this.siteLocalAddressesEnabled = siteLocalAddressesEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isDiscoveryEnabled() {
    return isDiscoveryEnabled;
  }

  public int getListenUdpPort() {
    return listenUdpPort;
  }

  public int getAdvertisedUdpPort() {
    return advertisedUdpPort.orElse(listenUdpPort);
  }

  public List<String> getStaticPeers() {
    return staticPeers;
  }

  public List<String> getBootnodes() {
    return bootnodes;
  }

  public int getMinPeers() {
    return minPeers;
  }

  public int getMaxPeers() {
    return maxPeers;
  }

  public int getMinRandomlySelectedPeers() {
    return minRandomlySelectedPeers;
  }

  public boolean areSiteLocalAddressesEnabled() {
    return siteLocalAddressesEnabled;
  }

  public static class Builder {
    private Boolean isDiscoveryEnabled = DEFAULT_P2P_DISCOVERY_ENABLED;
    private List<String> staticPeers = Collections.emptyList();
    private List<String> bootnodes;
    private int minPeers = DEFAULT_P2P_PEERS_LOWER_BOUND;
    private int maxPeers = DEFAULT_P2P_PEERS_UPPER_BOUND;
    private OptionalInt minRandomlySelectedPeers = OptionalInt.empty();
    private OptionalInt listenUdpPort = OptionalInt.empty();
    private OptionalInt advertisedUdpPort = OptionalInt.empty();
    private boolean siteLocalAddressesEnabled = DEFAULT_SITE_LOCAL_ADDRESSES_ENABLED;

    private Builder() {}

    public DiscoveryConfig build() {
      initMissingDefaults();

      return new DiscoveryConfig(
          isDiscoveryEnabled,
          listenUdpPort.orElseThrow(),
          advertisedUdpPort,
          staticPeers,
          bootnodes == null ? Collections.emptyList() : bootnodes,
          minPeers,
          maxPeers,
          minRandomlySelectedPeers.orElseThrow(),
          siteLocalAddressesEnabled);
    }

    private void initMissingDefaults() {
      if (minRandomlySelectedPeers.isEmpty()) {
        if (maxPeers == 0) {
          minRandomlySelectedPeers = OptionalInt.of(0);
        } else {
          minRandomlySelectedPeers = OptionalInt.of(Math.max(1, minPeers * 2 / 10));
        }
      }
      if (listenUdpPort.isEmpty()) {
        listenUdpPort = OptionalInt.of(DEFAULT_P2P_PORT);
      }
    }

    public Builder isDiscoveryEnabled(final Boolean discoveryEnabled) {
      checkNotNull(discoveryEnabled);
      isDiscoveryEnabled = discoveryEnabled;
      return this;
    }

    public Builder listenUdpPort(final int listenUdpPort) {
      if (!PortAvailability.isPortValid(listenUdpPort)) {
        throw new InvalidConfigurationException(
            String.format("Invalid listenUdpPort: %d", listenUdpPort));
      }
      this.listenUdpPort = OptionalInt.of(listenUdpPort);
      return this;
    }

    public Builder listenUdpPortDefault(final int listenUdpPort) {
      if (!PortAvailability.isPortValid(listenUdpPort)) {
        throw new InvalidConfigurationException(
            String.format("Invalid listenUdpPortDefault: %d", listenUdpPort));
      }
      if (this.listenUdpPort.isEmpty()) {
        this.listenUdpPort = OptionalInt.of(listenUdpPort);
      }
      return this;
    }

    public Builder advertisedUdpPort(final OptionalInt advertisedUdpPort) {
      checkNotNull(advertisedUdpPort);
      if (advertisedUdpPort.isPresent()) {
        if (!PortAvailability.isPortValid(advertisedUdpPort.getAsInt())) {
          throw new InvalidConfigurationException(
              String.format("Invalid advertisedUdpPort: %d", advertisedUdpPort.getAsInt()));
        }
      }
      this.advertisedUdpPort = advertisedUdpPort;
      return this;
    }

    public Builder advertisedUdpPortDefault(final OptionalInt advertisedUdpPort) {
      checkNotNull(advertisedUdpPort);
      if (advertisedUdpPort.isPresent()) {
        if (!PortAvailability.isPortValid(advertisedUdpPort.getAsInt())) {
          throw new InvalidConfigurationException(
              String.format("Invalid advertisedUdpPortDefault: %d", advertisedUdpPort.getAsInt()));
        }
      }
      if (this.advertisedUdpPort.isEmpty()) {
        this.advertisedUdpPort = advertisedUdpPort;
      }
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

    public Builder bootnodesDefault(final List<String> bootnodes) {
      checkNotNull(bootnodes);
      if (this.bootnodes == null) {
        this.bootnodes = bootnodes;
      }
      return this;
    }

    public Builder minPeers(final Integer minPeers) {
      checkNotNull(minPeers);
      if (minPeers < 0) {
        throw new InvalidConfigurationException(String.format("Invalid minPeers: %d", minPeers));
      }
      this.minPeers = minPeers;
      return this;
    }

    public Builder maxPeers(final Integer maxPeers) {
      checkNotNull(maxPeers);
      if (maxPeers < 0) {
        throw new InvalidConfigurationException(String.format("Invalid maxPeers: %d", maxPeers));
      }
      this.maxPeers = maxPeers;
      return this;
    }

    public Builder minRandomlySelectedPeers(final Integer minRandomlySelectedPeers) {
      checkNotNull(minRandomlySelectedPeers);
      if (minRandomlySelectedPeers < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid minRandomlySelectedPeers: %d", minRandomlySelectedPeers));
      }
      this.minRandomlySelectedPeers = OptionalInt.of(minRandomlySelectedPeers);
      return this;
    }

    public Builder siteLocalAddressesEnabled(final boolean siteLocalAddressesEnabled) {
      this.siteLocalAddressesEnabled = siteLocalAddressesEnabled;
      return this;
    }
  }
}
