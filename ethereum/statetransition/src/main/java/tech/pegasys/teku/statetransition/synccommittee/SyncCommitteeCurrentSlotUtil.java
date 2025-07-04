/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.synccommittee;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeCurrentSlotUtil {
  private final RecentChainData recentChainData;
  private final Spec spec;
  private final TimeProvider timeProvider;

  public SyncCommitteeCurrentSlotUtil(
      final RecentChainData recentChainData, final Spec spec, final TimeProvider timeProvider) {
    this.recentChainData = recentChainData;
    this.spec = spec;
    this.timeProvider = timeProvider;
  }

  boolean isForCurrentSlot(final UInt64 slot) {
    if (recentChainData.getCurrentSlot().isEmpty()) {
      return false;
    }

    final UInt64 slotMillis = spec.getMillisPerSlot(slot);
    final UInt64 slotStartTimeMillis =
        secondsToMillis(spec.computeTimeAtSlot(slot, recentChainData.getGenesisTime()));
    final UInt64 slotEndTimeMillis = slotStartTimeMillis.plus(slotMillis);
    final UInt64 currentTimeMillis = timeProvider.getTimeInMillis();
    final int maximumGossipClockDisparityMillis =
        spec.getNetworkingConfig().getMaximumGossipClockDisparity();

    return currentTimeMillis.isGreaterThanOrEqualTo(
            slotStartTimeMillis.minusMinZero(maximumGossipClockDisparityMillis))
        && currentTimeMillis.isLessThanOrEqualTo(
            slotEndTimeMillis.plus(maximumGossipClockDisparityMillis));
  }
}
