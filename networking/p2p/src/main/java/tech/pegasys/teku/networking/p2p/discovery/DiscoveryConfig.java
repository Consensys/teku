/*
 * Copyright 2021 ConsenSys AG.
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

import java.util.Collections;
import java.util.List;

public class DiscoveryConfig {
  private final boolean isDiscoveryEnabled;
  private final List<String> staticPeers;
  private final List<String> bootnodes;
  private final int minPeers;
  private final int maxPeers;
  private final int minRandomlySelectedPeers;

  private DiscoveryConfig(
      final boolean isDiscoveryEnabled,
      final List<String> staticPeers,
      final List<String> bootnodes,
      final int minPeers,
      final int maxPeers,
      final int minRandomlySelectedPeers) {
    this.isDiscoveryEnabled = isDiscoveryEnabled;
    this.staticPeers = staticPeers;
    this.bootnodes = bootnodes;
    this.minPeers = minPeers;
    this.maxPeers = maxPeers;
    this.minRandomlySelectedPeers = minRandomlySelectedPeers;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isDiscoveryEnabled() {
    return isDiscoveryEnabled;
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

  public static class Builder {
    private Boolean isDiscoveryEnabled = true;
    private List<String> staticPeers = Collections.emptyList();
    private List<String> bootnodes = Collections.emptyList();
    private int minPeers = 64;
    private int maxPeers = 74;
    private int minRandomlySelectedPeers = 2;

    private Builder() {}

    public Builder isDiscoveryEnabled(final Boolean discoveryEnabled) {
      checkNotNull(discoveryEnabled);
      isDiscoveryEnabled = discoveryEnabled;
      return this;
    }

    public DiscoveryConfig build() {
      return new DiscoveryConfig(
          isDiscoveryEnabled, staticPeers, bootnodes, minPeers, maxPeers, minRandomlySelectedPeers);
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

    public Builder minPeers(final Integer minPeers) {
      checkNotNull(minPeers);
      this.minPeers = minPeers;
      return this;
    }

    public Builder maxPeers(final Integer maxPeers) {
      checkNotNull(maxPeers);
      this.maxPeers = maxPeers;
      return this;
    }

    public Builder minRandomlySelectedPeers(final Integer minRandomlySelectedPeers) {
      checkNotNull(minRandomlySelectedPeers);
      this.minRandomlySelectedPeers = minRandomlySelectedPeers;
      return this;
    }
  }
}
