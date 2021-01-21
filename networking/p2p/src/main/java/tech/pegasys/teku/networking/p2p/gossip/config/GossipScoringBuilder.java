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

import static com.google.common.base.Preconditions.checkNotNull;

import io.libp2p.pubsub.gossip.GossipScoreParams;
import io.libp2p.pubsub.gossip.GossipTopicsScoreParams;
import io.libp2p.pubsub.gossip.builders.GossipPeerScoreParamsBuilder;
import io.libp2p.pubsub.gossip.builders.GossipScoreParamsBuilder;
import io.libp2p.pubsub.gossip.builders.GossipTopicScoreParamsBuilder;
import java.util.function.Consumer;

public class GossipScoringBuilder {
  private final GossipPeerScoreParamsBuilder peerScoreParamsBuilder =
      new GossipPeerScoreParamsBuilder();
  private final GossipTopicsScoringBuilder topicsScoringBuilder = new GossipTopicsScoringBuilder();
  private final GossipTopicScoreParamsBuilder defaultTopicScoreParamsBuilder =
      GossipTopicsScoringBuilder.singleTopicBuilder();
  private final GossipScoreParamsBuilder scoreParamsBuilder = new GossipScoreParamsBuilder();

  public GossipScoreParams build() {
    final GossipTopicsScoreParams topicParams =
        new GossipTopicsScoreParams(
            defaultTopicScoreParamsBuilder.build(), topicsScoringBuilder.build());

    return new GossipScoreParamsBuilder()
        .topicsScoreParams(topicParams)
        .peerScoreParams(peerScoreParamsBuilder.build())
        .build();
  }

  public GossipScoringBuilder peerScoring(final Consumer<GossipPeerScoreParamsBuilder> consumer) {
    consumer.accept(peerScoreParamsBuilder);
    return this;
  }

  public GossipScoringBuilder topicScoring(
      final String topic, final Consumer<GossipTopicScoreParamsBuilder> consumer) {
    topicsScoringBuilder.topicScoring(topic, consumer);
    return this;
  }

  public GossipTopicsScoringBuilder topicsScoringBuilder() {
    return topicsScoringBuilder;
  }

  public GossipScoringBuilder defaultTopicScoring(
      final String topic, final Consumer<GossipTopicScoreParamsBuilder> consumer) {
    consumer.accept(defaultTopicScoreParamsBuilder);
    return this;
  }

  public GossipScoringBuilder gossipThreshold(final Double gossipThreshold) {
    checkNotNull(gossipThreshold);
    scoreParamsBuilder.gossipThreshold(gossipThreshold);
    return this;
  }

  public GossipScoringBuilder publishThreshold(final Double publishThreshold) {
    checkNotNull(publishThreshold);
    scoreParamsBuilder.publishThreshold(publishThreshold);
    return this;
  }

  public GossipScoringBuilder graylistThreshold(final Double graylistThreshold) {
    checkNotNull(graylistThreshold);
    scoreParamsBuilder.graylistThreshold(graylistThreshold);
    return this;
  }

  public GossipScoringBuilder acceptPXThreshold(final Double acceptPXThreshold) {
    checkNotNull(acceptPXThreshold);
    scoreParamsBuilder.acceptPXThreshold(acceptPXThreshold);
    return this;
  }

  public GossipScoringBuilder opportunisticGraftThreshold(
      final Double opportunisticGraftThreshold) {
    checkNotNull(opportunisticGraftThreshold);
    scoreParamsBuilder.opportunisticGraftThreshold(opportunisticGraftThreshold);
    return this;
  }
}
