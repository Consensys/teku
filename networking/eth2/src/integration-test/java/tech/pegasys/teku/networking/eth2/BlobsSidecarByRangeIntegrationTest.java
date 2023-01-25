/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;

public class BlobsSidecarByRangeIntegrationTest extends AbstractRpcMethodIntegrationTest {

  @Test
  public void shouldFailBeforeEip4844Milestone() {
    final Eth2Peer peer = createPeer();
    assertThatThrownBy(() -> requestBlobsSideCars(peer, UInt64.ONE, UInt64.valueOf(2)))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("BlobsSidecarsByRange method is not available");
  }

  @Test
  public void shouldFailWhenBlobsSidecarsNotAvailable() {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalEip4844());
    assertThatThrownBy(() -> requestBlobsSideCars(peer, UInt64.ONE, UInt64.valueOf(2)))
        .hasRootCauseInstanceOf(RpcException.class)
        .hasMessageContaining("Requested blobs sidecars are not available");
  }

  @Test
  public void shouldReturnAvailableBlobsSideCars() throws Exception {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalEip4844());

    peerStorage.chainUpdater().advanceChain(1);
    final SignedBlockAndState blockAndState2 = peerStorage.chainUpdater().advanceChain(2);
    final SignedBlockAndState blockAndState3 = peerStorage.chainUpdater().advanceChain(3);
    peerStorage.chainUpdater().updateBestBlock(blockAndState3);

    final List<BlobsSidecar> response = requestBlobsSideCars(peer, UInt64.ONE, UInt64.valueOf(2));

    assertThat(response).hasSize(2);
    assertThat(response.get(0).getBeaconBlockRoot())
        .isEqualTo(blockAndState2.getBlock().getParentRoot());
    assertThat(response.get(1).getBeaconBlockRoot())
        .isEqualTo(blockAndState3.getBlock().getParentRoot());
  }

  @Test
  public void requestBlobsSidecarsByRangeAfterPeerDisconnected() throws Exception {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalEip4844());

    peerStorage.chainUpdater().advanceChain(1);
    peerStorage.chainUpdater().advanceChain(2);
    final SignedBlockAndState blockAndState3 = peerStorage.chainUpdater().advanceChain(3);
    peerStorage.chainUpdater().updateBestBlock(blockAndState3);

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));

    final List<BlobsSidecar> blobsSidecars = new ArrayList<>();

    final SafeFuture<Void> res =
        peer.requestBlobsSidecarsByRange(
            UInt64.ONE, UInt64.valueOf(2), RpcResponseListener.from(blobsSidecars::add));

    waitFor(() -> assertThat(res).isDone());

    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blobsSidecars).isEmpty();
  }

  @Test
  public void requestBlobsSidecarsByRangeAfterPeerDisconnectedImmediately() {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalEip4844());

    peerStorage.chainUpdater().advanceChain(1);
    peerStorage.chainUpdater().advanceChain(2);
    final SignedBlockAndState blockAndState3 = peerStorage.chainUpdater().advanceChain(3);
    peerStorage.chainUpdater().updateBestBlock(blockAndState3);

    peer.disconnectImmediately(Optional.empty(), false);

    final List<BlobsSidecar> blobsSidecars = new ArrayList<>();

    final SafeFuture<Void> res =
        peer.requestBlobsSidecarsByRange(
            UInt64.ONE, UInt64.valueOf(2), RpcResponseListener.from(blobsSidecars::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  private List<BlobsSidecar> requestBlobsSideCars(
      final Eth2Peer peer, UInt64 startSlot, UInt64 count)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<BlobsSidecar> blobsSidecars = new ArrayList<>();
    waitFor(
        peer.requestBlobsSidecarsByRange(
            startSlot, count, RpcResponseListener.from(blobsSidecars::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return blobsSidecars;
  }
}
