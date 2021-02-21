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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecProvider;

public class Eth1VotingPeriod {

  private final UInt64 cacheDuration;
  private final SpecProvider specProvider;

  public Eth1VotingPeriod(final SpecProvider specProvider) {
    this.specProvider = specProvider;
    cacheDuration = calculateEth1DataCacheDurationPriorToFollowDistance();
  }

  public UInt64 getSpecRangeLowerBound(final UInt64 slot, final UInt64 genesisTime) {
    return secondsBeforeCurrentVotingPeriodStartTime(
        slot,
        genesisTime,
        specProvider
            .getEth1FollowDistance(slot)
            .times(specProvider.getSecondsPerEth1Block(slot))
            .times(2));
  }

  public UInt64 getSpecRangeUpperBound(final UInt64 slot, final UInt64 genesisTime) {
    return secondsBeforeCurrentVotingPeriodStartTime(
        slot,
        genesisTime,
        specProvider.getEth1FollowDistance(slot).times(specProvider.getSecondsPerEth1Block(slot)));
  }

  private UInt64 secondsBeforeCurrentVotingPeriodStartTime(
      final UInt64 slot, final UInt64 genesisTime, final UInt64 valueToSubtract) {
    final UInt64 currentVotingPeriodStartTime = getVotingPeriodStartTime(slot, genesisTime);
    if (currentVotingPeriodStartTime.isGreaterThan(valueToSubtract)) {
      return currentVotingPeriodStartTime.minus(valueToSubtract);
    } else {
      return UInt64.ZERO;
    }
  }

  private UInt64 getVotingPeriodStartTime(final UInt64 slot, final UInt64 genesisTime) {
    final UInt64 eth1VotingPeriodStartSlot =
        slot.minus(
            slot.mod(
                specProvider.getEpochsPerEth1VotingPeriod(slot)
                    * specProvider.getSlotsPerEpoch(slot)));
    return computeTimeAtSlot(eth1VotingPeriodStartSlot, genesisTime);
  }

  private UInt64 computeTimeAtSlot(final UInt64 slot, final UInt64 genesisTime) {
    return genesisTime.plus(slot.times(specProvider.getSecondsPerSlot(slot)));
  }

  public UInt64 getCacheDurationInSeconds() {
    return cacheDuration;
  }
}
