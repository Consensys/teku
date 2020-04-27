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

package tech.pegasys.artemis.networking.p2p.network;

import static com.google.common.net.InetAddresses.isInetAddress;

import io.libp2p.core.crypto.PrivKey;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import tech.pegasys.artemis.networking.p2p.connection.TargetPeerRange;

public class NetworkConfig {

  private final PrivKey privateKey;
  private final String networkInterface;
  private final Optional<String> advertisedIp;
  private final int listenPort;
  private final int advertisedPort;
  private final List<String> staticPeers;
  private final boolean isDiscoveryEnabled;
  private final List<String> bootnodes;
  private final TargetPeerRange targetPeerRange;

  // Gossip options
  // https://github.com/ethereum/eth2.0-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#the-gossip-domain-gossipsub
  private int gossipD = 6;
  private int gossipDLow = 4;
  private int gossipDHigh = 12;
  private int gossipDLazy = 6;
  private Duration gossipFanoutTTL = Duration.ofSeconds(60);
  private int gossipAdvertise = 3;
  private int gossipHistory = 5;
  private Duration gossipHeartbeatInterval = Duration.ofSeconds(1);

  private boolean logWireCipher = false;
  private boolean logWirePlain = false;
  private boolean logWireMuxFrames = false;
  private boolean logWireGossip = false;

  public NetworkConfig(
      final PrivKey privateKey,
      final String networkInterface,
      final String advertisedIp,
      final int listenPort,
      final int advertisedPort,
      final List<String> staticPeers,
      final boolean isDiscoveryEnabled,
      final List<String> bootnodes,
      final TargetPeerRange targetPeerRange) {

    this.privateKey = privateKey;
    this.networkInterface = networkInterface;

    if (advertisedIp.trim().isEmpty()) {
      this.advertisedIp = Optional.empty();
    } else if (!isInetAddress(advertisedIp)) {
      throw new IllegalArgumentException("Advertised ip is set incorrectly.");
    } else {
      this.advertisedIp = Optional.of(advertisedIp);
    }

    this.listenPort = listenPort;
    this.advertisedPort = advertisedPort;
    this.staticPeers = staticPeers;
    this.isDiscoveryEnabled = isDiscoveryEnabled;
    this.bootnodes = bootnodes;
    this.targetPeerRange = targetPeerRange;
  }

  public PrivKey getPrivateKey() {
    return privateKey;
  }

  public String getNetworkInterface() {
    return networkInterface;
  }

  public Optional<String> getAdvertisedIp() {
    return advertisedIp;
  }

  public int getListenPort() {
    return listenPort;
  }

  public int getAdvertisedPort() {
    return advertisedPort;
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

  public int getGossipD() {
    return gossipD;
  }

  public int getGossipDLow() {
    return gossipDLow;
  }

  public int getGossipDHigh() {
    return gossipDHigh;
  }

  public int getGossipDLazy() {
    return gossipDLazy;
  }

  public Duration getGossipFanoutTTL() {
    return gossipFanoutTTL;
  }

  public int getGossipAdvertise() {
    return gossipAdvertise;
  }

  public int getGossipHistory() {
    return gossipHistory;
  }

  public Duration getGossipHeartbeatInterval() {
    return gossipHeartbeatInterval;
  }

  public boolean isLogWireCipher() {
    return logWireCipher;
  }

  public boolean isLogWirePlain() {
    return logWirePlain;
  }

  public boolean isLogWireMuxFrames() {
    return logWireMuxFrames;
  }

  public boolean isLogWireGossip() {
    return logWireGossip;
  }

  public NetworkConfig setGossipD(int gossipD) {
    this.gossipD = gossipD;
    return this;
  }

  public NetworkConfig setGossipDLow(int gossipDLow) {
    this.gossipDLow = gossipDLow;
    return this;
  }

  public NetworkConfig setGossipDHigh(int gossipDHigh) {
    this.gossipDHigh = gossipDHigh;
    return this;
  }

  public NetworkConfig setGossipDLazy(int gossipDLazy) {
    this.gossipDLazy = gossipDLazy;
    return this;
  }

  public NetworkConfig setGossipFanoutTTL(Duration gossipFanoutTTL) {
    this.gossipFanoutTTL = gossipFanoutTTL;
    return this;
  }

  public NetworkConfig setGossipAdvertise(int gossipAdvertise) {
    this.gossipAdvertise = gossipAdvertise;
    return this;
  }

  public NetworkConfig setGossipHistory(int gossipHistory) {
    this.gossipHistory = gossipHistory;
    return this;
  }

  public NetworkConfig setGossipHeartbeatInterval(Duration gossipHeartbeatInterval) {
    this.gossipHeartbeatInterval = gossipHeartbeatInterval;
    return this;
  }

  public NetworkConfig setLogWireCipher(boolean logWireCipher) {
    this.logWireCipher = logWireCipher;
    return this;
  }

  public NetworkConfig setLogWirePlain(boolean logWirePlain) {
    this.logWirePlain = logWirePlain;
    return this;
  }

  public NetworkConfig setLogWireMuxFrames(boolean logWireMuxFrames) {
    this.logWireMuxFrames = logWireMuxFrames;
    return this;
  }

  public NetworkConfig setLogWireGossip(boolean logWireGossip) {
    this.logWireGossip = logWireGossip;
    return this;
  }
}
