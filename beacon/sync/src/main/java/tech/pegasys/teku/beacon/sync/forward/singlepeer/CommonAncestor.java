/*
 * Copyright Consensys Software Inc., 2022
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

import java.util.Optional;
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
  private static final int MAX_ATTEMPTS = 3;
  private static final UInt64 BLOCK_COUNT_PER_ATTEMPT = UInt64.valueOf(10);
  private static final UInt64 SLOTS_TO_JUMP_BACK_ON_EACH_ATTEMPT = UInt64.valueOf(100);

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

    final UInt64 localNonFinalisedSlotCount = lowestHeadSlot.minus(firstNonFinalSlot);

    LOG.debug(
        "Local head slot {}. Have {} non finalized slots, peer head is {}",
        ourHeadSlot,
        localNonFinalisedSlotCount,
        peerHeadSlot);

    return getCommonAncestor(
        peer,
        lowestHeadSlot.minusMinZero(BLOCK_COUNT_PER_ATTEMPT),
        firstNonFinalSlot,
        MAX_ATTEMPTS);
  }

  private SafeFuture<UInt64> getCommonAncestor(
      final SyncSource peer,
      final UInt64 firstRequestedSlot,
      final UInt64 defaultSlot,
      final int remainingAttempts) {
    if (remainingAttempts <= 0 || firstRequestedSlot.isLessThanOrEqualTo(defaultSlot)) {
      return SafeFuture.completedFuture(defaultSlot);
    }

    final UInt64 lastSlot = firstRequestedSlot.plus(BLOCK_COUNT_PER_ATTEMPT);

    LOG.debug("Sampling ahead from {} to {}.", firstRequestedSlot, lastSlot);

    final BestBlockListener blockResponseListener = new BestBlockListener(recentChainData);
    final PeerSyncBlockListener blockListener =
        new PeerSyncBlockListener(
            SafeFuture.COMPLETE,
            firstRequestedSlot,
            BLOCK_COUNT_PER_ATTEMPT,
            blockResponseListener);

    return peer.requestBlocksByRange(firstRequestedSlot, BLOCK_COUNT_PER_ATTEMPT, blockListener)
        .thenCompose(
            __ ->
                blockResponseListener
                    .getBestSlot()
                    .map(SafeFuture::completedFuture)
                    .orElseGet(
                        () ->
                            getCommonAncestor(
                                peer,
                                firstRequestedSlot.minus(SLOTS_TO_JUMP_BACK_ON_EACH_ATTEMPT),
                                defaultSlot,
                                remainingAttempts - 1)));
  }

  private static class BestBlockListener implements RpcResponseListener<SignedBeaconBlock> {
    private final RecentChainData recentChainData;
    private Optional<UInt64> bestSlot;

    BestBlockListener(final RecentChainData recentChainData) {
      this.recentChainData = recentChainData;
      this.bestSlot = Optional.empty();
    }

    private Optional<UInt64> getBestSlot() {
      return bestSlot;
    }

    @Override
    public SafeFuture<?> onResponse(final SignedBeaconBlock block) {
      if (recentChainData.containsBlock(block.getRoot())) {
        bestSlot =
            bestSlot
                .map(uInt64 -> uInt64.max(block.getSlot()))
                .or(() -> Optional.of(block.getSlot()));
      }

      return SafeFuture.COMPLETE;
    }
  }
}
