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

package tech.pegasys.teku.networking.p2p.network;

public class WireLogsConfig {
  private final boolean logWireCipher;
  private final boolean logWirePlain;
  private final boolean logWireMuxFrames;
  private final boolean logWireGossip;

  public static final WireLogsConfig DEFAULT_CONFIG =
      new WireLogsConfig(false, false, false, false);

  public WireLogsConfig(
      boolean logWireCipher,
      boolean logWirePlain,
      boolean logWireMuxFrames,
      boolean logWireGossip) {
    this.logWireCipher = logWireCipher;
    this.logWirePlain = logWirePlain;
    this.logWireMuxFrames = logWireMuxFrames;
    this.logWireGossip = logWireGossip;
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
}
