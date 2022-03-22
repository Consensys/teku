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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;

public class GetPeersTest extends AbstractBeaconHandlerTest {
  final MockNodeId peerId1 = new MockNodeId(123456);
  final Eth2Peer peer1 = mock(Eth2Peer.class);

  final MockNodeId peerId2 = new MockNodeId(789123);
  final Eth2Peer peer2 = mock(Eth2Peer.class);

  @BeforeEach
  void setup() {
    when(peer1.getId()).thenReturn(peerId1);
    when(peer1.getAddress()).thenReturn(new PeerAddress(peerId1));
    when(peer1.isConnected()).thenReturn(true);
    when(peer1.connectionInitiatedLocally()).thenReturn(false);

    when(peer2.getId()).thenReturn(peerId2);
    when(peer2.getAddress()).thenReturn(new PeerAddress(peerId2));
    when(peer2.isConnected()).thenReturn(true);
    when(peer2.connectionInitiatedLocally()).thenReturn(true);
  }

  @Test
  public void shouldReturnListOfPeers() throws Exception {
    List<Eth2Peer> data = List.of(peer1, peer2);

    final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
    when(networkDataProvider.getEth2Peers()).thenReturn(data);

    GetPeers handler = new GetPeers(networkDataProvider);
    final RestApiRequest request = mock(RestApiRequest.class);
    handler.handleRequest(request);
    verify(request).respondOk(refEq(data), eq(CacheLength.NO_CACHE));
  }
}
