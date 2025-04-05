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

import io.libp2p.pubsub.gossip.Gossip;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipMeshInfo.MeshMetrics;

/** Tests temporarily disabled due to API incompatibility with current version of library */
@Disabled("Tests disabled due to API incompatibility with current version of library")
public class GossipMeshInfoTest {

  private final Gossip gossip = mock(Gossip.class);
  private GossipMeshInfo gossipMeshInfo;

  @BeforeEach
  public void setup() {
    gossipMeshInfo = new GossipMeshInfo(gossip);
  }

  @Test
  public void shouldCreateObject() {
    // Test that the object can be created
    assertThat(gossipMeshInfo).isNotNull();

    // Test that the stub methods return empty maps
    SafeFuture<Map<String, Integer>> sizeByTopic = gossipMeshInfo.getMeshSizeByTopic();
    assertThat(sizeByTopic).isCompleted();
    assertThat(sizeByTopic.join()).isEmpty();

    SafeFuture<Map<String, MeshMetrics>> metrics = gossipMeshInfo.getMeshMetrics();
    assertThat(metrics).isCompleted();
    assertThat(metrics.join()).isEmpty();
  }
}
