/*
 * Copyright Consensys Software Inc., 2025
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

import io.libp2p.core.PeerId;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.PubsubSubscription;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.pubsub.PubsubRouterMessageValidator;
import io.libp2p.pubsub.gossip.Gossip;
import io.libp2p.pubsub.gossip.GossipTopicScoreParams;
import io.netty.buffer.Unpooled;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import kotlin.jvm.functions.Function0;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.libp2p.config.LibP2PParamsFactory;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class LibP2PGossipNetwork implements GossipNetwork {

  private static final Logger LOG = LogManager.getLogger();

  static final PubsubRouterMessageValidator STRICT_FIELDS_VALIDATOR = new GossipWireValidator();
  static final Function0<Long> NULL_SEQNO_GENERATOR = () -> null;

  private final MetricsSystem metricsSystem;
  private final Gossip gossip;
  private final PubsubPublisherApi publisher;
  private final GossipTopicHandlers topicHandlers;
  private final GossipMeshInfo gossipMeshInfo;
  private final LabelledSuppliedMetric meshSizeGauge;
  private final LabelledSuppliedMetric meshPeersJoinedGauge;
  private final LabelledSuppliedMetric meshPeersLeftGauge;

  @SuppressWarnings("UnusedVariable") // Сохранено для будущего использования
  private final AsyncRunner asyncRunner;

  // Track gauges by topic to allow cleanup
  private final Map<String, AtomicReference<Double>> meshSizeGaugeValues = new HashMap<>();
  private final Map<String, AtomicReference<Double>> meshPeersJoinedGaugeValues = new HashMap<>();
  private final Map<String, AtomicReference<Double>> meshPeersLeftGaugeValues = new HashMap<>();

  public LibP2PGossipNetwork(
      final MetricsSystem metricsSystem,
      final Gossip gossip,
      final PubsubPublisherApi publisher,
      final GossipTopicHandlers topicHandlers,
      final AsyncRunner asyncRunner) {
    this.metricsSystem = metricsSystem;
    this.gossip = gossip;
    this.publisher = publisher;
    this.topicHandlers = topicHandlers;
    this.asyncRunner = asyncRunner;
    this.gossipMeshInfo = new GossipMeshInfo(gossip);

    // Create gauges for mesh metrics
    this.meshSizeGauge =
        metricsSystem.createLabelledSuppliedGauge(
            TekuMetricCategory.LIBP2P,
            "gossip_mesh_peers_current",
            "Current number of peers in mesh for each topic",
            "topic");

    this.meshPeersJoinedGauge =
        metricsSystem.createLabelledSuppliedGauge(
            TekuMetricCategory.LIBP2P,
            "gossip_mesh_peers_joined",
            "Number of peers that have joined the mesh since the last sampling period",
            "topic");

    this.meshPeersLeftGauge =
        metricsSystem.createLabelledSuppliedGauge(
            TekuMetricCategory.LIBP2P,
            "gossip_mesh_peers_left",
            "Number of peers that have left the mesh since the last sampling period",
            "topic");
  }

  /** Updates mesh metrics by fetching the latest mesh information. */
  @SuppressWarnings("FutureReturnValueIgnored")
  public void updateMeshMetrics() {
    gossipMeshInfo
        .getMeshMetrics()
        .thenAccept(
            meshMetricsByTopic -> {
              // Update metrics for all topics
              meshMetricsByTopic.forEach(
                  (topic, metrics) -> {
                    // Update mesh size gauge
                    final AtomicReference<Double> meshSizeValue =
                        meshSizeGaugeValues.computeIfAbsent(topic, t -> new AtomicReference<>(0.0));
                    meshSizeValue.set((double) metrics.getMeshSize());
                    meshSizeGauge.labels(() -> meshSizeValue.get(), topic);

                    // Update peers joined gauge
                    final AtomicReference<Double> peersJoinedValue =
                        meshPeersJoinedGaugeValues.computeIfAbsent(
                            topic, t -> new AtomicReference<>(0.0));
                    peersJoinedValue.set((double) metrics.getPeersJoined());
                    meshPeersJoinedGauge.labels(() -> peersJoinedValue.get(), topic);

                    // Update peers left gauge
                    final AtomicReference<Double> peersLeftValue =
                        meshPeersLeftGaugeValues.computeIfAbsent(
                            topic, t -> new AtomicReference<>(0.0));
                    peersLeftValue.set((double) metrics.getPeersLeft());
                    meshPeersLeftGauge.labels(() -> peersLeftValue.get(), topic);
                  });

              // Check for topics that no longer exist and remove their gauges
              // (This would require more complex logic to properly implement in Prometheus)
            })
        .exceptionally(
            error -> {
              LOG.debug("Failed to update mesh metrics", error);
              return null;
            });
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

    // Update metrics when a new topic is subscribed
    updateMeshMetrics();

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

  @Override
  public void updateGossipTopicScoring(final GossipTopicsScoringConfig config) {
    if (config.isEmpty()) {
      return;
    }

    final Map<String, GossipTopicScoreParams> params =
        config.getTopicConfigs().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> LibP2PParamsFactory.createTopicScoreParams(e.getValue())));
    gossip.updateTopicScoreParams(params);
  }

  public Gossip getGossip() {
    return gossip;
  }

  public GossipMeshInfo getGossipMeshInfo() {
    return gossipMeshInfo;
  }
}
