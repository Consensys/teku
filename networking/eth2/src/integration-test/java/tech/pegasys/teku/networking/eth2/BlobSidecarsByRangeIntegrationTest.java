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
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.generator.ChainBuilder;

public class BlobSidecarsByRangeIntegrationTest extends AbstractRpcMethodIntegrationTest {

  @Test
  public void requestBlobSidecars_shouldFailBeforeDenebMilestone() {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalCapella());
    assertThatThrownBy(() -> requestBlobSidecarsByRange(peer, UInt64.ONE, UInt64.valueOf(10)))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("BlobSidecarsByRange method is not supported");
  }

  @Test
  public void requestBlobSidecars_shouldReturnEmptyBlobSidecarsOnDenebMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalDeneb());
    final List<BlobSidecar> blobSidecars =
        requestBlobSidecarsByRange(peer, UInt64.ONE, UInt64.valueOf(10));
    assertThat(blobSidecars).isEmpty();
  }

  @Test
  public void requestBlobSidecars_shouldReturnCanonicalBlobSidecarsOnDenebMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalDeneb());

    // finalize chain 2 blobs per block
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobs(true);
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobsCount(Optional.of(2));

    final List<SignedBlockAndState> finalizedBlocksAndStates =
        peerStorage
            .chainBuilder()
            .finalizeCurrentChain(Optional.of(peerStorage.chainUpdater().blockOptions));
    finalizedBlocksAndStates.forEach(
        blockAndState -> {
          final List<BlobSidecar> blobSidecars =
              peerStorage.chainBuilder().getBlobSidecars(blockAndState.getRoot());
          peerStorage.chainUpdater().saveBlock(blockAndState, blobSidecars);
          peerStorage.chainUpdater().updateBestBlock(blockAndState);
        });

    final ChainBuilder fork = peerStorage.chainBuilder().fork();

    final Checkpoint finalizedCheckpoint =
        peerStorage.recentChainData().getFinalizedCheckpoint().orElseThrow();
    final UInt64 finalizedSlot =
        finalizedCheckpoint.getEpochStartSlot(peerStorage.recentChainData().getSpec());

    // add 5 extra blocks that will be canonical
    final UInt64 targetSlot = peerStorage.getChainHead().getSlot().plus(5);
    final SignedBlockAndState canonicalHead =
        peerStorage.chainUpdater().advanceChainUntil(targetSlot);

    // generate non canonical blocks and blobs up to the same target slot
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobsCount(Optional.of(4));
    final List<SignedBlockAndState> nonCanonicalBlocksAndStates =
        fork.generateBlocksUpToSlot(targetSlot.intValue(), peerStorage.chainUpdater().blockOptions);

    final List<BlobSidecar> nonCanonicalBlobSidecars = new ArrayList<>();
    nonCanonicalBlocksAndStates.forEach(
        signedBlockAndState -> {
          final List<BlobSidecar> blobSidecars =
              fork.getBlobSidecars(signedBlockAndState.getRoot());
          nonCanonicalBlobSidecars.addAll(blobSidecars);
          peerStorage.chainUpdater().saveBlock(signedBlockAndState, blobSidecars);
        });

    // make sure canonical head is the canonical head
    peerStorage.chainUpdater().updateBestBlock(canonicalHead);

    // make sure we have 2 heads
    assertThat(peerStorage.recentChainData().getChainHeads().size()).isEqualTo(2);

    // lets get blobs starting from 5 slots prior to finalized slot
    final UInt64 startSlot = finalizedSlot.minus(5);

    // grab expected blobs from storage
    final List<BlobSidecar> expectedCanonicalBlobSidecars =
        retrieveCanonicalBlobSidecarsFromPeerStorage(UInt64.rangeClosed(startSlot, targetSlot));

    final UInt64 slotCount = targetSlot.minus(startSlot).increment();
    // call and check
    final List<BlobSidecar> blobSidecars = requestBlobSidecarsByRange(peer, startSlot, slotCount);
    assertThat(blobSidecars).containsExactlyInAnyOrderElementsOf(expectedCanonicalBlobSidecars);
    assertThat(blobSidecars).doesNotContainAnyElementsOf(nonCanonicalBlobSidecars);
  }

  private List<BlobSidecar> requestBlobSidecarsByRange(
      final Eth2Peer peer, final UInt64 from, final UInt64 count)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<BlobSidecar> blobSidecars = new ArrayList<>();
    waitFor(
        peer.requestBlobSidecarsByRange(from, count, RpcResponseListener.from(blobSidecars::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return blobSidecars;
  }
}
