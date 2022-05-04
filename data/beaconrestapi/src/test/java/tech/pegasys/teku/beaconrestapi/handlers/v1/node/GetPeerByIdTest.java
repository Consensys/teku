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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequestImpl;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;

public class GetPeerByIdTest extends AbstractMigratedBeaconHandlerTest {
  final MockNodeId peerId = new MockNodeId(123456);
  final Eth2Peer peer = mock(Eth2Peer.class);

  @BeforeEach
  void setUp() {
    when(peer.getId()).thenReturn(peerId);
    when(peer.getAddress()).thenReturn(new PeerAddress(peerId));
    when(peer.isConnected()).thenReturn(true);
    when(peer.connectionInitiatedLocally()).thenReturn(false);
  }

  @Test
  public void shouldReturnNotFoundIfPeerNotFound() throws Exception {
    final RestApiRequest request = mock(RestApiRequestImpl.class);
    final GetPeerById handler = new GetPeerById(network);
    when(network.getEth2PeerById(peerId.toBase58())).thenReturn(Optional.empty());
    when(context.pathParamMap()).thenReturn(Map.of("peer_id", peerId.toBase58()));

    handler.handleRequest(request);
    verify(request).respondError(eq(SC_NOT_FOUND), eq("Peer not found"));
  }

  @Test
  public void shouldReturnPeerIfFound() throws Exception {
    final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
    GetPeerById handler = new GetPeerById(networkDataProvider);
    when(context.pathParamMap()).thenReturn(Map.of("peer_id", peerId.toBase58()));
    when(networkDataProvider.getEth2PeerById(eq(peerId.toBase58()))).thenReturn(Optional.of(peer));

    final RestApiRequest request = new RestApiRequestImpl(context, handler.getMetadata());

    handler.handleRequest(request);

    checkResponse(peerId.toBase58(), peer.getAddress().toExternalForm(), "connected", "inbound");
  }

  private void checkResponse(String peerId, String address, String state, String direction) {
    final String expectedResponse =
        String.format(
            "{\"data\":{\"peer_id\":\"%s\",\"last_seen_p2p_address\":\"%s\",\"state\":\"%s\",\"direction\":\"%s\"}}",
            peerId, address, state, direction);

    assertThat(getResultString()).isEqualTo(expectedResponse);
  }
}
