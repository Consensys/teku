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

package tech.pegasys.teku.validator.coordinator;

import static tech.pegasys.teku.pow.api.Eth1DataCachePeriodCalculator.calculateEth1DataCacheDurationPriorToFollowDistance;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.teku.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_ETH1_BLOCK;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.teku.util.config.Constants;

public class Eth1VotingPeriod {

  private final UnsignedLong cacheDuration;

  public Eth1VotingPeriod() {
    cacheDuration = calculateEth1DataCacheDurationPriorToFollowDistance();
  }

  public UnsignedLong getSpecRangeLowerBound(
      final UnsignedLong slot, final UnsignedLong genesisTime) {
    return secondsBeforeCurrentVotingPeriodStartTime(
        slot,
        genesisTime,
        ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK).times(UnsignedLong.valueOf(2)));
  }

  public UnsignedLong getSpecRangeUpperBound(
      final UnsignedLong slot, final UnsignedLong genesisTime) {
    return secondsBeforeCurrentVotingPeriodStartTime(
        slot, genesisTime, ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK));
  }

  private UnsignedLong secondsBeforeCurrentVotingPeriodStartTime(
      final UnsignedLong slot, final UnsignedLong genesisTime, final UnsignedLong valueToSubtract) {
    final UnsignedLong currentVotingPeriodStartTime = getVotingPeriodStartTime(slot, genesisTime);
    if (currentVotingPeriodStartTime.compareTo(valueToSubtract) > 0) {
      return currentVotingPeriodStartTime.minus(valueToSubtract);
    } else {
      return UnsignedLong.ZERO;
    }
  }

  private UnsignedLong getVotingPeriodStartTime(
      final UnsignedLong slot, final UnsignedLong genesisTime) {
    final UnsignedLong eth1VotingPeriodStartSlot =
        slot.minus(slot.mod(UnsignedLong.valueOf(EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH)));
    return computeTimeAtSlot(eth1VotingPeriodStartSlot, genesisTime);
  }

  private UnsignedLong computeTimeAtSlot(final UnsignedLong slot, final UnsignedLong genesisTime) {
    return genesisTime.plus(slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT)));
  }

  public UnsignedLong getCacheDurationInSeconds() {
    return cacheDuration;
  }
}
