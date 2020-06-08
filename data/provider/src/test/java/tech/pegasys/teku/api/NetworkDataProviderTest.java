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

package tech.pegasys.teku.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;

public class NetworkDataProviderTest {

  @SuppressWarnings("unchecked")
  private final Eth2Network p2pNetwork = mock(Eth2Network.class);

  @Test
  void getPeerCount_shouldReturnTotalPeers() {
    final NetworkDataProvider network = new NetworkDataProvider(p2pNetwork);
    final Eth2Peer peer1 = mock(Eth2Peer.class);
    final Eth2Peer peer2 = mock(Eth2Peer.class);
    when(p2pNetwork.streamPeers()).thenReturn(Stream.of(peer1, peer2));

    assertThat(network.getPeerCount()).isEqualTo(2);
    verify(p2pNetwork).streamPeers();
  }

  @Test
  void getPeerCount_shouldReturnTotalPeersIfEmpty() {
    final NetworkDataProvider network = new NetworkDataProvider(p2pNetwork);
    when(p2pNetwork.streamPeers()).thenReturn(Stream.of());

    assertThat(network.getPeerCount()).isEqualTo(0);
    verify(p2pNetwork).streamPeers();
  }

  @Test
  void getListeningAddresses_shouldReturnAddressFromNetwork() {
    final NetworkDataProvider network = new NetworkDataProvider(p2pNetwork);
    final String nodeAddress = "/some/libp2p/addr";

    when(p2pNetwork.getNodeAddress()).thenReturn(nodeAddress);

    assertThat(network.getListeningAddresses()).isEqualTo(List.of(nodeAddress));
  }
}
