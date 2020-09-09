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
import static org.mockito.Mockito.when;

import io.libp2p.core.PeerId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.node.Direction;
import tech.pegasys.teku.api.response.v1.node.Peer;
import tech.pegasys.teku.api.response.v1.node.PeerResponse;
import tech.pegasys.teku.api.response.v1.node.State;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;

public class GetPeerByIdTest extends AbstractBeaconHandlerTest {
  final String peerId = PeerId.random().toBase58();
  final Peer peer =
      new Peer(
          peerId, null, "/ip4/7.7.7.7/tcp/4242/p2p/" + peerId, State.connected, Direction.inbound);

  @Test
  public void shouldReturnNotFoundIfPeerNotFound() throws Exception {
    final GetPeerById handler = new GetPeerById(network, jsonProvider);
    when(network.getPeerById(peerId)).thenReturn(Optional.empty());
    when(context.pathParamMap()).thenReturn(Map.of("peer_id", peerId));
    handler.handle(context);

    verifyStatusCode(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnPeerIfFound() throws Exception {
    final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
    GetPeerById handler = new GetPeerById(networkDataProvider, jsonProvider);
    when(context.pathParamMap()).thenReturn(Map.of("peer_id", peerId));
    when(networkDataProvider.getPeerById(eq(peerId))).thenReturn(Optional.of(peer));
    handler.handle(context);

    final PeerResponse response = getResponseObject(PeerResponse.class);

    assertThat(response.data).isEqualTo(peer);
  }
}
