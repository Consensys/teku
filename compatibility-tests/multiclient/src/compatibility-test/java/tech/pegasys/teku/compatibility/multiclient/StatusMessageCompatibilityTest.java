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

package tech.pegasys.teku.compatibility.multiclient;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.pegasys.teku.compatibility.multiclient.clients.Prysm;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.eth2.Eth2NetworkFactory;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.util.config.Constants;

@Testcontainers
class StatusMessageCompatibilityTest {

  @Container private static final Prysm PRYSM_NODE = new Prysm();

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Network teku;

  @BeforeEach
  public void setUp() throws Exception {
    Constants.setConstants("mainnet");
    teku = networkFactory.builder().startNetwork();
  }

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
    Constants.setConstants("minimal");
  }

  @Test
  public void shouldExchangeStatusWhenTekuConnectsToPrysm() throws Exception {
    waitFor(teku.connect(teku.createPeerAddress(PRYSM_NODE.getMultiAddr())));
    waitFor(() -> assertThat(teku.getPeerCount()).isEqualTo(1));
    final Eth2Peer prysm = teku.getPeer(PRYSM_NODE.getId()).orElseThrow();
    final PeerStatus status = prysm.getStatus();
    assertThat(status).isNotNull();

    // No validators so nothing should get finalized.
    assertThat(status.getFinalizedEpoch()).isEqualTo(UInt64.ZERO);
    assertThat(status.getFinalizedRoot()).isEqualTo(Bytes32.ZERO);
    // But we can't verify anything about the slot details as they may have progressed.
  }
}
