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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import kotlin.jvm.functions.Function0;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
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
  private static final long MESH_METRICS_UPDATE_INTERVAL_SECONDS = 30;

  static final PubsubRouterMessageValidator STRICT_FIELDS_VALIDATOR = new GossipWireValidator();
  static final Function0<Long> NULL_SEQNO_GENERATOR = () -> null;

  private final MetricsSystem metricsSystem;
  private final Gossip gossip;
  private final PubsubPublisherApi publisher;
  private final GossipTopicHandlers topicHandlers;
  private final LabelledSuppliedMetric meshPeersGauge;
  private final LabelledMetric<Counter> meshPeersJoinedCounter;
  private final LabelledMetric<Counter> meshPeersLeftCounter;
  private final ScheduledExecutorService meshMetricsExecutor;
  private final Map<String, Set<PeerId>> previousMeshPeers = new ConcurrentHashMap<>();

  public LibP2PGossipNetwork(
      final MetricsSystem metricsSystem,
      final Gossip gossip,
      final PubsubPublisherApi publisher,
      final GossipTopicHandlers topicHandlers) {
    this.metricsSystem = metricsSystem;
    this.gossip = gossip;
    this.publisher = publisher;
    this.topicHandlers = topicHandlers;
    
    // Create metrics for p2p mesh
    this.meshPeersGauge = metricsSystem.createLabelledSuppliedGauge(
        TekuMetricCategory.LIBP2P,
        "mesh_peers_count",
        "Number of peers in p2p mesh for each topic",
        "topic");
    
    this.meshPeersJoinedCounter = metricsSystem.createLabelledCounter(
        TekuMetricCategory.LIBP2P,
        "mesh_peers_joined_total",
        "Total number of peers that joined the p2p mesh since last check",
        "topic");
        
    this.meshPeersLeftCounter = metricsSystem.createLabelledCounter(
        TekuMetricCategory.LIBP2P,
        "mesh_peers_left_total",
        "Total number of peers that left the p2p mesh since last check",
        "topic");
    
    // Create and start scheduler for metric updates
    this.meshMetricsExecutor = Executors.newSingleThreadScheduledExecutor(
        r -> { 
          Thread t = new Thread(r, "mesh-metrics-updater");
          t.setDaemon(true);
          return t;
        });
        
    // Start regular updates of p2p mesh metrics
    registerMeshPeersMetrics();
    startMeshMetricsUpdater();
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
    
    // Update metrics after subscribing to the topic
    registerMeshPeersMetric(topic);
    
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
  
  /**
   * Returns a thread-safe copy of the current mesh state for metrics.
   * 
   * @return Map of topic to set of node IDs in that topic's mesh
   */
  public Map<String, Set<NodeId>> getMeshPeers() {
    Map<String, Set<NodeId>> result = new ConcurrentHashMap<>();
    try {
      Map<String, Set<PeerId>> meshMap = gossip.getRouter().getMesh();
      meshMap.forEach((topic, peerSet) -> {
        Set<NodeId> nodeIdSet = peerSet.stream()
            .map(LibP2PNodeId::new)
            .collect(Collectors.toSet());
        result.put(topic, nodeIdSet);
      });
    } catch (Exception e) {
      LOG.warn("Failed to retrieve mesh peer information", e);
    }
    return result;
  }
  
  /**
   * Register metrics suppliers for all current mesh topics.
   */
  public void registerMeshPeersMetrics() {
    // Get current topics
    try {
      Map<String, Set<PeerId>> meshMap = gossip.getRouter().getMesh();
      meshMap.keySet().forEach(this::registerMeshPeersMetric);
    } catch (Exception e) {
      LOG.warn("Failed to register mesh peer metrics", e);
    }
  }
  
  /**
   * Register a metric supplier for a specific mesh topic.
   *
   * @param topic The topic to register a metric for
   */
  private void registerMeshPeersMetric(final String topic) {
    meshPeersGauge.labels(
        () -> {
          try {
            Map<String, Set<PeerId>> meshMap = gossip.getRouter().getMesh();
            Set<PeerId> peers = meshMap.getOrDefault(topic, Set.of());
            return peers.size();
          } catch (Exception e) {
            LOG.debug("Failed to get mesh peer count for topic {}", topic, e);
            return 0.0;
          }
        },
        topic);
  }
  
  /**
   * Starts the scheduled task that updates mesh metrics regularly.
   */
  private void startMeshMetricsUpdater() {
    meshMetricsExecutor.scheduleAtFixedRate(
        this::updateMeshMetrics, 
        MESH_METRICS_UPDATE_INTERVAL_SECONDS, 
        MESH_METRICS_UPDATE_INTERVAL_SECONDS, 
        TimeUnit.SECONDS);
  }
  
  /**
   * Update mesh metrics by comparing current mesh state with previous state.
   */
  public void updateMeshMetrics() {
    try {
      Map<String, Set<PeerId>> currentMeshPeers = gossip.getRouter().getMesh();
      
      // Process existing topics
      for (String topic : currentMeshPeers.keySet()) {
        Set<PeerId> currentPeers = currentMeshPeers.getOrDefault(topic, Set.of());
        Set<PeerId> previousPeers = previousMeshPeers.getOrDefault(topic, Set.of());
        
        // Register metric if this is a new topic
        if (!previousMeshPeers.containsKey(topic)) {
          registerMeshPeersMetric(topic);
        }
        
        // Calculate peers that joined
        Set<PeerId> joinedPeers = new HashSet<>(currentPeers);
        joinedPeers.removeAll(previousPeers);
        
        // Calculate peers that left
        Set<PeerId> leftPeers = new HashSet<>(previousPeers);
        leftPeers.removeAll(currentPeers);
        
        // Update counters
        if (!joinedPeers.isEmpty()) {
          Counter joinedCounter = meshPeersJoinedCounter.labels(topic);
          joinedCounter.inc(joinedPeers.size());
        }
        
        if (!leftPeers.isEmpty()) {
          Counter leftCounter = meshPeersLeftCounter.labels(topic);
          leftCounter.inc(leftPeers.size());
        }
      }
      
      // Check for topics that disappeared
      Set<String> removedTopics = new HashSet<>(previousMeshPeers.keySet());
      removedTopics.removeAll(currentMeshPeers.keySet());
      
      for (String removedTopic : removedTopics) {
        Set<PeerId> leftPeers = previousMeshPeers.get(removedTopic);
        if (leftPeers != null && !leftPeers.isEmpty()) {
          Counter leftCounter = meshPeersLeftCounter.labels(removedTopic);
          leftCounter.inc(leftPeers.size());
        }
      }
      
      // Update stored state
      previousMeshPeers.clear();
      previousMeshPeers.putAll(currentMeshPeers);
      
    } catch (Exception e) {
      LOG.warn("Failed to update mesh metrics", e);
    }
  }
  
  /**
   * Stops the metrics updater when the network is closed.
   */
  public void stop() {
    meshMetricsExecutor.shutdownNow();
  }
}
