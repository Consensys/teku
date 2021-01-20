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

package tech.pegasys.teku.sync.forward.multipeer;

import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.Optional;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChains;

public class ChainSelector {

  private static final int SYNC_THRESHOLD_IN_EPOCHS = 1;
  private static final int SYNC_THRESHOLD_IN_SLOTS = SYNC_THRESHOLD_IN_EPOCHS * SLOTS_PER_EPOCH;
  static final int MIN_PENDING_BLOCKS = 5;

  private final TargetChains availableChains;
  private final RecentChainData recentChainData;
  private final TargetChainFilter filter;

  private ChainSelector(
      final RecentChainData recentChainData,
      final TargetChains availableChains,
      final TargetChainFilter filter) {
    this.availableChains = availableChains;
    this.recentChainData = recentChainData;
    this.filter = filter;
  }

  public static ChainSelector createForCanonicalChains(
      final RecentChainData recentChainData, final TargetChains availableChains) {
    return new ChainSelector(
        recentChainData,
        availableChains,
        (currentSyncTarget, chain) ->
            isFarEnoughAheadOfCurrentChain(recentChainData, currentSyncTarget, chain));
  }

  public static ChainSelector createForForkChains(
      final RecentChainData recentChainData,
      final TargetChains availableChains,
      final PendingPool<SignedBeaconBlock> pendingBlocks) {
    return new ChainSelector(
        recentChainData,
        availableChains,
        (currentSyncTarget, candidateChain) ->
            hasEnoughPendingBlocks(pendingBlocks, candidateChain.getChainHead()));
  }

  private static boolean isFarEnoughAheadOfCurrentChain(
      final RecentChainData recentChainData,
      final Optional<TargetChain> currentSyncTarget,
      final TargetChain chain) {
    return chain
        .getChainHead()
        .getSlot()
        .isGreaterThan(
            getMinimumSlotForSuitableTargetChain(recentChainData, currentSyncTarget.isPresent()));
  }

  private static boolean hasEnoughPendingBlocks(
      final PendingPool<SignedBeaconBlock> pendingBlocks, final SlotAndBlockRoot chainHead) {
    int pendingBlockCount = 0;
    Optional<SignedBeaconBlock> block = pendingBlocks.get(chainHead.getBlockRoot());
    while (block.isPresent()) {
      pendingBlockCount++;
      if (pendingBlockCount >= MIN_PENDING_BLOCKS) {
        return true;
      }
      block = pendingBlocks.get(block.get().getParentRoot());
    }
    return false;
  }

  private static UInt64 getMinimumSlotForSuitableTargetChain(
      final RecentChainData recentChainData, final boolean syncInProgress) {
    final UInt64 localHeadSlot = recentChainData.getHeadSlot();
    return syncInProgress ? localHeadSlot : localHeadSlot.plus(SYNC_THRESHOLD_IN_SLOTS);
  }

  /**
   * Select the best chain to sync to out of the supplied available chains. If empty is returned,
   * the node is considered in sync.
   *
   * @param currentSyncTarget the current target chain being synced, or empty if no sync is active
   * @return the sync target or empty if no sync is required
   */
  public Optional<TargetChain> selectTargetChain(final Optional<TargetChain> currentSyncTarget) {
    return availableChains
        .streamChains()
        .filter(chain -> !recentChainData.containsBlock(chain.getChainHead().getBlockRoot()))
        .filter(chain -> filter.isSuitableTarget(currentSyncTarget, chain))
        .filter(
            chain ->
                currentSyncTarget.isEmpty() || isWorthSwitching(chain, currentSyncTarget.get()))
        .findFirst();
  }

  /**
   * Avoids thrashing between different chains with the same number of peers based on which one had
   * the latest block. Sticks with the current sync target unless it is either not available from
   * our pool (possibly switching from finalized to non-finalized or back) or the new chain has more
   * peers (making it more likely to be the canonical chain).
   *
   * @param candidateChain the chain being considered as an option to switch to
   * @param currentTargetChain the current sync target
   * @return true if the candidate chain is worth the potential cost of switching to
   */
  private boolean isWorthSwitching(
      final TargetChain candidateChain, final TargetChain currentTargetChain) {
    return candidateChain.equals(currentTargetChain)
        || !availableChains.containsChain(currentTargetChain)
        || candidateChain.getPeerCount() > currentTargetChain.getPeerCount();
  }

  private interface TargetChainFilter {
    boolean isSuitableTarget(Optional<TargetChain> currentSyncTarget, TargetChain candidateChain);
  }
}
