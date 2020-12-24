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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import com.google.common.base.Preconditions;
import io.libp2p.core.PeerId;
import io.libp2p.core.pubsub.PubsubApi;
import io.libp2p.core.pubsub.PubsubApiKt;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.PubsubSubscription;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.core.pubsub.ValidationResult;
import io.libp2p.pubsub.FastIdSeenCache;
import io.libp2p.pubsub.MaxCountTopicSubscriptionFilter;
import io.libp2p.pubsub.PubsubProtocol;
import io.libp2p.pubsub.PubsubRouterMessageValidator;
import io.libp2p.pubsub.SeenCache;
import io.libp2p.pubsub.TTLSeenCache;
import io.libp2p.pubsub.TopicSubscriptionFilter;
import io.libp2p.pubsub.gossip.Gossip;
import io.libp2p.pubsub.gossip.GossipParams;
import io.libp2p.pubsub.gossip.GossipRouter;
import io.libp2p.pubsub.gossip.GossipScoreParams;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.jvm.functions.Function0;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.network.GossipConfig;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class LibP2PGossipNetwork implements GossipNetwork {

  private static final Logger LOG = LogManager.getLogger();

  private static final PubsubRouterMessageValidator STRICT_FIELDS_VALIDATOR =
      new GossipWireValidator();
  private static final Function0<Long> NULL_SEQNO_GENERATOR = () -> null;

  private final MetricsSystem metricsSystem;
  private final Gossip gossip;
  private final PubsubPublisherApi publisher;
  private final TopicHandlers topicHandlers;

  public static LibP2PGossipNetwork create(
      MetricsSystem metricsSystem,
      GossipConfig gossipConfig,
      PreparedGossipMessageFactory defaultMessageFactory,
      GossipTopicFilter gossipTopicFilter,
      boolean logWireGossip) {

    TopicHandlers topicHandlers = new TopicHandlers();
    Gossip gossip =
        createGossip(
            gossipConfig, logWireGossip, defaultMessageFactory, gossipTopicFilter, topicHandlers);
    PubsubPublisherApi publisher = gossip.createPublisher(null, NULL_SEQNO_GENERATOR);

    return new LibP2PGossipNetwork(metricsSystem, gossip, publisher, topicHandlers);
  }

  private static Gossip createGossip(
      GossipConfig gossipConfig,
      boolean gossipLogsEnabled,
      PreparedGossipMessageFactory defaultMessageFactory,
      GossipTopicFilter gossipTopicFilter,
      TopicHandlers topicHandlers) {
    GossipParams gossipParams =
        GossipParams.builder()
            .D(gossipConfig.getD())
            .DLow(gossipConfig.getDLow())
            .DHigh(gossipConfig.getDHigh())
            .DLazy(gossipConfig.getDLazy())
            .fanoutTTL(gossipConfig.getFanoutTTL())
            .gossipSize(gossipConfig.getAdvertise())
            .gossipHistoryLength(gossipConfig.getHistory())
            .heartbeatInterval(gossipConfig.getHeartbeatInterval())
            .floodPublish(true)
            .seenTTL(gossipConfig.getSeenTTL())
            .maxPublishedMessages(1000)
            .maxTopicsPerPublishedMessage(1)
            .maxSubscriptions(200)
            .maxGraftMessages(200)
            .maxPruneMessages(200)
            .maxPeersPerPruneMessage(1000)
            .maxIHaveLength(5000)
            .maxIWantMessageIds(5000)
            .build();

    final TopicSubscriptionFilter subscriptionFilter =
        new MaxCountTopicSubscriptionFilter(100, 200, gossipTopicFilter::isRelevantTopic);
    GossipRouter router =
        new GossipRouter(
            gossipParams,
            new GossipScoreParams(),
            PubsubProtocol.Gossip_V_1_1,
            subscriptionFilter) {

          final SeenCache<Optional<ValidationResult>> seenCache =
              new TTLSeenCache<>(
                  new FastIdSeenCache<>(
                      msg ->
                          Bytes.wrap(
                              Hash.sha2_256(msg.getProtobufMessage().getData().toByteArray()))),
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

    ChannelHandler debugHandler =
        gossipLogsEnabled ? new LoggingHandler("wire.gossip", LogLevel.DEBUG) : null;
    PubsubApi pubsubApi = PubsubApiKt.createPubsubApi(router);

    return new Gossip(router, pubsubApi, debugHandler);
  }

  public LibP2PGossipNetwork(
      MetricsSystem metricsSystem,
      Gossip gossip,
      PubsubPublisherApi publisher,
      TopicHandlers topicHandlers) {
    this.metricsSystem = metricsSystem;
    this.gossip = gossip;
    this.publisher = publisher;
    this.topicHandlers = topicHandlers;
  }

  @Override
  public SafeFuture<?> gossip(final String topic, final Bytes data) {
    return SafeFuture.of(
        publisher.publish(Unpooled.wrappedBuffer(data.toArrayUnsafe()), new Topic(topic)));
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    LOG.trace("Subscribe to topic: {}", topic);
    topicHandlers.add(topic, topicHandler);
    final Topic libP2PTopic = new Topic(topic);
    final GossipHandler gossipHandler =
        new GossipHandler(metricsSystem, libP2PTopic, publisher, topicHandler);
    PubsubSubscription subscription = gossip.subscribe(gossipHandler, libP2PTopic);
    return new LibP2PTopicChannel(gossipHandler, subscription);
  }

  @Override
  public Map<String, Collection<NodeId>> getSubscribersByTopic() {
    Map<PeerId, Set<Topic>> peerTopics = gossip.getPeerTopics().join();
    final Map<String, Collection<NodeId>> result = new HashMap<>();
    for (Map.Entry<PeerId, Set<Topic>> peerTopic : peerTopics.entrySet()) {
      final LibP2PNodeId nodeId = new LibP2PNodeId(peerTopic.getKey());
      peerTopic
          .getValue()
          .forEach(
              topic -> result.computeIfAbsent(topic.getTopic(), __ -> new HashSet<>()).add(nodeId));
    }
    return result;
  }

  public Gossip getGossip() {
    return gossip;
  }

  private static class TopicHandlers {

    private final Map<String, TopicHandler> topicToHandlerMap = new ConcurrentHashMap<>();

    public void add(String topic, TopicHandler handler) {
      topicToHandlerMap.put(topic, handler);
    }

    public Optional<TopicHandler> getHandlerForTopic(String topic) {
      return Optional.ofNullable(topicToHandlerMap.get(topic));
    }
  }
}
