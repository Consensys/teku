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

package tech.pegasys.artemis.beaconrestapi.handlers.network;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.libp2p.core.PeerId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.NetworkDataProvider;
import tech.pegasys.artemis.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.provider.JsonProvider;

@ExtendWith(MockitoExtension.class)
public class GetPeerIdTest {
  private Context context = mock(Context.class);
  @Mock P2PNetwork<Peer> p2pNetwork;
  @Mock private JsonProvider jsonProvider;

  @Test
  public void shouldReturnPeerId() throws Exception {
    NetworkDataProvider network = new NetworkDataProvider(p2pNetwork);
    final PeerId peerId1 = new PeerId(PeerId.random().getBytes());
    final NodeId nodeId1 = new LibP2PNodeId(peerId1);
    final GetPeerId handler = new GetPeerId(network, jsonProvider);

    when(p2pNetwork.getNodeId()).thenReturn(nodeId1);
    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).result(jsonProvider.objectToJSON(nodeId1.toBase58()));
  }
}
