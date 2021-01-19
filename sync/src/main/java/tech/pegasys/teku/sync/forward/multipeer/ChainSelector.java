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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChains;

public class ChainSelector {

  private static final int SYNC_THRESHOLD_IN_EPOCHS = 1;
  private static final int SYNC_THRESHOLD_IN_SLOTS = SYNC_THRESHOLD_IN_EPOCHS * SLOTS_PER_EPOCH;

  private final TargetChains availableChains;
  private final RecentChainData recentChainData;

  public ChainSelector(final RecentChainData recentChainData, final TargetChains availableChains) {
    this.availableChains = availableChains;
    this.recentChainData = recentChainData;
  }

  private UInt64 getMinimumSlotForSuitableTargetChain(
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
    final UInt64 minimumSlot =
        getMinimumSlotForSuitableTargetChain(recentChainData, currentSyncTarget.isPresent());
    return availableChains
        .streamChains()
        .filter(chain -> chain.getChainHead().getSlot().isGreaterThan(minimumSlot))
        .filter(chain -> !recentChainData.containsBlock(chain.getChainHead().getBlockRoot()))
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
}
