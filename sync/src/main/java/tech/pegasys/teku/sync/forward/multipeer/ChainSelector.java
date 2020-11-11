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
   * @param syncInProgress whether or not an existing sync is already in progress.
   * @return the sync target or empty if no sync is required
   */
  public Optional<TargetChain> selectTargetChain(final boolean syncInProgress) {
    final UInt64 minimumSlot =
        getMinimumSlotForSuitableTargetChain(recentChainData, syncInProgress);
    return availableChains
        .streamChains()
        .filter(chain -> chain.getChainHead().getSlot().isGreaterThan(minimumSlot))
        .findFirst();
  }
}
