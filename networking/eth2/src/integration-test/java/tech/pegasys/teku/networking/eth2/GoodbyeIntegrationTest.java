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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding.SSZ_SNAPPY;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;

public class GoodbyeIntegrationTest {
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();
  private Eth2Peer peer1;
  private Eth2Peer peer2;

  private void setUp() throws Exception {
    final Eth2P2PNetwork network1 = networkFactory.builder().rpcEncoding(SSZ_SNAPPY).startNetwork();
    final Eth2P2PNetwork network2 =
        networkFactory.builder().rpcEncoding(SSZ_SNAPPY).peer(network1).startNetwork();
    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
    peer2 = network1.getPeer(network2.getNodeId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldCloseConnectionAfterGoodbyeReceived() throws Exception {
    setUp();
    Waiter.waitFor(peer1.disconnectCleanly(DisconnectReason.SHUTTING_DOWN));
    Waiter.waitFor(() -> assertThat(peer1.isConnected()).isFalse());
    Waiter.waitFor(() -> assertThat(peer2.isConnected()).isFalse());
  }
}
