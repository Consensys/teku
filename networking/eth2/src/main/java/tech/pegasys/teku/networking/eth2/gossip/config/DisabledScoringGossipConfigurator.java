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

import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;

class DisabledScoringGossipConfigurator implements GossipConfigurator {

  @Override
  public void configure(final GossipConfig.Builder gossipParams) {
    gossipParams
        .resetDefaults()
        .scoring(
            b ->
                b.peerScoring(
                        p ->
                            p.appSpecificWeight(0.0)
                                .behaviourPenaltyWeight(0.0)
                                .ipColocationFactorWeight(0.0))
                    .defaultTopicScoring(dt -> dt.topicWeight(0.0))
                    .topicScoring(GossipTopicsScoringConfig.Builder::clear));
  }

  @Override
  public void updateState(final Eth2State eth2State) {
    // No-op
  }
}
