/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.node.GetPeersScore.LastAction;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.node.GetPeersScore.PeerScore;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

public class GetPeersScoreTest extends AbstractMigratedBeaconHandlerTest {
  private final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
  private final ReputationManager reputationManager = mock(ReputationManager.class);
  private final MockNodeId peerId = new MockNodeId(123456);
  private final Eth2Peer peer = mock(Eth2Peer.class);

  @BeforeEach
  void setup() {
    setHandler(new GetPeersScore(networkDataProvider));

    when(peer.getId()).thenReturn(peerId);
    when(peer.getGossipScore()).thenReturn(1.0);
    when(networkDataProvider.getReputationManager()).thenReturn(reputationManager);
    when(reputationManager.getReputationScore(any())).thenReturn(OptionalInt.empty());
    when(reputationManager.getLastAdjustment(any())).thenReturn(Optional.empty());
  }

  @Test
  public void shouldReturnListOfPeerScores() throws Exception {
    when(networkDataProvider.getPeerScores()).thenReturn(List.of(peer));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody())
        .isEqualTo(
            List.of(new PeerScore(peerId.toBase58(), 1.0, Optional.empty(), Optional.empty())));
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
  void metadata_shouldHandle200_withoutLastAction() throws IOException {
    final List<PeerScore> responseData =
        List.of(new PeerScore(peerId.toBase58(), 1.0, Optional.empty(), Optional.empty()));

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        "{\"data\":[{\"peer_id\":\"1111111111111111111111111111177em\",\"gossip_score\":1.0}]}";
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }

  @Test
  void metadata_shouldHandle200_withReputationAndLastAction() throws IOException {
    final List<PeerScore> responseData =
        List.of(
            new PeerScore(
                peerId.toBase58(),
                1.0,
                Optional.of(3),
                Optional.of(new LastAction("SMALL_PENALTY", -3.0, 12))));

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        "{\"data\":[{"
            + "\"peer_id\":\"1111111111111111111111111111177em\","
            + "\"gossip_score\":1.0,"
            + "\"reputation_score\":3,"
            + "\"last_action\":{\"reason\":\"SMALL_PENALTY\",\"delta\":-3.0,\"seconds_ago\":12}"
            + "}]}";
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }
}
