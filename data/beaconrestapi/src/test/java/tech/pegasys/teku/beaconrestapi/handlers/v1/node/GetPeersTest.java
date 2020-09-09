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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.libp2p.core.PeerId;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.node.Direction;
import tech.pegasys.teku.api.response.v1.node.Peer;
import tech.pegasys.teku.api.response.v1.node.PeersResponse;
import tech.pegasys.teku.api.response.v1.node.State;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;

public class GetPeersTest extends AbstractBeaconHandlerTest {
  final String peerId1 = PeerId.random().toBase58();
  final Peer peer1 =
      new Peer(
          peerId1,
          null,
          "/ip4/7.7.7.7/tcp/4242/p2p/" + peerId1,
          State.connected,
          Direction.inbound);
  final String peerId2 = PeerId.random().toBase58();
  final Peer peer2 =
      new Peer(
          peerId2,
          null,
          "/ip4/8.8.8.8/tcp/4243/p2p/" + peerId2,
          State.connected,
          Direction.outbound);

  @Test
  public void shouldReturnListOfPeers() throws Exception {
    final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
    GetPeers handler = new GetPeers(networkDataProvider, jsonProvider);
    when(networkDataProvider.getPeers()).thenReturn(List.of(peer1, peer2));
    handler.handle(context);

    PeersResponse response = getResponseObject(PeersResponse.class);
    assertThat(response.data).containsExactlyInAnyOrder(peer1, peer2);
  }
}
