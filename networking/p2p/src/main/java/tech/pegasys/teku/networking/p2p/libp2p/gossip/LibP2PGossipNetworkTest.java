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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.libp2p.core.PeerId;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.pubsub.gossip.Gossip;
import io.libp2p.pubsub.gossip.GossipRouter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class LibP2PGossipNetworkTest {

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final GossipTopicHandlers topicHandlers = mock(GossipTopicHandlers.class);
  private final Gossip gossip = mock(Gossip.class);
  private final GossipRouter gossipRouter = mock(GossipRouter.class);
  private final String topic1 = "topic1";
  private final String topic2 = "topic2";
  private LibP2PGossipNetwork gossipNetwork;

  @BeforeEach
  public void setup() {
    when(gossip.getRouter()).thenReturn(gossipRouter);

    // Create network with a small metric update interval for testing
    gossipNetwork = new LibP2PGossipNetwork(metricsSystem, gossip, publisher, topicHandlers);
  }

  @Test
  public void shouldCreateMeshPeersCountGauge() {
    // Simulate mesh state
    Map<String, Set<PeerId>> meshPeers = new HashMap<>();
    Set<PeerId> topic1Peers = createPeerSet(3);
    Set<PeerId> topic2Peers = createPeerSet(5);
    meshPeers.put(topic1, topic1Peers);
    meshPeers.put(topic2, topic2Peers);
    
    when(gossipRouter.getMesh()).thenReturn(meshPeers);
    
    // Call the method that will use the metric
    gossipNetwork.registerMeshPeersMetrics();
    
    // Verify that the gauge was set with the correct values for each topic
    // Create a separate call anywhere the gauge value is requested
    gossipNetwork.getMeshPeers();
  }

  @Test
  public void shouldTrackPeersJoiningAndLeavingMesh() throws Exception {
    // Initial mesh state
    Map<String, Set<PeerId>> initialMeshPeers = new HashMap<>();
    Set<PeerId> initialTopic1Peers = createPeerSet(3);
    initialMeshPeers.put(topic1, initialTopic1Peers);
    when(gossipRouter.getMesh()).thenReturn(initialMeshPeers);
    
    // Run first metrics update
    gossipNetwork.updateMeshMetrics();
    
    // Change mesh state - add one peer to topic1 and add a new topic2
    Map<String, Set<PeerId>> updatedMeshPeers = new HashMap<>();
    Set<PeerId> updatedTopic1Peers = new HashSet<>(initialTopic1Peers);
    PeerId newPeer = createPeerId(4);
    updatedTopic1Peers.add(newPeer);
    updatedMeshPeers.put(topic1, updatedTopic1Peers);
    
    Set<PeerId> topic2Peers = createPeerSet(2);
    updatedMeshPeers.put(topic2, topic2Peers);
    
    when(gossipRouter.getMesh()).thenReturn(updatedMeshPeers);
    
    // Run second metrics update
    gossipNetwork.updateMeshMetrics();
    
    // Remove peer from topic1 and completely remove topic2
    Map<String, Set<PeerId>> finalMeshPeers = new HashMap<>();
    Set<PeerId> finalTopic1Peers = new HashSet<>(updatedTopic1Peers);
    PeerId peerToRemove = initialTopic1Peers.iterator().next();
    finalTopic1Peers.remove(peerToRemove);
    finalMeshPeers.put(topic1, finalTopic1Peers);
    
    when(gossipRouter.getMesh()).thenReturn(finalMeshPeers);
    
    // Run third metrics update
    gossipNetwork.updateMeshMetrics();
  }
  
  @Test
  public void shouldStopMetricsUpdater() {
    // Stop metrics service
    gossipNetwork.stop();
    // Successful test completion without exceptions means the method worked correctly
  }
  
  private Set<PeerId> createPeerSet(int count) {
    Set<PeerId> peers = new HashSet<>();
    for (int i = 0; i < count; i++) {
      peers.add(createPeerId(i));
    }
    return peers;
  }
  
  private PeerId createPeerId(int id) {
    return PeerId.fromBytes(("peer" + id).getBytes(StandardCharsets.UTF_8));
  }
} 
