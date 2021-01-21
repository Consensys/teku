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

package tech.pegasys.teku.networking.p2p.gossip.config;

import io.libp2p.pubsub.gossip.GossipTopicScoreParams;
import io.libp2p.pubsub.gossip.builders.GossipTopicScoreParamsBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Builder to create a collection of topic params */
public class GossipTopicsScoringBuilder {
  private final Map<String, GossipTopicScoreParamsBuilder> topicScoringBuilders = new HashMap<>();

  public Map<String, GossipTopicScoreParams> build() {
    return topicScoringBuilders.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build()));
  }

  public static GossipTopicScoreParamsBuilder singleTopicBuilder() {
    return new GossipTopicScoreParamsBuilder();
  }

  public GossipTopicsScoringBuilder topicScoring(
      final String topic, final Consumer<GossipTopicScoreParamsBuilder> consumer) {
    GossipTopicScoreParamsBuilder builder =
        topicScoringBuilders.computeIfAbsent(topic, __ -> new GossipTopicScoreParamsBuilder());
    consumer.accept(builder);
    return this;
  }
}
