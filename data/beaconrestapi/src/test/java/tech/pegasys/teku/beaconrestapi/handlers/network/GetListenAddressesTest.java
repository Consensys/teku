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

import io.javalin.http.Context;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.provider.JsonProvider;

class GetListenAddressesTest {
  private final Context context = mock(Context.class);

  private final JsonProvider jsonProvider = new JsonProvider();

  @Test
  public void shouldReturnListenerAddressList() throws Exception {
    final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
    final GetListenAddresses handler = new GetListenAddresses(networkDataProvider, jsonProvider);
    final List<String> addresses = List.of("/ip4/127.0.0.1/tcp/9000");
    final String expected = jsonProvider.objectToJSON(addresses);

    when(networkDataProvider.getListeningAddresses()).thenReturn(addresses);

    handler.handle(context);
    verify(context).result(expected);
  }
}
