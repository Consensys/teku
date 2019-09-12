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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import io.libp2p.core.crypto.PrivKey;
import java.util.List;
import java.util.Optional;

public class JvmLibP2PConfig {

  private final Optional<PrivKey> privateKey;
  private final Optional<Integer> listenPort;
  private final List<String> peers;
  private final boolean logWireCipher;
  private final boolean logWirePlain;
  private final boolean logMuxFrames;

  public JvmLibP2PConfig(
      final Optional<PrivKey> privateKey,
      final Optional<Integer> listenPort,
      final List<String> peers,
      final boolean logWireCipher,
      final boolean logWirePlain,
      final boolean logMuxFrames) {
    this.privateKey = privateKey;
    this.listenPort = listenPort;
    this.peers = peers;
    this.logWireCipher = logWireCipher;
    this.logWirePlain = logWirePlain;
    this.logMuxFrames = logMuxFrames;
  }

  public Optional<PrivKey> getPrivateKey() {
    return privateKey;
  }

  public Optional<Integer> getListenPort() {
    return listenPort;
  }

  public List<String> getPeers() {
    return peers;
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
