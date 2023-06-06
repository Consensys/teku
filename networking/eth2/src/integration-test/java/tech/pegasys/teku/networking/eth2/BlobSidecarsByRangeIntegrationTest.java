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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;

public class BlobSidecarsByRangeIntegrationTest extends AbstractRpcMethodIntegrationTest {

  @Test
  public void requestBlobSidecars_shouldFailBeforeDenebMilestone() {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalCapella());
    assertThatThrownBy(() -> requestBlobSidecarsByRange(peer))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("BlobSidecarsByRange method is not supported");
  }

  @Test
  public void requestBlobSidecars_shouldReturnEmptyBlobSidecarsOnDenebMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalDeneb());
    final List<BlobSidecar> blobSidecars = requestBlobSidecarsByRange(peer);
    assertThat(blobSidecars).isEmpty();
  }

  @Test
  public void requestBlobSidecars_shouldReturnBlobSidecarsOnDenebMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalDeneb());

    // generate 4 blobs per block
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobs(true);
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState lastBlock = peerStorage.chainUpdater().advanceChainUntil(targetSlot);
    peerStorage.chainUpdater().updateBestBlock(lastBlock);

    // grab expected blobs from storage
    final List<BlobSidecar> expectedBlobSidecars =
        retrieveCanonicalBlobSidecarsFromPeerStorage(UInt64.rangeClosed(UInt64.ONE, targetSlot));

    // call and check
    final List<BlobSidecar> blobSidecars = requestBlobSidecarsByRange(peer);
    assertThat(blobSidecars).containsExactlyInAnyOrderElementsOf(expectedBlobSidecars);
  }

  private List<BlobSidecar> requestBlobSidecarsByRange(final Eth2Peer peer)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<BlobSidecar> blobSidecars = new ArrayList<>();
    waitFor(
        peer.requestBlobSidecarsByRange(
            UInt64.ONE, UInt64.valueOf(10), RpcResponseListener.from(blobSidecars::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return blobSidecars;
  }
}
