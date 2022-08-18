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

package tech.pegasys.teku.beacon.pow.api;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;

public class Eth1DataCachePeriodCalculator {

  public static UInt64 calculateEth1DataCacheDurationPriorToFollowDistance(
      final SpecConfig config) {
    // Worst case we're in the very last moment of the current slot
    long cacheDurationSeconds = config.getSecondsPerSlot();

    // Worst case this slot is at the very end of the current voting period
    final long secondsPerEth1VotingPeriod =
        ((long) config.getEpochsPerEth1VotingPeriod())
            * config.getSlotsPerEpoch()
            * config.getSecondsPerSlot();
    cacheDurationSeconds += secondsPerEth1VotingPeriod;

    // We need 2 * ETH1_FOLLOW_DISTANCE prior to that but this assumes our current time is from a
    // block already ETH1_FOLLOW_DISTANCE behind head.
    cacheDurationSeconds +=
        ((long) config.getSecondsPerEth1Block()) * config.getEth1FollowDistance().longValue();

    // And we want to be able to create blocks for at least the past epoch
    cacheDurationSeconds += ((long) config.getSlotsPerEpoch()) * config.getSecondsPerSlot();
    // Some networks have very small ETH1_FOLLOW_DISTANCE but we may need to lookup Eth1Data block
    // height from the current state
    return UInt64.valueOf(cacheDurationSeconds).max(secondsPerEth1VotingPeriod * 2);
  }

  public static UInt64 calculateEth1DataCacheDurationPriorToCurrentTime(final SpecConfig config) {
    // Add in the difference between current time and a block ETH1_FOLLOW_DISTANCE behind.
    return calculateEth1DataCacheDurationPriorToFollowDistance(config)
        .plus(config.getEth1FollowDistance().times(config.getSecondsPerEth1Block()));
  }
}
