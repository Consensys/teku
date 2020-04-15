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

import static tech.pegasys.artemis.pow.Eth1DataManager.getCacheRangeLowerBound;
import static tech.pegasys.artemis.pow.Eth1DataManager.hasBeenApproximately;
import static tech.pegasys.artemis.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_ETH1_BLOCK;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.pow.event.CacheEth1BlockEvent;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.TimeProvider;
import tech.pegasys.artemis.util.time.channels.TimeTickChannel;

public class Eth1DataCache implements TimeTickChannel {

  private final TimeProvider timeProvider;
  private volatile Optional<UnsignedLong> genesisTime = Optional.empty();

  private final NavigableMap<UnsignedLong, Eth1Data> eth1ChainCache = new ConcurrentSkipListMap<>();
  private volatile UnsignedLong currentVotingPeriodStartTime;

  public Eth1DataCache(EventBus eventBus, TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    eventBus.register(this);
  }

  public void startBeaconChainMode(BeaconState headState) {
    this.genesisTime = Optional.of(headState.getGenesis_time());
    this.currentVotingPeriodStartTime = getVotingPeriodStartTime(headState.getSlot());
    this.onSlot(headState.getSlot());
  }

  @Subscribe
  public void onCacheEth1BlockEvent(CacheEth1BlockEvent cacheEth1BlockEvent) {
    eth1ChainCache.put(
        cacheEth1BlockEvent.getBlockTimestamp(), createEth1Data(cacheEth1BlockEvent));
  }

  @Override
  public void onTick(Date date) {
    if (genesisTime.isPresent()
        || !hasBeenApproximately(SECONDS_PER_ETH1_BLOCK, timeProvider.getTimeInSeconds())) {
      return;
    }
    prune();
  }

  // Called by BeaconChainController not the event bus to ensure we process slot events in sync
  public void onSlot(UnsignedLong slot) {
    if (genesisTime.isEmpty()) {
      return;
    }

    UnsignedLong voting_period_start_time = getVotingPeriodStartTime(slot);

    if (voting_period_start_time.equals(currentVotingPeriodStartTime)) {
      return;
    }

    currentVotingPeriodStartTime = voting_period_start_time;
    prune();
  }

  public Eth1Data get_eth1_vote(BeaconState state) {
    NavigableMap<UnsignedLong, Eth1Data> votesToConsider = getVotesToConsider();
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

  private NavigableMap<UnsignedLong, Eth1Data> getVotesToConsider() {
    return eth1ChainCache.subMap(getSpecRangeLowerBound(), true, getSpecRangeUpperBound(), true);
  }

  private void prune() {
    while (!eth1ChainCache.isEmpty() && isBlockTooOld(eth1ChainCache.firstKey())) {
      eth1ChainCache.remove(eth1ChainCache.firstKey());
    }
  }

  private UnsignedLong getVotingPeriodStartTime(UnsignedLong slot) {
    UnsignedLong eth1VotingPeriodStartSlot =
        slot.minus(slot.mod(UnsignedLong.valueOf(EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH)));
    return computeTimeAtSlot(eth1VotingPeriodStartSlot);
  }

  UnsignedLong getSpecRangeLowerBound() {
    return secondsBeforeCurrentVotingPeriodStartTime(
        ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK).times(UnsignedLong.valueOf(2)));
  }

  UnsignedLong getSpecRangeUpperBound() {
    return secondsBeforeCurrentVotingPeriodStartTime(
        ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK));
  }

  private UnsignedLong secondsBeforeCurrentVotingPeriodStartTime(
      final UnsignedLong valueToSubtract) {
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

  private boolean isBlockTooOld(UnsignedLong blockTimestamp) {
    if (genesisTime.isPresent()) {
      return blockTimestamp.compareTo(getSpecRangeLowerBound()) < 0;
    } else {
      return blockTimestamp.compareTo(getCacheRangeLowerBound(timeProvider.getTimeInSeconds())) < 0;
    }
  }

  public static Eth1Data createEth1Data(CacheEth1BlockEvent eth1BlockEvent) {
    return new Eth1Data(
        eth1BlockEvent.getDepositRoot(),
        eth1BlockEvent.getDepositCount(),
        eth1BlockEvent.getBlockHash());
  }

  @VisibleForTesting
  NavigableMap<UnsignedLong, Eth1Data> getMapForTesting() {
    return Collections.unmodifiableNavigableMap(eth1ChainCache);
  }
}
