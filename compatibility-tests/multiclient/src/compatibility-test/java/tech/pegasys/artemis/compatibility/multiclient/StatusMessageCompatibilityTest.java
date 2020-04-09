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
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkFactory;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.PeerStatus;
import tech.pegasys.artemis.util.config.Constants;

@Testcontainers
class StatusMessageCompatibilityTest {

  @Container private static final Prysm PRYSM_NODE = new Prysm();

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Network artemis;

  @BeforeEach
  public void setUp() throws Exception {
    Constants.setConstants("mainnet");
    artemis = networkFactory.builder().startNetwork();
  }

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
    Constants.setConstants("minimal");
  }

  @Test
  public void shouldExchangeStatusWhenArtemisConnectsToPrysm() throws Exception {
    waitFor(artemis.connect(artemis.createPeerAddress(PRYSM_NODE.getMultiAddr())));
    waitFor(() -> assertThat(artemis.getPeerCount()).isEqualTo(1));
    final Eth2Peer prysm = artemis.getPeer(PRYSM_NODE.getId()).orElseThrow();
    final PeerStatus status = prysm.getStatus();
    assertThat(status).isNotNull();
    assertThat(status.getHeadForkVersion()).isEqualTo(Constants.GENESIS_FORK_VERSION);

    // No validators so nothing should get finalized.
    assertThat(status.getFinalizedEpoch()).isEqualTo(UnsignedLong.ZERO);
    assertThat(status.getFinalizedRoot()).isEqualTo(Bytes32.ZERO);
    // But we can't verify anything about the slot details as they may have progressed.
  }
}
