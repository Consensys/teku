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

package tech.pegasys.teku.sync;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.storage.client.RecentChainData;

public class CommonAncestor {
  private static final Logger LOG = LogManager.getLogger();
  private static final int MINIMUM_VIABLE_SLOTS = 10000;
  private static final int OPTIMISTIC_HISTORY_LENGTH = 3000;
  private final RecentChainData storageClient;

  public CommonAncestor(final RecentChainData storageClient) {
    this.storageClient = storageClient;
  }

  public SafeFuture<UInt64> getCommonAncestor(
      final Eth2Peer peer, final PeerStatus status, final UInt64 firstNonFinalSlot) {
    final UInt64 localHead = storageClient.getHeadSlot();
    // more than 10000 blocks behind, try to find a better starting slot if we have non finalized
    // data
    if (localHead.isGreaterThanOrEqualTo(firstNonFinalSlot.plus(MINIMUM_VIABLE_SLOTS))
        && firstNonFinalSlot.plus(MINIMUM_VIABLE_SLOTS).isLessThan(status.getHeadSlot())) {
      final UInt64 localNonFinalisedSlotCount = localHead.minus(firstNonFinalSlot);
      final UInt64 count = UInt64.valueOf(20);
      final UInt64 freq = UInt64.valueOf(50);
      final UInt64 firstRequestedSlot =
          localNonFinalisedSlotCount.isGreaterThanOrEqualTo(UInt64.valueOf(MINIMUM_VIABLE_SLOTS))
              ? localHead.minus(OPTIMISTIC_HISTORY_LENGTH)
              : firstNonFinalSlot;
      final UInt64 lastSlot = firstRequestedSlot.plus(count.times(freq));
      LOG.debug(
          "Local head slot {}. Have {} non finalized slots, "
              + "will sample ahead every {} slots from {} to {}. Peer head is {}",
          localHead,
          localNonFinalisedSlotCount,
          freq,
          firstRequestedSlot,
          lastSlot,
          status.getHeadSlot());

      final BestBlockListener blockListener =
          new BestBlockListener(storageClient, firstNonFinalSlot);

      final PeerSyncBlockRequest request =
          new PeerSyncBlockRequest(SafeFuture.COMPLETE, lastSlot, blockListener);

      return peer.requestBlocksByRange(firstRequestedSlot, count, freq, request)
          .thenApply(__ -> blockListener.getBestSlot());
    }

    return SafeFuture.completedFuture(firstNonFinalSlot);
  }

  private static class BestBlockListener implements ResponseStreamListener<SignedBeaconBlock> {
    private final RecentChainData storageClient;
    private UInt64 bestSlot;

    BestBlockListener(final RecentChainData storageClient, final UInt64 bestSlot) {
      this.storageClient = storageClient;
      this.bestSlot = bestSlot;
    }

    private UInt64 getBestSlot() {
      return bestSlot;
    }

    @Override
    public SafeFuture<?> onResponse(final SignedBeaconBlock block) {
      if (storageClient.containsBlock(block.getRoot())) {
        bestSlot = bestSlot.max(block.getSlot());
      }

      return SafeFuture.COMPLETE;
    }
  }
}
