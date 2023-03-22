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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.storage.client.RecentChainData;

public class CommonAncestor {
  private static final Logger LOG = LogManager.getLogger();
  private final RecentChainData recentChainData;

  static final UInt64 OPTIMISTIC_HISTORY_LENGTH = UInt64.valueOf(3000);
  // prysm allows a maximum range of 1000 blocks (endSlot - startSlot) due to database limitations
  static final UInt64 BLOCK_COUNT = UInt64.valueOf(100);

  public CommonAncestor(final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  public SafeFuture<UInt64> getCommonAncestor(
      final SyncSource peer, final UInt64 firstNonFinalSlot, final UInt64 peerHeadSlot) {
    final UInt64 ourHeadSlot = recentChainData.getHeadSlot();
    final UInt64 lowestHeadSlot = ourHeadSlot.min(peerHeadSlot);
    if (lowestHeadSlot.isLessThan(firstNonFinalSlot.plus(OPTIMISTIC_HISTORY_LENGTH))) {
      return SafeFuture.completedFuture(firstNonFinalSlot);
    }
    final UInt64 localNonFinalisedSlotCount = lowestHeadSlot.minus(firstNonFinalSlot);
    final UInt64 firstRequestedSlot = lowestHeadSlot.minus(OPTIMISTIC_HISTORY_LENGTH);
    final UInt64 lastSlot = firstRequestedSlot.plus(BLOCK_COUNT);

    LOG.debug(
        "Local head slot {}. Have {} non finalized slots, "
            + "will sample ahead from {} to {}. Peer head is {}",
        ourHeadSlot,
        localNonFinalisedSlotCount,
        firstRequestedSlot,
        lastSlot,
        peerHeadSlot);

    final BestBlockListener blockResponseListener =
        new BestBlockListener(recentChainData, firstNonFinalSlot);
    final PeerSyncBlockListener blockListener =
        new PeerSyncBlockListener(
            SafeFuture.COMPLETE, firstRequestedSlot, BLOCK_COUNT, blockResponseListener);

    return peer.requestBlocksByRange(firstRequestedSlot, BLOCK_COUNT, blockListener)
        .thenApply(__ -> blockResponseListener.getBestSlot());
  }

  private static class BestBlockListener implements RpcResponseListener<SignedBeaconBlock> {
    private final RecentChainData recentChainData;
    private UInt64 bestSlot;

    BestBlockListener(final RecentChainData recentChainData, final UInt64 bestSlot) {
      this.recentChainData = recentChainData;
      this.bestSlot = bestSlot;
    }

    private UInt64 getBestSlot() {
      return bestSlot;
    }

    @Override
    public SafeFuture<?> onResponse(final SignedBeaconBlock block) {
      if (recentChainData.containsBlock(block.getRoot())) {
        bestSlot = bestSlot.max(block.getSlot());
      }

      return SafeFuture.COMPLETE;
    }
  }
}
