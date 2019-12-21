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
import java.util.Optional;

public final class NetworkConfigBuilder {

  private Optional<PrivKey> privateKey;
  private String networkInterface;
  private int listenPort;
  private int advertisedPort;
  private List<String> peers;
  private boolean logWireCipher;
  private boolean logWirePlain;
  private boolean logMuxFrames;

  public NetworkConfigBuilder privateKey(Optional<PrivKey> privateKey) {
    this.privateKey = privateKey;
    return this;
  }

  public NetworkConfigBuilder networkInterface(String networkInterface) {
    this.networkInterface = networkInterface;
    return this;
  }

  public NetworkConfigBuilder listenPort(int listenPort) {
    this.listenPort = listenPort;
    return this;
  }

  public NetworkConfigBuilder advertisedPort(int advertisedPort) {
    this.advertisedPort = advertisedPort;
    return this;
  }

  public NetworkConfigBuilder peers(List<String> peers) {
    this.peers = peers;
    return this;
  }

  public NetworkConfigBuilder logWireCipher(boolean logWireCipher) {
    this.logWireCipher = logWireCipher;
    return this;
  }

  public NetworkConfigBuilder logWirePlain(boolean logWirePlain) {
    this.logWirePlain = logWirePlain;
    return this;
  }

  public NetworkConfigBuilder logMuxFrames(boolean logMuxFrames) {
    this.logMuxFrames = logMuxFrames;
    return this;
  }

  public NetworkConfig build() {
    return new NetworkConfig(
        privateKey,
        networkInterface,
        listenPort,
        advertisedPort,
        peers,
        logWireCipher,
        logWirePlain,
        logMuxFrames);
  }
}
