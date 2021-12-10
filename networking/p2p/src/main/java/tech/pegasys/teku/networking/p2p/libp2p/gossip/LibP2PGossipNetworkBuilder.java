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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import static tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetwork.NULL_SEQNO_GENERATOR;
import static tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetwork.STRICT_FIELDS_VALIDATOR;

import com.google.common.base.Preconditions;
import io.libp2p.core.pubsub.PubsubApi;
import io.libp2p.core.pubsub.PubsubApiKt;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.ValidationResult;
import io.libp2p.pubsub.FastIdSeenCache;
import io.libp2p.pubsub.MaxCountTopicSubscriptionFilter;
import io.libp2p.pubsub.PubsubProtocol;
import io.libp2p.pubsub.SeenCache;
import io.libp2p.pubsub.TTLSeenCache;
import io.libp2p.pubsub.TopicSubscriptionFilter;
import io.libp2p.pubsub.gossip.Gossip;
import io.libp2p.pubsub.gossip.GossipParams;
import io.libp2p.pubsub.gossip.GossipRouter;
import io.libp2p.pubsub.gossip.GossipScoreParams;
import io.netty.channel.ChannelHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.libp2p.config.LibP2PParamsFactory;

/**
 * CAUTION: this API is unstable and primarily intended for debugging and testing purposes this API
 * might be changed in any version in backward incompatible way
 */
public class LibP2PGossipNetworkBuilder {
  public static LibP2PGossipNetworkBuilder create() {
    return new LibP2PGossipNetworkBuilder();
  }

  protected MetricsSystem metricsSystem;
  protected GossipConfig gossipConfig;
  protected PreparedGossipMessageFactory defaultMessageFactory;
  protected GossipTopicFilter gossipTopicFilter;
  protected boolean logWireGossip;

  protected ChannelHandler debugGossipHandler = null;

  protected LibP2PGossipNetworkBuilder() {}

  public LibP2PGossipNetwork build() {
    GossipTopicHandlers topicHandlers = new GossipTopicHandlers();
    Gossip gossip =
        createGossip(
            gossipConfig, logWireGossip, defaultMessageFactory, gossipTopicFilter, topicHandlers);
    PubsubPublisherApi publisher = gossip.createPublisher(null, NULL_SEQNO_GENERATOR);

    return new LibP2PGossipNetwork(metricsSystem, gossip, publisher, topicHandlers);
  }

  protected Gossip createGossip(
      GossipConfig gossipConfig,
      boolean gossipLogsEnabled,
      PreparedGossipMessageFactory defaultMessageFactory,
      GossipTopicFilter gossipTopicFilter,
      GossipTopicHandlers topicHandlers) {
    final GossipParams gossipParams = LibP2PParamsFactory.createGossipParams(gossipConfig);
    final GossipScoreParams scoreParams =
        LibP2PParamsFactory.createGossipScoreParams(gossipConfig.getScoringConfig());

    final TopicSubscriptionFilter subscriptionFilter =
        new MaxCountTopicSubscriptionFilter(100, 200, gossipTopicFilter::isRelevantTopic);
    GossipRouter router =
        new GossipRouter(
            gossipParams, scoreParams, PubsubProtocol.Gossip_V_1_1, subscriptionFilter) {

          final SeenCache<Optional<ValidationResult>> seenCache =
              new TTLSeenCache<>(
                  new FastIdSeenCache<>(
                      msg ->
                          Bytes.wrap(
                              Hash.sha256(msg.getProtobufMessage().getData().toByteArray()))),
                  gossipParams.getSeenTTL(),
                  getCurTimeMillis());

          @NotNull
          @Override
          protected SeenCache<Optional<ValidationResult>> getSeenMessages() {
            return seenCache;
          }
        };

    router.setMessageFactory(
        msg -> {
          Preconditions.checkArgument(
              msg.getTopicIDsCount() == 1,
              "Unexpected number of topics for a single message: " + msg.getTopicIDsCount());
          String topic = msg.getTopicIDs(0);
          Bytes payload = Bytes.wrap(msg.getData().toByteArray());

          PreparedGossipMessage preparedMessage =
              topicHandlers
                  .getHandlerForTopic(topic)
                  .map(handler -> handler.prepareMessage(payload))
                  .orElse(defaultMessageFactory.create(topic, payload));

          return new PreparedPubsubMessage(msg, preparedMessage);
        });
    router.setMessageValidator(STRICT_FIELDS_VALIDATOR);

    if (gossipLogsEnabled) {
      if (debugGossipHandler != null) {
        throw new IllegalStateException(
            "Adding more than 1 gossip debug handlers is not implemented yet");
      }
      debugGossipHandler = new LoggingHandler("wire.gossip", LogLevel.DEBUG);
    }
    PubsubApi pubsubApi = PubsubApiKt.createPubsubApi(router);

    return new Gossip(router, pubsubApi, debugGossipHandler);
  }

  public LibP2PGossipNetworkBuilder metricsSystem(MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public LibP2PGossipNetworkBuilder gossipConfig(GossipConfig gossipConfig) {
    this.gossipConfig = gossipConfig;
    return this;
  }

  public LibP2PGossipNetworkBuilder defaultMessageFactory(
      PreparedGossipMessageFactory defaultMessageFactory) {
    this.defaultMessageFactory = defaultMessageFactory;
    return this;
  }

  public LibP2PGossipNetworkBuilder gossipTopicFilter(GossipTopicFilter gossipTopicFilter) {
    this.gossipTopicFilter = gossipTopicFilter;
    return this;
  }

  public LibP2PGossipNetworkBuilder logWireGossip(boolean logWireGossip) {
    this.logWireGossip = logWireGossip;
    return this;
  }

  public LibP2PGossipNetworkBuilder debugGossipHandler(ChannelHandler debugGossipHandler) {
    this.debugGossipHandler = debugGossipHandler;
    return this;
  }
}
