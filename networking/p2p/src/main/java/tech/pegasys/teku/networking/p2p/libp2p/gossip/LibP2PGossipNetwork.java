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
import io.libp2p.pubsub.PubsubRouterMessageValidator;
import io.libp2p.pubsub.SeenCache;
import io.libp2p.pubsub.TTLSeenCache;
import io.libp2p.pubsub.gossip.Gossip;
import io.libp2p.pubsub.gossip.GossipParams;
import io.libp2p.pubsub.gossip.GossipRouter;
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
  private Gossip gossip;
  private PubsubPublisherApi publisher;
  private final Map<String, TopicHandler> topicHandlerMap = new ConcurrentHashMap<>();
  private final PreparedGossipMessageFactory defaultMessageFactory;

  public static LibP2PGossipNetwork create(
      MetricsSystem metricsSystem,
      GossipConfig gossipConfig,
      PreparedGossipMessageFactory defaultMessageFactory,
      boolean logWireGossip) {

    LibP2PGossipNetwork gossipNetwork =
        new LibP2PGossipNetwork(metricsSystem, defaultMessageFactory);
    gossipNetwork.initGossip(gossipConfig, logWireGossip);
    return gossipNetwork;
  }

  private LibP2PGossipNetwork(
      MetricsSystem metricsSystem, PreparedGossipMessageFactory defaultMessageFactory) {
    this.metricsSystem = metricsSystem;
    this.defaultMessageFactory = defaultMessageFactory;
  }

  private void initGossip(GossipConfig gossipConfig, boolean logWireGossip) {
    this.gossip = createGossip(gossipConfig, logWireGossip);
    this.publisher = gossip.createPublisher(null, NULL_SEQNO_GENERATOR);
  }

  @Override
  public SafeFuture<?> gossip(final String topic, final Bytes data) {
    return SafeFuture.of(
        publisher.publish(Unpooled.wrappedBuffer(data.toArrayUnsafe()), new Topic(topic)));
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    LOG.trace("Subscribe to topic: {}", topic);
    topicHandlerMap.put(topic, topicHandler);
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

  private Optional<TopicHandler> getSubscribedHandler(String topic) {
    return Optional.ofNullable(topicHandlerMap.get(topic));
  }

  public PreparedGossipMessage prepareMessage(String topic, Bytes payload) {
    Optional<TopicHandler> topicHandler = getSubscribedHandler(topic);
    return topicHandler
        .map(handler -> handler.prepareMessage(payload))
        .orElse(prepareNonSubscribedMessage(topic, payload));
  }

  private PreparedGossipMessage prepareNonSubscribedMessage(String topic, Bytes payload) {
    return defaultMessageFactory.create(topic, payload);
  }

  private Gossip createGossip(GossipConfig gossipConfig, boolean gossipLogsEnabled) {
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
            .build();

    GossipRouter router =
        new GossipRouter(gossipParams) {

          final SeenCache<Optional<ValidationResult>> seenCache =
              new TTLSeenCache<>(
                  new FastIdSeenCache<>(msg -> msg.getProtobufMessage().getData()),
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
          PreparedGossipMessage preparedMessage =
              prepareMessage(topic, Bytes.wrap(msg.getData().toByteArray()));
          return new PreparedPubsubMessage(msg, preparedMessage);
        });
    router.setMessageValidator(STRICT_FIELDS_VALIDATOR);

    ChannelHandler debugHandler =
        gossipLogsEnabled ? new LoggingHandler("wire.gossip", LogLevel.DEBUG) : null;
    PubsubApi pubsubApi = PubsubApiKt.createPubsubApi(router);

    return new Gossip(router, pubsubApi, debugHandler);
  }

  public Gossip getGossip() {
    return gossip;
  }
}
