/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.networking.p2p.connection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.connection.PeerPools.PeerPool;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;

class PeerPoolsTest {
  private final PeerPools pools = new PeerPools();

  @Test
  void shouldAddPeerToPool() {
    pools.addPeerToPool(new MockNodeId(1), PeerPool.RANDOMLY_SELECTED);
    pools.addPeerToPool(new MockNodeId(2), PeerPool.STATIC);
    pools.addPeerToPool(new MockNodeId(3), PeerPool.SCORE_BASED);
    assertThat(pools.getPool(new MockNodeId(1))).isEqualTo(PeerPool.RANDOMLY_SELECTED);
    assertThat(pools.getPool(new MockNodeId(2))).isEqualTo(PeerPool.STATIC);
    assertThat(pools.getPool(new MockNodeId(3))).isEqualTo(PeerPool.SCORE_BASED);
  }

  @Test
  void shouldDefaultToScoreBasedPool() {
    assertThat(pools.getPool(new MockNodeId(1))).isEqualTo(PeerPool.SCORE_BASED);
  }

  @Test
  void shouldReturnToDefaultPoolWhenPeerForgotten() {
    final MockNodeId nodeId = new MockNodeId(1);
    pools.addPeerToPool(nodeId, PeerPool.RANDOMLY_SELECTED);
    pools.forgetPeer(nodeId);
    assertThat(pools.getPool(nodeId)).isEqualTo(PeerPool.SCORE_BASED);
  }
}
