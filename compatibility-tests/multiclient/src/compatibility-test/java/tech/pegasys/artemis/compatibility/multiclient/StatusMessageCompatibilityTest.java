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

package tech.pegasys.artemis.compatibility.multiclient;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.util.Waiter.waitFor;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.pegasys.artemis.compatibility.multiclient.clients.Prysm;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.NetworkFactory;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Peer;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Peer.StatusData;

@Testcontainers
class StatusMessageCompatibilityTest {

  @Container private static final Prysm PRYSM_NODE = new Prysm();

  private final NetworkFactory networkFactory = new NetworkFactory();
  private JvmLibP2PNetwork artemis;

  @BeforeEach
  public void setUp() throws Exception {
    artemis = networkFactory.startNetwork();
  }

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldExchangeStatusWhenArtemisConnectsToPrysm() throws Exception {
    waitFor(artemis.connect(PRYSM_NODE.getMultiAddr()));
    waitFor(() -> assertThat(artemis.getPeerManager().getAvailablePeerCount()).isEqualTo(1));
    final Peer prysm =
        artemis.getPeerManager().getAvailablePeer(PRYSM_NODE.getPeerId()).orElseThrow();
    final StatusData status = prysm.getStatus();
    assertThat(status).isNotNull();
    assertThat(status.getHeadForkVersion()).isEqualTo(Fork.VERSION_ZERO);

    // No validators so nothing should get finalised.
    assertThat(status.getFinalizedEpoch()).isEqualTo(UnsignedLong.ZERO);
    assertThat(status.getFinalizedRoot()).isEqualTo(Bytes32.ZERO);
    // But we can't verify anything about the slot details as they may have progressed.
  }
}
