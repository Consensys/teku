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

package tech.pegasys.teku.beaconrestapi.handlers.network;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.libp2p.core.PeerId;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.provider.JsonProvider;

public class GetPeersTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final Context context = mock(Context.class);

  private final Eth2Network eth2Network = mock(Eth2Network.class);

  @Test
  public void shouldReturnArrayOfPeersIfPresent() throws Exception {
    final NetworkDataProvider network = new NetworkDataProvider(eth2Network);
    final GetPeers handler = new GetPeers(network, jsonProvider);
    final Eth2Peer peer1 = mock(Eth2Peer.class);
    final Eth2Peer peer2 = mock(Eth2Peer.class);
    final PeerId peerId1 = new PeerId(PeerId.random().getBytes());
    final PeerId peerId2 = new PeerId(PeerId.random().getBytes());
    final NodeId nodeId1 = new LibP2PNodeId(peerId1);
    final NodeId nodeId2 = new LibP2PNodeId(peerId2);

    when(peer1.getId()).thenReturn(nodeId1);
    when(peer2.getId()).thenReturn(nodeId2);
    when(eth2Network.streamPeers()).thenReturn(Stream.of(peer1, peer2));

    final String response =
        jsonProvider.objectToJSON(new String[] {peerId1.toBase58(), peerId2.toBase58()});

    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).result(response);
  }

  @Test
  public void shouldReturnEmptyPeersArrayIfNoneConnected() throws Exception {
    final NetworkDataProvider network = new NetworkDataProvider(eth2Network);
    final GetPeers handler = new GetPeers(network, jsonProvider);
    final String response = jsonProvider.objectToJSON(new String[] {});

    when(eth2Network.streamPeers()).thenReturn(Stream.empty());

    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).result(response);
  }
}
