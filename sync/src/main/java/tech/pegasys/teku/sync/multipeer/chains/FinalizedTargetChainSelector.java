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

package tech.pegasys.teku.sync.multipeer.chains;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

public class FinalizedTargetChainSelector {

  private static final int SYNC_THRESHOLD_IN_EPOCHS = 1;
  private final RecentChainData recentChainData;

  public FinalizedTargetChainSelector(final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  public Optional<TargetChain> selectTargetChain(final TargetChains chains) {
    final UInt64 ourFinalizedEpoch = recentChainData.getFinalizedEpoch();
    final UInt64 minimumSlot =
        compute_start_slot_at_epoch(ourFinalizedEpoch.plus(SYNC_THRESHOLD_IN_EPOCHS));
    return chains
        .streamChains()
        .filter(chain -> chain.getChainHead().getSlot().isGreaterThan(minimumSlot))
        .findFirst();
  }
}
