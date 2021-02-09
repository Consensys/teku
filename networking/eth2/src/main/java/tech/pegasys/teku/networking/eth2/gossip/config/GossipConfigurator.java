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

package tech.pegasys.teku.networking.eth2.gossip.config;

import java.time.Duration;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;
import tech.pegasys.teku.spec.constants.SpecConstants;

public interface GossipConfigurator {
  int GOSSIP_D = 8;
  int GOSSIP_D_LOW = 6;
  int GOSSIP_D_HIGH = 12;
  int GOSSIP_D_LAZY = 6;
  Duration GOSSIP_FANOUT_TTL = Duration.ofSeconds(60);
  int GOSSIP_ADVERTISE = 3;
  int GOSSIP_HISTORY = 6;
  Duration GOSSIP_HEARTBEAT_INTERVAL = Duration.ofMillis(700);
  Duration GOSSIP_SEEN_TTL = GOSSIP_HEARTBEAT_INTERVAL.multipliedBy(550);

  static GossipConfigurator scoringEnabled(SpecConstants specConstants) {
    return new GossipScoringConfigurator(specConstants);
  }

  static GossipConfigurator scoringDisabled() {
    return new GossipConfigurator() {

      @Override
      public void configureAllTopics(
          final GossipTopicsScoringConfig.Builder topicsConfigBuilder,
          final Eth2Context eth2Context) {}

      @Override
      public void configureDynamicTopics(
          final GossipTopicsScoringConfig.Builder topicsConfigBuilder,
          final Eth2Context eth2Context) {}
    };
  }

  /**
   * Configure gossip
   *
   * @param gossipConfigBuilder The builder to be configured
   * @param eth2Context Contextual information about the current chain
   */
  default void configure(
      final GossipConfig.Builder gossipConfigBuilder, final Eth2Context eth2Context) {
    gossipConfigBuilder
        .d(GOSSIP_D)
        .dLow(GOSSIP_D_LOW)
        .dHigh(GOSSIP_D_HIGH)
        .dLazy(GOSSIP_D_LAZY)
        .fanoutTTL(GOSSIP_FANOUT_TTL)
        .advertise(GOSSIP_ADVERTISE)
        .history(GOSSIP_HISTORY)
        .heartbeatInterval(GOSSIP_HEARTBEAT_INTERVAL)
        .seenTTL(GOSSIP_SEEN_TTL);
  }

  /**
   * Configure scoring for all topics
   *
   * @param topicsConfigBuilder The builder to be configured
   * @param eth2Context Contextual information about the current chain
   */
  void configureAllTopics(
      final GossipTopicsScoringConfig.Builder topicsConfigBuilder, final Eth2Context eth2Context);

  /**
   * Configure scoring for all topics
   *
   * @param eth2Context Contextual information about the current chain
   */
  default GossipTopicsScoringConfig configureAllTopics(final Eth2Context eth2Context) {
    final GossipTopicsScoringConfig.Builder builder = GossipTopicsScoringConfig.builder();
    configureAllTopics(builder, eth2Context);
    return builder.build();
  }

  /**
   * Configure scoring for dynamic topics that should be updated over time
   *
   * @param topicsConfigBuilder The builder to be configured
   * @param eth2Context Contextual information about the current chain
   */
  void configureDynamicTopics(
      final GossipTopicsScoringConfig.Builder topicsConfigBuilder, final Eth2Context eth2Context);

  /**
   * Configure scoring for dynamic topics that should be updated over time
   *
   * @param eth2Context Contextual information about the current chain
   */
  default GossipTopicsScoringConfig configureDynamicTopics(final Eth2Context eth2Context) {
    final GossipTopicsScoringConfig.Builder builder = GossipTopicsScoringConfig.builder();
    configureDynamicTopics(builder, eth2Context);
    return builder.build();
  }
}
