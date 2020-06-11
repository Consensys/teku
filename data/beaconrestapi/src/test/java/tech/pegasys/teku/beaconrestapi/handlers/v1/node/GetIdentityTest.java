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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.node.IdentityResponse;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.provider.JsonProvider;

public class GetIdentityTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final Context context = mock(Context.class);

  @SuppressWarnings("unchecked")
  private final Eth2Network eth2Network = mock(Eth2Network.class);

  private final NetworkDataProvider network = new NetworkDataProvider(eth2Network);

  private final ArgumentCaptor<String> stringArgs = ArgumentCaptor.forClass(String.class);

  @Test
  public void shouldReturnExpectedObjectType() throws Exception {
    GetIdentity handler = new GetIdentity(network, jsonProvider);
    NodeId nodeid = mock(NodeId.class);

    when(eth2Network.getMetadata()).thenReturn(MetadataMessage.createDefault());
    when(eth2Network.getNodeId()).thenReturn(nodeid);
    when(nodeid.toBase58()).thenReturn("aeiou");
    when(eth2Network.getNodeAddress()).thenReturn("address");

    handler.handle(context);
    verify(context).result(stringArgs.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    String val = stringArgs.getValue();
    assertThat(val).isNotNull();
    IdentityResponse response = jsonProvider.jsonToObject(val, IdentityResponse.class);
    assertThat(response.data.peerId).isEqualTo("aeiou");
    assertThat(response.data.p2pAddresses.get(0)).isEqualTo("address");
  }
}
