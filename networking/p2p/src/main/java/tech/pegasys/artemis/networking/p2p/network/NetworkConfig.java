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

import io.libp2p.core.crypto.PrivKey;
import java.util.List;

public class NetworkConfig {

  private final PrivKey privateKey;
  private final String networkInterface;
  private final int listenPort;
  private final int advertisedPort;
  private final List<String> staticPeers;
  private final String discoveryMethod;
  private final List<String> bootnodes;
  private final boolean logWireCipher;
  private final boolean logWirePlain;
  private final boolean logMuxFrames;

  public NetworkConfig(
      final PrivKey privateKey,
      final String networkInterface,
      final int listenPort,
      final int advertisedPort,
      final List<String> staticPeers,
      final String discoveryMethod,
      final List<String> bootnodes,
      final boolean logWireCipher,
      final boolean logWirePlain,
      final boolean logMuxFrames) {
    this.privateKey = privateKey;
    this.networkInterface = networkInterface;
    this.listenPort = listenPort;
    this.advertisedPort = advertisedPort;
    this.staticPeers = staticPeers;
    this.discoveryMethod = discoveryMethod;
    this.bootnodes = bootnodes;
    this.logWireCipher = logWireCipher;
    this.logWirePlain = logWirePlain;
    this.logMuxFrames = logMuxFrames;
  }

  public PrivKey getPrivateKey() {
    return privateKey;
  }

  public String getNetworkInterface() {
    return networkInterface;
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

  public String getDiscoveryMethod() {
    return discoveryMethod;
  }

  public List<String> getBootnodes() {
    return bootnodes;
  }

  public boolean isLogWireCipher() {
    return logWireCipher;
  }

  public boolean isLogWirePlain() {
    return logWirePlain;
  }

  public boolean isLogMuxFrames() {
    return logMuxFrames;
  }
}
