/*
 * Copyright 2021 ConsenSys AG.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.libp2p.core.PeerId;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.teku.GetPeerScoresResponse;
import tech.pegasys.teku.api.response.v1.teku.PeerScore;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;

public class GetPeersScoreTest extends AbstractBeaconHandlerTest {
  final String peerId1 = PeerId.random().toBase58();
  final PeerScore peerScore1 = new PeerScore(peerId1, 1.0);
  final String peerId2 = PeerId.random().toBase58();
  final PeerScore peerScore2 = new PeerScore(peerId2, 0.05);

  @Test
  public void shouldReturnListOfPeerScores() throws Exception {
    final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
    GetPeersScore handler = new GetPeersScore(networkDataProvider, jsonProvider);
    when(networkDataProvider.getPeerScores()).thenReturn(List.of(peerScore1, peerScore2));
    handler.handle(context);

    GetPeerScoresResponse response = getResponseObject(GetPeerScoresResponse.class);
    assertThat(response.data).containsExactlyInAnyOrder(peerScore1, peerScore2);
  }
}
