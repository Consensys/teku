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

package tech.pegasys.teku.sync.multipeer;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.multipeer.chains.TargetChains;

public class ChainSelector {

  private static final int SYNC_THRESHOLD_IN_EPOCHS = 1;
  private static final int SYNC_THRESHOLD_IN_SLOTS = SYNC_THRESHOLD_IN_EPOCHS * SLOTS_PER_EPOCH;

  private final MinimumSlotCalculator minimumSlotCalculator;

  private ChainSelector(final MinimumSlotCalculator minimumSlotCalculator) {
    this.minimumSlotCalculator = minimumSlotCalculator;
  }

  public static ChainSelector createFinalizedChainSelector(final RecentChainData recentChainData) {
    return new ChainSelector(
        syncInProgress -> getMinimumSlotForFinalizedTargetChain(recentChainData, syncInProgress));
  }

  private static UInt64 getMinimumSlotForFinalizedTargetChain(
      final RecentChainData recentChainData, final boolean syncInProgress) {
    final UInt64 localFinalizedEpoch = recentChainData.getFinalizedEpoch();
    return compute_start_slot_at_epoch(
        syncInProgress ? localFinalizedEpoch : localFinalizedEpoch.plus(SYNC_THRESHOLD_IN_EPOCHS));
  }

  public static ChainSelector createNonfinalizedChainSelector(
      final RecentChainData recentChainData) {
    return new ChainSelector(
        syncInProgress -> getMinimumSlotForNonfinalizedChain(recentChainData, syncInProgress));
  }

  private static UInt64 getMinimumSlotForNonfinalizedChain(
      final RecentChainData recentChainData, final boolean syncInProgress) {
    final UInt64 localHeadSlot = recentChainData.getHeadSlot();
    return syncInProgress ? localHeadSlot : localHeadSlot.plus(SYNC_THRESHOLD_IN_SLOTS);
  }

  /**
   * Select the best chain to sync to out of the supplied available chains. If empty is returned,
   * the node is considered in sync.
   *
   * @param availableChains the chains to select from
   * @param syncInProgress whether or not an existing sync is already in progress.
   * @return the sync target or empty if no sync is required
   */
  public Optional<TargetChain> selectTargetChain(
      TargetChains availableChains, final boolean syncInProgress) {
    final UInt64 minimumSlot =
        minimumSlotCalculator.getMinimumSlotForSuitableTargetChain(syncInProgress);
    return availableChains
        .streamChains()
        .filter(chain -> chain.getChainHead().getSlot().isGreaterThan(minimumSlot))
        .findFirst();
  }

  private interface MinimumSlotCalculator {
    UInt64 getMinimumSlotForSuitableTargetChain(boolean syncInProgress);
  }
}
