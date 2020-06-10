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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.provider.JsonProvider;

public class GetPeerIdTest {
  private Context context = mock(Context.class);
  private final JsonProvider jsonProvider = new JsonProvider();

  @SuppressWarnings("unchecked")
  private final Eth2Network eth2Network = mock(Eth2Network.class);

  @Test
  public void shouldReturnPeerId() throws Exception {
    NetworkDataProvider network = new NetworkDataProvider(eth2Network);
    final PeerId peerId1 = new PeerId(PeerId.random().getBytes());
    final NodeId nodeId1 = new LibP2PNodeId(peerId1);
    final GetPeerId handler = new GetPeerId(network, jsonProvider);

    when(eth2Network.getNodeId()).thenReturn(nodeId1);
    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).result(jsonProvider.objectToJSON(nodeId1.toBase58()));
  }
}
