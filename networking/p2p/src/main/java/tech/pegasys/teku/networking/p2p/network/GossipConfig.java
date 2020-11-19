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

import java.time.Duration;

/**
 * Gossip options
 * https://github.com/ethereum/eth2.0-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#the-gossip-domain-gossipsub
 */
public class GossipConfig {
  public static final int DEFAULT_D = 6;
  public static final int DEFAULT_D_LOW = 5;
  public static final int DEFAULT_D_HIGH = 12;
  public static final int DEFAULT_D_LAZY = 6;
  public static final Duration DEFAULT_FANOUT_TTL = Duration.ofSeconds(60);
  public static final int DEFAULT_ADVERTISE = 3;
  public static final int DEFAULT_HISTORY = 6;
  public static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofMillis(700);
  public static final Duration DEFAULT_SEEN_TTL = DEFAULT_HEARTBEAT_INTERVAL.multipliedBy(550);

  public static final GossipConfig DEFAULT_CONFIG =
      new GossipConfig(
          DEFAULT_D,
          DEFAULT_D_LOW,
          DEFAULT_D_HIGH,
          DEFAULT_D_LAZY,
          DEFAULT_FANOUT_TTL,
          DEFAULT_ADVERTISE,
          DEFAULT_HISTORY,
          DEFAULT_HEARTBEAT_INTERVAL,
          DEFAULT_SEEN_TTL);

  private final int d;
  private final int dLow;
  private final int dHigh;
  private final int dLazy;
  private final Duration fanoutTTL;
  private final int advertise;
  private final int history;
  private final Duration heartbeatInterval;
  private final Duration seenTTL;

  public GossipConfig(
      int d,
      int dLow,
      int dHigh,
      int dLazy,
      Duration fanoutTTL,
      int advertise,
      int history,
      Duration heartbeatInterval,
      Duration seenTTL) {
    this.d = d;
    this.dLow = dLow;
    this.dHigh = dHigh;
    this.dLazy = dLazy;
    this.fanoutTTL = fanoutTTL;
    this.advertise = advertise;
    this.history = history;
    this.heartbeatInterval = heartbeatInterval;
    this.seenTTL = seenTTL;
  }

  public int getD() {
    return d;
  }

  public int getDLow() {
    return dLow;
  }

  public int getDHigh() {
    return dHigh;
  }

  public int getDLazy() {
    return dLazy;
  }

  public Duration getFanoutTTL() {
    return fanoutTTL;
  }

  public int getAdvertise() {
    return advertise;
  }

  public int getHistory() {
    return history;
  }

  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public Duration getSeenTTL() {
    return seenTTL;
  }
}
