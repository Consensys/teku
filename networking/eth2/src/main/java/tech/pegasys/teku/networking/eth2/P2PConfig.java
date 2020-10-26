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

package tech.pegasys.teku.networking.eth2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class P2PConfig {

  private final boolean p2pEnabled;
  private final String p2pInterface;
  private final int p2pPort;
  private final boolean p2pDiscoveryEnabled;
  private final List<String> p2pDiscoveryBootnodes;
  private final Optional<String> p2pAdvertisedIp;
  private final OptionalInt p2pAdvertisedPort;
  private final String p2pPrivateKeyFile;
  private final int p2pPeerLowerBound;
  private final int p2pPeerUpperBound;
  private final int targetSubnetSubscriberCount;
  private final List<String> p2pStaticPeers;
  private final boolean multiPeerSyncEnabled;

  private P2PConfig(
      final boolean p2pEnabled,
      final String p2pInterface,
      final int p2pPort,
      final boolean p2pDiscoveryEnabled,
      final List<String> p2pDiscoveryBootnodes,
      final Optional<String> p2pAdvertisedIp,
      final OptionalInt p2pAdvertisedPort,
      final String p2pPrivateKeyFile,
      final int p2pPeerLowerBound,
      final int p2pPeerUpperBound,
      final int targetSubnetSubscriberCount,
      final List<String> p2pStaticPeers,
      final boolean multiPeerSyncEnabled) {
    this.p2pEnabled = p2pEnabled;
    this.p2pInterface = p2pInterface;
    this.p2pPort = p2pPort;
    this.p2pDiscoveryEnabled = p2pDiscoveryEnabled;
    this.p2pDiscoveryBootnodes = p2pDiscoveryBootnodes;
    this.p2pAdvertisedIp = p2pAdvertisedIp;
    this.p2pAdvertisedPort = p2pAdvertisedPort;
    this.p2pPrivateKeyFile = p2pPrivateKeyFile;
    this.p2pPeerLowerBound = p2pPeerLowerBound;
    this.p2pPeerUpperBound = p2pPeerUpperBound;
    this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
    this.p2pStaticPeers = p2pStaticPeers;
    this.multiPeerSyncEnabled = multiPeerSyncEnabled;
  }

  public static P2PConfigBuilder builder() {
    return new P2PConfigBuilder();
  }

  public boolean isP2pEnabled() {
    return p2pEnabled;
  }

  public String getP2pInterface() {
    return p2pInterface;
  }

  public int getP2pPort() {
    return p2pPort;
  }

  public boolean isP2pDiscoveryEnabled() {
    return p2pDiscoveryEnabled;
  }

  public List<String> getP2pDiscoveryBootnodes() {
    return p2pDiscoveryBootnodes;
  }

  public Optional<String> getP2pAdvertisedIp() {
    return p2pAdvertisedIp;
  }

  public OptionalInt getP2pAdvertisedPort() {
    return p2pAdvertisedPort;
  }

  public String getP2pPrivateKeyFile() {
    return p2pPrivateKeyFile;
  }

  public int getP2pPeerLowerBound() {
    return p2pPeerLowerBound;
  }

  public int getP2pPeerUpperBound() {
    return p2pPeerUpperBound;
  }

  public int getTargetSubnetSubscriberCount() {
    return targetSubnetSubscriberCount;
  }

  public int getMinimumRandomlySelectedPeerCount() {
    return Math.min(1, p2pPeerLowerBound * 2 / 10);
  }

  public List<String> getP2pStaticPeers() {
    return p2pStaticPeers;
  }

  public boolean isMultiPeerSyncEnabled() {
    return multiPeerSyncEnabled;
  }

  public static final class P2PConfigBuilder {

    private boolean p2pEnabled;
    private String p2pInterface;
    private int p2pPort;
    private boolean p2pDiscoveryEnabled;
    private List<String> p2pDiscoveryBootnodes = new ArrayList<>();
    private Optional<String> p2pAdvertisedIp;
    private OptionalInt p2pAdvertisedPort;
    private String p2pPrivateKeyFile;
    private int p2pPeerLowerBound;
    private int p2pPeerUpperBound;
    private int targetSubnetSubscriberCount;
    private List<String> p2pStaticPeers = new ArrayList<>();
    private boolean multiPeerSyncEnabled;

    private P2PConfigBuilder() {}

    public P2PConfigBuilder p2pEnabled(boolean p2pEnabled) {
      this.p2pEnabled = p2pEnabled;
      return this;
    }

    public P2PConfigBuilder p2pInterface(String p2pInterface) {
      this.p2pInterface = p2pInterface;
      return this;
    }

    public P2PConfigBuilder p2pPort(int p2pPort) {
      this.p2pPort = p2pPort;
      return this;
    }

    public P2PConfigBuilder p2pDiscoveryEnabled(boolean p2pDiscoveryEnabled) {
      this.p2pDiscoveryEnabled = p2pDiscoveryEnabled;
      return this;
    }

    public P2PConfigBuilder p2pDiscoveryBootnodes(List<String> p2pDiscoveryBootnodes) {
      this.p2pDiscoveryBootnodes = p2pDiscoveryBootnodes;
      return this;
    }

    public P2PConfigBuilder p2pAdvertisedIp(Optional<String> p2pAdvertisedIp) {
      this.p2pAdvertisedIp = p2pAdvertisedIp;
      return this;
    }

    public P2PConfigBuilder p2pAdvertisedPort(OptionalInt p2pAdvertisedPort) {
      this.p2pAdvertisedPort = p2pAdvertisedPort;
      return this;
    }

    public P2PConfigBuilder p2pPrivateKeyFile(String p2pPrivateKeyFile) {
      this.p2pPrivateKeyFile = p2pPrivateKeyFile;
      return this;
    }

    public P2PConfigBuilder p2pPeerLowerBound(int p2pPeerLowerBound) {
      this.p2pPeerLowerBound = p2pPeerLowerBound;
      return this;
    }

    public P2PConfigBuilder p2pPeerUpperBound(int p2pPeerUpperBound) {
      this.p2pPeerUpperBound = p2pPeerUpperBound;
      return this;
    }

    public P2PConfigBuilder targetSubnetSubscriberCount(int targetSubnetSubscriberCount) {
      this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
      return this;
    }

    public P2PConfigBuilder p2pStaticPeers(List<String> p2pStaticPeers) {
      this.p2pStaticPeers = p2pStaticPeers;
      return this;
    }

    public P2PConfigBuilder multiPeerSyncEnabled(boolean multiPeerSyncEnabled) {
      this.multiPeerSyncEnabled = multiPeerSyncEnabled;
      return this;
    }

    public P2PConfig build() {
      return new P2PConfig(
          p2pEnabled,
          p2pInterface,
          p2pPort,
          p2pDiscoveryEnabled,
          p2pDiscoveryBootnodes,
          p2pAdvertisedIp,
          p2pAdvertisedPort,
          p2pPrivateKeyFile,
          p2pPeerLowerBound,
          p2pPeerUpperBound,
          targetSubnetSubscriberCount,
          p2pStaticPeers,
          multiPeerSyncEnabled);
    }
  }
}
