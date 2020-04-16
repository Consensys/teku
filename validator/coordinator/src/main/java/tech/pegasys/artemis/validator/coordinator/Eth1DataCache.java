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

package tech.pegasys.artemis.validator.coordinator;

import static tech.pegasys.artemis.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_ETH1_BLOCK;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.pow.event.CacheEth1BlockEvent;
import tech.pegasys.artemis.util.config.Constants;

public class Eth1DataCache {

  private final UnsignedLong cacheDuration;
  private volatile Optional<UnsignedLong> genesisTime = Optional.empty();

  private final NavigableMap<UnsignedLong, Eth1Data> eth1ChainCache = new ConcurrentSkipListMap<>();

  public Eth1DataCache(EventBus eventBus) {
    eventBus.register(this);
    cacheDuration = calculateCacheDuration();
  }

  public void startBeaconChainMode(BeaconState headState) {
    this.genesisTime = Optional.of(headState.getGenesis_time());
  }

  @Subscribe
  public void onCacheEth1BlockEvent(CacheEth1BlockEvent cacheEth1BlockEvent) {
    final UnsignedLong latestBlockTimestamp = cacheEth1BlockEvent.getBlockTimestamp();
    eth1ChainCache.put(latestBlockTimestamp, createEth1Data(cacheEth1BlockEvent));
    prune(latestBlockTimestamp);
  }

  public Eth1Data get_eth1_vote(BeaconState state) {
    NavigableMap<UnsignedLong, Eth1Data> votesToConsider = getVotesToConsider(state.getSlot());
    Map<Eth1Data, Eth1Vote> validVotes = new HashMap<>();

    int i = 0;
    for (Eth1Data eth1Data : state.getEth1_data_votes()) {
      if (!votesToConsider.containsValue(eth1Data)) {
        continue;
      }

      final int currentIndex = i;
      Eth1Vote vote = validVotes.computeIfAbsent(eth1Data, key -> new Eth1Vote(currentIndex));
      vote.incrementVotes();
      i++;
    }

    Eth1Data defaultVote =
        votesToConsider.isEmpty() ? state.getEth1_data() : votesToConsider.lastEntry().getValue();

    Optional<Eth1Data> vote =
        validVotes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);

    return vote.orElse(defaultVote);
  }

  private NavigableMap<UnsignedLong, Eth1Data> getVotesToConsider(final UnsignedLong slot) {
    return eth1ChainCache.subMap(
        getSpecRangeLowerBound(slot), true, getSpecRangeUpperBound(slot), true);
  }

  private UnsignedLong calculateCacheDuration() {
    // Worst case we're in the very last moment of the current slot
    long cacheDurationSeconds = SECONDS_PER_SLOT;

    // Worst case this slot is at the very end of the current voting period
    cacheDurationSeconds += (EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH * SECONDS_PER_SLOT);

    // We need 2 * ETH1_FOLLOW_DISTANCE prior to that but the blocks we get from Eth1DataManager are
    // already ETH1_FOLLOW_DISTANCE behind head and the current time is taken from that.
    cacheDurationSeconds += SECONDS_PER_ETH1_BLOCK.longValue() * ETH1_FOLLOW_DISTANCE.longValue();

    // And we want to be able to create blocks for at least the past epoch
    cacheDurationSeconds += SLOTS_PER_EPOCH * SECONDS_PER_SLOT;
    return UnsignedLong.valueOf(cacheDurationSeconds);
  }

  private void prune(final UnsignedLong latestBlockTimestamp) {
    while (!eth1ChainCache.isEmpty()) {
      final UnsignedLong earliestBlockTimestamp = eth1ChainCache.firstKey();
      if (earliestBlockTimestamp.plus(cacheDuration).compareTo(latestBlockTimestamp) >= 0) {
        break;
      }
      eth1ChainCache.remove(earliestBlockTimestamp);
    }
  }

  private UnsignedLong getVotingPeriodStartTime(UnsignedLong slot) {
    UnsignedLong eth1VotingPeriodStartSlot =
        slot.minus(slot.mod(UnsignedLong.valueOf(EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH)));
    return computeTimeAtSlot(eth1VotingPeriodStartSlot);
  }

  UnsignedLong getSpecRangeLowerBound(final UnsignedLong slot) {
    return secondsBeforeCurrentVotingPeriodStartTime(
        slot, ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK).times(UnsignedLong.valueOf(2)));
  }

  UnsignedLong getSpecRangeUpperBound(final UnsignedLong slot) {
    return secondsBeforeCurrentVotingPeriodStartTime(
        slot, ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK));
  }

  private UnsignedLong secondsBeforeCurrentVotingPeriodStartTime(
      final UnsignedLong slot, final UnsignedLong valueToSubtract) {
    final UnsignedLong currentVotingPeriodStartTime = getVotingPeriodStartTime(slot);
    if (currentVotingPeriodStartTime.compareTo(valueToSubtract) > 0) {
      return currentVotingPeriodStartTime.minus(valueToSubtract);
    } else {
      return UnsignedLong.ZERO;
    }
  }

  private UnsignedLong computeTimeAtSlot(UnsignedLong slot) {
    return genesisTime
        .orElseThrow(
            () -> new RuntimeException("computeTimeAtSlot called without genesisTime being set"))
        .plus(slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT)));
  }

  public static Eth1Data createEth1Data(CacheEth1BlockEvent eth1BlockEvent) {
    return new Eth1Data(
        eth1BlockEvent.getDepositRoot(),
        eth1BlockEvent.getDepositCount(),
        eth1BlockEvent.getBlockHash());
  }

  UnsignedLong getCacheDuration() {
    return cacheDuration;
  }

  int size() {
    return eth1ChainCache.size();
  }
}
