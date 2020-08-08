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

package tech.pegasys.teku.pow.api;

import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.teku.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_ETH1_BLOCK;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Eth1DataCachePeriodCalculator {

  public static UInt64 calculateEth1DataCacheDurationPriorToFollowDistance() {
    // Worst case we're in the very last moment of the current slot
    long cacheDurationSeconds = SECONDS_PER_SLOT;

    // Worst case this slot is at the very end of the current voting period
    cacheDurationSeconds += (EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH * SECONDS_PER_SLOT);

    // We need 2 * ETH1_FOLLOW_DISTANCE prior to that but this assumes our current time is from a
    // block already ETH1_FOLLOW_DISTANCE behind head.
    cacheDurationSeconds += SECONDS_PER_ETH1_BLOCK.longValue() * ETH1_FOLLOW_DISTANCE.longValue();

    // And we want to be able to create blocks for at least the past epoch
    cacheDurationSeconds += SLOTS_PER_EPOCH * SECONDS_PER_SLOT;
    return UInt64.valueOf(cacheDurationSeconds);
  }

  public static UInt64 calculateEth1DataCacheDurationPriorToCurrentTime() {
    // Add in the difference between current time and a block ETH1_FOLLOW_DISTANCE behind.
    return calculateEth1DataCacheDurationPriorToFollowDistance()
        .plus(SECONDS_PER_ETH1_BLOCK.times(ETH1_FOLLOW_DISTANCE));
  }
}
