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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.CACHE_ONE_HOUR;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.NetworkDataProvider;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.provider.JsonProvider;

@ExtendWith(MockitoExtension.class)
public class ENRHandlerTest {
  @Mock Context context;
  @Mock P2PNetwork<Peer> p2pNetwork;
  private final JsonProvider jsonProvider = new JsonProvider();
  private String ENR = "enr:-";

  @Test
  public void shouldReturnEmptyStringWhenDiscoveryNotInUse() throws Exception {
    NetworkDataProvider network = new NetworkDataProvider(p2pNetwork);
    ENRHandler handler = new ENRHandler(network, jsonProvider);
    when(p2pNetwork.getEnr()).thenReturn(Optional.empty());
    handler.handle(context);

    verify(context).header(Header.CACHE_CONTROL, CACHE_ONE_HOUR);
    verify(context).result(jsonProvider.objectToJSON(""));
  }

  @Test
  public void shouldReturnPopulatedStringWhenDiscoveryIsInUse() throws Exception {
    NetworkDataProvider network = new NetworkDataProvider(p2pNetwork);
    ENRHandler handler = new ENRHandler(network, jsonProvider);
    when(p2pNetwork.getEnr()).thenReturn(Optional.of(ENR));
    handler.handle(context);

    verify(context).header(Header.CACHE_CONTROL, CACHE_ONE_HOUR);
    verify(context).result(jsonProvider.objectToJSON(ENR));
  }
}
