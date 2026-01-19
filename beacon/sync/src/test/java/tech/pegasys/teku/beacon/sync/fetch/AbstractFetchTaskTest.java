/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.beacon.sync.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class AbstractFetchTaskTest {

  protected final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalDeneb());
  protected final Eth2P2PNetwork eth2P2PNetwork = mock(Eth2P2PNetwork.class);
  protected final List<Eth2Peer> peers = new ArrayList<>();

  @BeforeEach
  public void setup() {
    when(eth2P2PNetwork.streamPeers()).thenAnswer((invocation) -> peers.stream());
  }

  protected Eth2Peer registerNewPeer(final int id) {
    final Eth2Peer peer = createNewPeer(id);
    peers.add(peer);
    return peer;
  }

  protected Eth2Peer createNewPeer(final int id) {
    final Eth2Peer peer = mock(Eth2Peer.class);
    final PeerStatus peerStatus = mock(PeerStatus.class);

    when(peer.getOutstandingRequests()).thenReturn(0);
    when(peer.getId()).thenReturn(new MockNodeId(id));
    when(peer.getStatus()).thenReturn(peerStatus);

    return peer;
  }

  protected <T> void assertPeerPrioritizesEarliestAvailableSlotAfterOutstandingRequests(
      final AbstractFetchTask<?, T> task,
      final T expectedResult,
      final ThrowingConsumer<Eth2Peer> mockResponse) {

    final Eth2Peer peer1 = registerNewPeer(1);
    final Eth2Peer peer2 = registerNewPeer(2);
    final Eth2Peer peer3 = registerNewPeer(3);

    when(peer1.getOutstandingRequests()).thenReturn(1);
    when(peer2.getOutstandingRequests()).thenReturn(1);
    when(peer3.getOutstandingRequests()).thenReturn(1);

    when(peer1.getStatus().getEarliestAvailableSlot()).thenReturn(Optional.of(UInt64.valueOf(100)));
    when(peer2.getStatus().getEarliestAvailableSlot()).thenReturn(Optional.of(UInt64.valueOf(50)));
    when(peer3.getStatus().getEarliestAvailableSlot()).thenReturn(Optional.of(UInt64.valueOf(75)));

    try {
      mockResponse.accept(peer2);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    final SafeFuture<FetchResult<T>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<T> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer2);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(expectedResult);
  }

  protected <T> void assertPeerPrioritizesOutstandingRequestsOverEarliestAvailableSlot(
      final AbstractFetchTask<?, T> task,
      final T expectedResult,
      final ThrowingConsumer<Eth2Peer> mockResponse) {

    final Eth2Peer peer1 = registerNewPeer(1);
    final Eth2Peer peer2 = registerNewPeer(2);

    when(peer1.getOutstandingRequests()).thenReturn(2);
    when(peer2.getOutstandingRequests()).thenReturn(1);

    when(peer1.getStatus().getEarliestAvailableSlot())
        .thenReturn(Optional.of(UInt64.valueOf(50))); // Earlier slot
    when(peer2.getStatus().getEarliestAvailableSlot())
        .thenReturn(Optional.of(UInt64.valueOf(100))); // Later slot

    try {
      mockResponse.accept(peer2);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    final SafeFuture<FetchResult<T>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<T> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer2);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(expectedResult);
  }

  @FunctionalInterface
  protected interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;
  }
}
