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
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.network.PeerAddress;
import tech.pegasys.artemis.util.time.StubTimeProvider;

class ReputationManagerTest {
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final PeerAddress peerAddress = new PeerAddress(new MockNodeId(1));

  private final ReputationManager reputationManager = new ReputationManager(timeProvider, 5);

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

    timeProvider.advanceTimeBySeconds(61);

    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();
  }
}
