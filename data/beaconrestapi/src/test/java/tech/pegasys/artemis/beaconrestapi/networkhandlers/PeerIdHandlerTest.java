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
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;

public class PeerIdHandlerTest {
  private Context mockContext = mock(Context.class);
  private P2PNetwork p2PNetwork = mock(P2PNetwork.class);

  @Test
  public void shouldReturnPeerId() throws Exception {
    final String peerId = "peerId";
    final PeerIdHandler peerIdHandler = new PeerIdHandler(p2PNetwork);

    when(p2PNetwork.getNodeAddress()).thenReturn(peerId);

    peerIdHandler.handle(mockContext);
    verify(mockContext).result(peerId);
  }
}
