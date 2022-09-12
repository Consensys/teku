/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.networking.p2p.libp2p.config;

import io.libp2p.core.PeerId;
import io.libp2p.pubsub.gossip.GossipParams;
import io.libp2p.pubsub.gossip.GossipPeerScoreParams;
import io.libp2p.pubsub.gossip.GossipScoreParams;
import io.libp2p.pubsub.gossip.GossipTopicScoreParams;
import io.libp2p.pubsub.gossip.GossipTopicsScoreParams;
import io.libp2p.pubsub.gossip.builders.GossipParamsBuilder;
import io.libp2p.pubsub.gossip.builders.GossipPeerScoreParamsBuilder;
import java.util.Map;
import java.util.stream.Collectors;
import kotlin.jvm.functions.Function1;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipPeerScoringConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipScoringConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicScoringConfig;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;

public class LibP2PParamsFactory {

  public static final int MAX_SUBSCRIPTIONS_PER_MESSAGE = 200;
  public static final int MAX_COMPRESSED_GOSSIP_SIZE = 10 * (1 << 20);

  public static GossipParams createGossipParams(final GossipConfig gossipConfig) {
    final GossipParamsBuilder builder = GossipParams.builder();
    addGossipParamsDValues(gossipConfig, builder);
    addGossipParamsMiscValues(gossipConfig, builder);
    addGossipParamsMaxValues(builder);
    return builder.build();
  }

  private static void addGossipParamsMiscValues(
      final GossipConfig gossipConfig, final GossipParamsBuilder builder) {
    builder
        .fanoutTTL(gossipConfig.getFanoutTTL())
        .gossipSize(gossipConfig.getAdvertise())
        .gossipHistoryLength(gossipConfig.getHistory())
        .heartbeatInterval(gossipConfig.getHeartbeatInterval())
        .floodPublish(true)
        .seenTTL(gossipConfig.getSeenTTL());
  }

  private static void addGossipParamsDValues(
      final GossipConfig gossipConfig, final GossipParamsBuilder builder) {
    builder
        .D(gossipConfig.getD())
        .DLow(gossipConfig.getDLow())
        .DHigh(gossipConfig.getDHigh())
        .DLazy(gossipConfig.getDLazy())
        // Calculate dScore and dOut based on other params
        .DScore(gossipConfig.getD() * 2 / 3)
        .DOut(Math.min(gossipConfig.getD() / 2, Math.max(0, gossipConfig.getDLow() - 1)));
  }

  private static void addGossipParamsMaxValues(final GossipParamsBuilder builder) {
    builder
        .maxGossipMessageSize(MAX_COMPRESSED_GOSSIP_SIZE)
        .maxPublishedMessages(1000)
        .maxTopicsPerPublishedMessage(1)
        .maxSubscriptions(MAX_SUBSCRIPTIONS_PER_MESSAGE)
        .maxGraftMessages(200)
        .maxPruneMessages(200)
        .maxPeersPerPruneMessage(1000)
        .maxIHaveLength(5000)
        .maxIWantMessageIds(5000);
  }

  public static GossipScoreParams createGossipScoreParams(final GossipScoringConfig config) {
    return GossipScoreParams.builder()
        .peerScoreParams(createPeerScoreParams(config.getPeerScoringConfig()))
        .topicsScoreParams(createTopicsScoreParams(config))
        .gossipThreshold(config.getGossipThreshold())
        .publishThreshold(config.getPublishThreshold())
        .graylistThreshold(config.getGraylistThreshold())
        .acceptPXThreshold(config.getAcceptPXThreshold())
        .opportunisticGraftThreshold(config.getOpportunisticGraftThreshold())
        .build();
  }

  public static GossipPeerScoreParams createPeerScoreParams(final GossipPeerScoringConfig config) {
    final GossipPeerScoreParamsBuilder builder =
        GossipPeerScoreParams.builder()
            .topicScoreCap(config.getTopicScoreCap())
            .appSpecificWeight(config.getAppSpecificWeight())
            .ipColocationFactorWeight(config.getIpColocationFactorWeight())
            .ipColocationFactorThreshold(config.getIpColocationFactorThreshold())
            .behaviourPenaltyWeight(config.getBehaviourPenaltyWeight())
            .behaviourPenaltyDecay(config.getBehaviourPenaltyDecay())
            .behaviourPenaltyThreshold(config.getBehaviourPenaltyThreshold())
            .decayInterval(config.getDecayInterval())
            .decayToZero(config.getDecayToZero())
            .retainScore(config.getRetainScore());

    // Configure optional params
    config
        .getAppSpecificScorer()
        .ifPresent(
            scorer -> {
              final Function1<? super PeerId, Double> appSpecificScore =
                  peerId -> scorer.scorePeer(new LibP2PNodeId(peerId));
              builder.appSpecificScore(appSpecificScore);
            });

    config
        .getDirectPeerManager()
        .ifPresent(
            mgr -> {
              final Function1<? super PeerId, Boolean> isDirectPeer =
                  peerId -> mgr.isDirectPeer(new LibP2PNodeId(peerId));
              builder.isDirect(isDirectPeer);
            });

    config
        .getWhitelistManager()
        .ifPresent(
            mgr -> {
              // Ip whitelisting
              final Function1<? super String, Boolean> isIpWhitelisted = mgr::isWhitelisted;
              builder.ipWhitelisted(isIpWhitelisted);
            });

    return builder.build();
  }

  public static GossipTopicsScoreParams createTopicsScoreParams(final GossipScoringConfig config) {
    final GossipTopicScoreParams defaultTopicParams =
        createTopicScoreParams(config.getDefaultTopicScoringConfig());
    final Map<String, GossipTopicScoreParams> topicParams =
        config.getTopicScoringConfig().entrySet().stream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, e -> createTopicScoreParams(e.getValue())));
    return new GossipTopicsScoreParams(defaultTopicParams, topicParams);
  }

  public static GossipTopicScoreParams createTopicScoreParams(
      final GossipTopicScoringConfig config) {
    return GossipTopicScoreParams.builder()
        .topicWeight(config.getTopicWeight())
        .timeInMeshWeight(config.getTimeInMeshWeight())
        .timeInMeshQuantum(config.getTimeInMeshQuantum())
        .timeInMeshCap(config.getTimeInMeshCap())
        .firstMessageDeliveriesWeight(config.getFirstMessageDeliveriesWeight())
        .firstMessageDeliveriesDecay(config.getFirstMessageDeliveriesDecay())
        .firstMessageDeliveriesCap(config.getFirstMessageDeliveriesCap())
        .meshMessageDeliveriesWeight(config.getMeshMessageDeliveriesWeight())
        .meshMessageDeliveriesDecay(config.getMeshMessageDeliveriesDecay())
        .meshMessageDeliveriesThreshold(config.getMeshMessageDeliveriesThreshold())
        .meshMessageDeliveriesCap(config.getMeshMessageDeliveriesCap())
        .meshMessageDeliveriesActivation(config.getMeshMessageDeliveriesActivation())
        .meshMessageDeliveryWindow(config.getMeshMessageDeliveryWindow())
        .meshFailurePenaltyWeight(config.getMeshFailurePenaltyWeight())
        .meshFailurePenaltyDecay(config.getMeshFailurePenaltyDecay())
        .invalidMessageDeliveriesWeight(config.getInvalidMessageDeliveriesWeight())
        .invalidMessageDeliveriesDecay(config.getInvalidMessageDeliveriesDecay())
        .build();
  }
}
