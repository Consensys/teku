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

package tech.pegasys.artemis.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.artemis.networking.p2p.peer.DisconnectRequestHandler.DisconnectReason;
import tech.pegasys.artemis.util.Waiter;

public class GoodbyeIntegrationTest {
  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Peer peer1;
  private Eth2Peer peer2;

  private void setUp(final RpcEncoding rpcEncoding) throws Exception {
    final Eth2Network network1 = networkFactory.builder().rpcEncoding(rpcEncoding).startNetwork();
    final Eth2Network network2 =
        networkFactory.builder().rpcEncoding(rpcEncoding).peer(network1).startNetwork();
    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
    peer2 = network1.getPeer(network2.getNodeId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @ParameterizedTest(name = "encoding: {0}")
  @MethodSource("getEncodings")
  public void shouldCloseConnectionAfterGoodbyeReceived(
      final String encodingName, final RpcEncoding encoding) throws Exception {
    setUp(encoding);
    peer1.disconnectCleanly(DisconnectReason.SHUTTING_DOWN);
    Waiter.waitFor(() -> assertThat(peer1.isConnected()).isFalse());
    Waiter.waitFor(() -> assertThat(peer2.isConnected()).isFalse());
  }

  public static Stream<Arguments> getEncodings() {
    final List<RpcEncoding> encodings = List.of(RpcEncoding.SSZ, RpcEncoding.SSZ_SNAPPY);
    return encodings.stream().map(e -> Arguments.of(e.getName(), e));
  }
}
