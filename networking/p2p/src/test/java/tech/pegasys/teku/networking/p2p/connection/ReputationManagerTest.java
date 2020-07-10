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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.metrics.StubGauge;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.util.time.StubTimeProvider;

class ReputationManagerTest {

  private static final int MORE_THAN_DISALLOW_PERIOD =
      ReputationManager.FAILURE_BAN_PERIOD.intValue() + 1;
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final PeerAddress peerAddress = new PeerAddress(new MockNodeId(1));
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final ReputationManager reputationManager =
      new ReputationManager(metricsSystem, timeProvider, 5);

  @Test
  public void shouldDisallowConnectionInitiationWhenConnectionHasFailedRecently() {
    reputationManager.reportInitiatedConnectionFailed(peerAddress);
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isFalse();
  }

  @Test
  public void shouldAllowConnectionInitiationToUnknownPeers() {
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();
  }

  @Test
  public void shouldAllowConnectionAfterSuccessfulConnection() {
    reputationManager.reportInitiatedConnectionFailed(peerAddress);
    reputationManager.reportInitiatedConnectionSuccessful(peerAddress);

    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();
  }

  @Test
  public void shouldAllowConnectionInitiationAfterTimePasses() {
    reputationManager.reportInitiatedConnectionFailed(peerAddress);

    timeProvider.advanceTimeBySeconds(MORE_THAN_DISALLOW_PERIOD);

    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();
  }

  @Test
  public void shouldReportCacheSize() {
    final StubGauge cacheSizeGauge =
        metricsSystem.getGauge(TekuMetricCategory.NETWORK, "peer_reputation_cache_size");
    assertThat(cacheSizeGauge.getValue()).isZero();

    reputationManager.reportInitiatedConnectionFailed(peerAddress);
    assertThat(cacheSizeGauge.getValue()).isEqualTo(1);

    reputationManager.reportInitiatedConnectionFailed(peerAddress);
    reputationManager.reportInitiatedConnectionFailed(new PeerAddress(new MockNodeId(2)));
    reputationManager.reportInitiatedConnectionSuccessful(new PeerAddress(new MockNodeId(2)));
    assertThat(cacheSizeGauge.getValue()).isEqualTo(2);
  }

  @Test
  void shouldNotAllowConnectionAfterDisconnect() {
    reputationManager.reportDisconnection(peerAddress, Optional.empty(), true);

    assertThat(reputationManager.isConnectionInitiationAllowed(new PeerAddress(new MockNodeId(1))))
        .isFalse();
  }

  @Test
  void shouldAllowConnectionAfterDisconnectAfterTimePasses() {
    reputationManager.reportDisconnection(peerAddress, Optional.empty(), true);

    timeProvider.advanceTimeBySeconds(MORE_THAN_DISALLOW_PERIOD);

    assertThat(reputationManager.isConnectionInitiationAllowed(new PeerAddress(new MockNodeId(1))))
        .isTrue();
  }

  @Test
  void shouldNeverAllowReconnectionForPermanentDisconnectReasons() {
    reputationManager.reportDisconnection(
        peerAddress, Optional.of(DisconnectReason.IRRELEVANT_NETWORK), true);

    timeProvider.advanceTimeBySeconds(MORE_THAN_DISALLOW_PERIOD);

    assertThat(reputationManager.isConnectionInitiationAllowed(new PeerAddress(new MockNodeId(1))))
        .isFalse();
  }
}
