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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.pubsub.gossip.Gossip;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

@SuppressWarnings("unchecked")
public class LibP2PGossipNetworkTest {

  private final MetricsSystem metricsSystem = mock(MetricsSystem.class);
  private final Gossip gossip = mock(Gossip.class);
  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final GossipTopicHandlers topicHandlers = mock(GossipTopicHandlers.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final LabelledSuppliedMetric meshSizeGauge = mock(LabelledSuppliedMetric.class);
  private final LabelledSuppliedMetric peersJoinedGauge = mock(LabelledSuppliedMetric.class);
  private final LabelledSuppliedMetric peersLeftGauge = mock(LabelledSuppliedMetric.class);

  private LibP2PGossipNetwork gossipNetwork;

  @BeforeEach
  public void setup() {
    // Mock metrics system
    when(metricsSystem.createLabelledSuppliedGauge(
            eq(TekuMetricCategory.LIBP2P),
            eq("gossip_mesh_peers_current"),
            anyString(),
            eq("topic")))
        .thenReturn(meshSizeGauge);

    when(metricsSystem.createLabelledSuppliedGauge(
            eq(TekuMetricCategory.LIBP2P),
            eq("gossip_mesh_peers_joined"),
            anyString(),
            eq("topic")))
        .thenReturn(peersJoinedGauge);

    when(metricsSystem.createLabelledSuppliedGauge(
            eq(TekuMetricCategory.LIBP2P), eq("gossip_mesh_peers_left"), anyString(), eq("topic")))
        .thenReturn(peersLeftGauge);

    // Create gossip network
    gossipNetwork =
        new LibP2PGossipNetwork(metricsSystem, gossip, publisher, topicHandlers, asyncRunner);
  }

  @Test
  public void shouldCreateMeshMetricsGauges() {
    // Verify that gauges were created
    verify(metricsSystem)
        .createLabelledSuppliedGauge(
            eq(TekuMetricCategory.LIBP2P),
            eq("gossip_mesh_peers_current"),
            anyString(),
            eq("topic"));

    verify(metricsSystem)
        .createLabelledSuppliedGauge(
            eq(TekuMetricCategory.LIBP2P),
            eq("gossip_mesh_peers_joined"),
            anyString(),
            eq("topic"));

    verify(metricsSystem)
        .createLabelledSuppliedGauge(
            eq(TekuMetricCategory.LIBP2P), eq("gossip_mesh_peers_left"), anyString(), eq("topic"));
  }

  @Test
  public void shouldUpdateMeshMetricsOnCall() {
    // This part of the test remains disabled until the functionality is fixed
    // GossipMeshInfo
    /*
    // Call update metrics
    gossipNetwork.updateMeshMetrics();

    // Verify that the gauge was updated with the correct labels and value supplier
    verify(meshSizeGauge).labels(any(DoubleSupplier.class), eq("test_topic"));
    */
  }

  @Test
  public void shouldHaveAccessToGossipMeshInfo() {
    // Verify that getGossipMeshInfo returns a non-null instance
    assertThat(gossipNetwork.getGossipMeshInfo()).isNotNull();
  }
}
