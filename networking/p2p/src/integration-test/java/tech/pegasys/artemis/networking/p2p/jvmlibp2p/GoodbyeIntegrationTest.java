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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.util.Waiter.waitFor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.NetworkFactory;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMethod;

public class GoodbyeIntegrationTest {
  private final NetworkFactory networkFactory = new NetworkFactory();
  private Peer peer1;
  private Peer peer2;
  private JvmLibP2PNetwork network1;
  private JvmLibP2PNetwork network2;

  @BeforeEach
  public void setUp() throws Exception {
    network1 = networkFactory.startNetwork();
    network2 = networkFactory.startNetwork(network1);
    peer1 = network2.getPeerManager().getAvailablePeer(network1.getPeerId()).orElseThrow();
    peer2 = network1.getPeerManager().getAvailablePeer(network2.getPeerId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldCloseConnectionAfterGoodbyeReceived() throws Exception {
    final GoodbyeMessage response =
        waitFor(
            peer1.send(
                RpcMethod.GOODBYE, new GoodbyeMessage(GoodbyeMessage.REASON_CLIENT_SHUT_DOWN)));
    assertThat(response).isNull();
    waitFor(() -> assertThat(peer1.isConnected()).isFalse());
    waitFor(() -> assertThat(peer2.isConnected()).isFalse());
    assertThat(network1.getPeerManager().getAvailablePeerCount()).isZero();
    assertThat(network2.getPeerManager().getAvailablePeerCount()).isZero();
  }
}
