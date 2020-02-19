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

package tech.pegasys.artemis.beaconrestapi.networkhandlers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import io.libp2p.core.PeerId;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.provider.JsonProvider;

@ExtendWith(MockitoExtension.class)
public class PeersHandlerTest {
  @Test
  public void shouldReturnArrayOfPeersIfPresent(
      @Mock Context mockContext, @Mock P2PNetwork<Peer> p2PNetwork) throws Exception {
    final PeersHandler peersHandler = new PeersHandler(p2PNetwork);
    final Peer peer1 = mock(Peer.class);
    final Peer peer2 = mock(Peer.class);
    final PeerId peerId1 = new PeerId(PeerId.random().getBytes());
    final PeerId peerId2 = new PeerId(PeerId.random().getBytes());
    final NodeId nodeId1 = new LibP2PNodeId(peerId1);
    final NodeId nodeId2 = new LibP2PNodeId(peerId2);

    when(peer1.getId()).thenReturn(nodeId1);
    when(peer2.getId()).thenReturn(nodeId2);
    when(p2PNetwork.streamPeers()).thenReturn(Stream.of(peer1, peer2));

    final String response =
        JsonProvider.objectToJSON(new String[] {peerId1.toBase58(), peerId2.toBase58()});

    peersHandler.handle(mockContext);
    verify(mockContext).result(response);
  }

  @Test
  public void shouldReturnEmptyPeersArrayIfNoneConnected(
      @Mock Context mockContext, @Mock P2PNetwork<Peer> p2PNetwork) throws Exception {
    final PeersHandler peersHandler = new PeersHandler(p2PNetwork);
    final String response = JsonProvider.objectToJSON(new String[] {});

    when(p2PNetwork.streamPeers()).thenReturn(Stream.empty());

    peersHandler.handle(mockContext);
    verify(mockContext).result(response);
  }
}
