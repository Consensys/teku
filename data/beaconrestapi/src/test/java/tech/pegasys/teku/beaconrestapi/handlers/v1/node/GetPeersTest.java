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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers.PeersData;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;

public class GetPeersTest extends AbstractMigratedBeaconHandlerTest {
  private final MockNodeId peerId1 = new MockNodeId(123456);
  private final Eth2Peer peer1 = mock(Eth2Peer.class);

  private final MockNodeId peerId2 = new MockNodeId(789123);
  private final Eth2Peer peer2 = mock(Eth2Peer.class);

  private final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
  private final List<Eth2Peer> data = List.of(peer1, peer2);
  private final GetPeers.PeersData peersData = new PeersData(data);

  @BeforeEach
  void setup() {
    setHandler(new GetPeers(networkDataProvider));
    when(peer1.getId()).thenReturn(peerId1);
    when(peer1.getAddress()).thenReturn(new PeerAddress(peerId1));
    when(peer1.isConnected()).thenReturn(true);
    when(peer1.connectionInitiatedLocally()).thenReturn(false);

    when(peer2.getId()).thenReturn(peerId2);
    when(peer2.getAddress()).thenReturn(new PeerAddress(peerId2));
    when(peer2.isConnected()).thenReturn(true);
    when(peer2.connectionInitiatedLocally()).thenReturn(true);
  }

  @Test
  public void shouldReturnListOfPeers() throws Exception {

    when(networkDataProvider.getEth2Peers()).thenReturn(data);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getCacheLength()).isEqualTo(CacheLength.NO_CACHE);
    assertThat(request.getResponseBody()).isEqualTo(peersData);
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
    final String data = getResponseStringFromMetadata(handler, SC_OK, peersData);
    assertThat(data)
        .isEqualTo(
            "{\"data\":[{\"peer_id\":\"1111111111111111111111111111177em\","
                + "\"last_seen_p2p_address\":\"1111111111111111111111111111177em\","
                + "\"state\":\"connected\",\"direction\":\"inbound\"},"
                + "{\"peer_id\":\"11111111111111111111111111111hVqL\","
                + "\"last_seen_p2p_address\":\"11111111111111111111111111111hVqL\","
                + "\"state\":\"connected\",\"direction\":\"outbound\"}],\"meta\":{\"count\":2}}");
  }
}
