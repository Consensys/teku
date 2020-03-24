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

package tech.pegasys.artemis.networking.p2p.connection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TargetPeerRangeTest {
  @Test
  public void shouldNotAddPeersWhenPeerCountIsEqualToRangeBottom() {
    assertThat(new TargetPeerRange(5, 10).getPeersToAdd(5)).isZero();
  }

  @Test
  public void shouldNotAddPeersWhenPeerCountIsWithinRange() {
    assertThat(new TargetPeerRange(5, 10).getPeersToAdd(6)).isZero();
  }

  @Test
  public void shouldNotAddPeersWhenPeerCountIsAboveRange() {
    assertThat(new TargetPeerRange(5, 10).getPeersToAdd(26)).isZero();
  }

  @Test
  public void shouldAddEnoughPeersToReachUpperBoundWhenPeerCountIsBelowRange() {
    assertThat(new TargetPeerRange(5, 10).getPeersToAdd(4)).isEqualTo(6);
  }

  @Test
  public void shouldNotDropPeersWhenPeerCountIsEqualToTopOfRange() {
    assertThat(new TargetPeerRange(5, 10).getPeersToDrop(10)).isZero();
  }

  @Test
  public void shouldNotDropPeersWhenPeerCountIsWithinRange() {
    assertThat(new TargetPeerRange(5, 10).getPeersToDrop(8)).isZero();
  }

  @Test
  public void shouldDropPeersToReachUpperBoundWhenPeerCountIsAboveRange() {
    assertThat(new TargetPeerRange(5, 10).getPeersToDrop(12)).isEqualTo(2);
  }
}
