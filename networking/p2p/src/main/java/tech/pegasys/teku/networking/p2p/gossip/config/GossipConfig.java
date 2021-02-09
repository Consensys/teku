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

package tech.pegasys.teku.networking.p2p.gossip.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Gossip options
 * https://github.com/ethereum/eth2.0-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#the-gossip-domain-gossipsub
 */
public class GossipConfig {
  private final int d;
  private final int dLow;
  private final int dHigh;
  private final int dLazy;
  private final Duration fanoutTTL;
  private final int advertise;
  private final int history;
  private final Duration heartbeatInterval;
  private final Duration seenTTL;
  private final GossipScoringConfig scoringConfig;

  private GossipConfig(
      int d,
      int dLow,
      int dHigh,
      int dLazy,
      Duration fanoutTTL,
      int advertise,
      int history,
      Duration heartbeatInterval,
      Duration seenTTL,
      final GossipScoringConfig scoringConfig) {
    this.d = d;
    this.dLow = dLow;
    this.dHigh = dHigh;
    this.dLazy = dLazy;
    this.fanoutTTL = fanoutTTL;
    this.advertise = advertise;
    this.history = history;
    this.heartbeatInterval = heartbeatInterval;
    this.seenTTL = seenTTL;
    this.scoringConfig = scoringConfig;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static GossipConfig createDefault() {
    return builder().build();
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

  public GossipScoringConfig getScoringConfig() {
    return scoringConfig;
  }

  public static class Builder {
    private final GossipScoringConfig.Builder scoringConfigBuilder = GossipScoringConfig.builder();

    private Integer d;
    private Integer dLow;
    private Integer dHigh;
    private Integer dLazy;
    private Duration fanoutTTL;
    private Integer advertise;
    private Integer history;
    private Duration heartbeatInterval;
    private Duration seenTTL;

    private Builder() {}

    public GossipConfig build() {
      validate();
      return new GossipConfig(
          d,
          dLow,
          dHigh,
          dLazy,
          fanoutTTL,
          advertise,
          history,
          heartbeatInterval,
          seenTTL,
          scoringConfigBuilder.build());
    }

    private void validate() {
      checkNotNull(d, "Gossip parameter 'd' must be set");
      checkNotNull(dLow, "Gossip parameter 'dLow' must be set");
      checkNotNull(dHigh, "Gossip parameter 'dHigh' must be set");
      checkNotNull(dLazy, "Gossip parameter 'dLazy' must be set");
      checkNotNull(fanoutTTL, "Gossip parameter 'fanoutTTL' must be set");
      checkNotNull(advertise, "Gossip parameter 'advertise' must be set");
      checkNotNull(history, "Gossip parameter 'history' must be set");
      checkNotNull(heartbeatInterval, "Gossip parameter 'heartbeatInterval' must be set");
      checkNotNull(seenTTL, "Gossip parameter 'seenTTL' must be set");
    }

    public Builder scoring(final Consumer<GossipScoringConfig.Builder> consumer) {
      consumer.accept(scoringConfigBuilder);
      return this;
    }

    public Builder d(final Integer d) {
      checkNotNull(d);
      this.d = d;
      return this;
    }

    public Builder dLow(final Integer dLow) {
      checkNotNull(dLow);
      this.dLow = dLow;
      return this;
    }

    public Builder dHigh(final Integer dHigh) {
      checkNotNull(dHigh);
      this.dHigh = dHigh;
      return this;
    }

    public Builder dLazy(final Integer dLazy) {
      checkNotNull(dLazy);
      this.dLazy = dLazy;
      return this;
    }

    public Builder fanoutTTL(final Duration fanoutTTL) {
      checkNotNull(fanoutTTL);
      this.fanoutTTL = fanoutTTL;
      return this;
    }

    public Builder advertise(final Integer advertise) {
      checkNotNull(advertise);
      this.advertise = advertise;
      return this;
    }

    public Builder history(final Integer history) {
      checkNotNull(history);
      this.history = history;
      return this;
    }

    public Builder heartbeatInterval(final Duration heartbeatInterval) {
      checkNotNull(heartbeatInterval);
      this.heartbeatInterval = heartbeatInterval;
      return this;
    }

    public Builder seenTTL(final Duration seenTTL) {
      checkNotNull(seenTTL);
      this.seenTTL = seenTTL;
      return this;
    }
  }
}
