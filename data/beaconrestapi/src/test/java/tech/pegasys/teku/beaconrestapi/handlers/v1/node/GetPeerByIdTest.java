/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;

public class GetPeerByIdTest extends AbstractMigratedBeaconHandlerTest {
  private final MockNodeId peerId = new MockNodeId(123456);
  private final Eth2Peer peer = mock(Eth2Peer.class);

  @BeforeEach
  void setUp() {
    setHandler(new GetPeerById(network));
    request.setPathParameter("peer_id", peerId.toBase58());
    when(peer.getId()).thenReturn(peerId);
    when(peer.getAddress()).thenReturn(new PeerAddress(peerId));
    when(peer.isConnected()).thenReturn(true);
    when(peer.connectionInitiatedLocally()).thenReturn(false);
  }

  @Test
  public void shouldReturnNotFoundIfPeerNotFound() throws Exception {
    when(network.getEth2PeerById(peerId.toBase58())).thenReturn(Optional.empty());

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_NOT_FOUND, "Peer not found"));
  }

  @Test
  public void shouldReturnPeerIfFound() throws Exception {
    when(network.getEth2PeerById(eq(peerId.toBase58()))).thenReturn(Optional.of(peer));
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(peer);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, peer);
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"peer_id\":\"1111111111111111111111111111177em\","
                + "\"last_seen_p2p_address\":\"1111111111111111111111111111177em\",\"state\":\"connected\",\"direction\":\"inbound\"}}");
  }
}
