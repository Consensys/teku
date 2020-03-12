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

package tech.pegasys.artemis.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.Peer;

public class NetworkDataProviderTest {
  @SuppressWarnings("unchecked")
  private P2PNetwork<Peer> p2pNetwork = mock(P2PNetwork.class);

  @Test
  void getPeerCount_shouldReturnTotalPeers() {
    final NetworkDataProvider network = new NetworkDataProvider(p2pNetwork);
    final Peer peer1 = mock(Peer.class);
    final Peer peer2 = mock(Peer.class);
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
}
