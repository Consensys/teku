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
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_ETH1_BLOCK;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;

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
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.TimeProvider;

public class Eth1DataCache {

  private final EventBus eventBus;
  private final TimeProvider timeProvider;
  private volatile Optional<UnsignedLong> genesisTime = Optional.empty();

  private final NavigableMap<UnsignedLong, Eth1Data> eth1ChainCache = new ConcurrentSkipListMap<>();
  private volatile UnsignedLong currentVotingPeriodStartTime;

  public Eth1DataCache(EventBus eventBus, TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  public void startBeaconChainMode(BeaconState genesisState) {
    this.genesisTime = Optional.of(genesisState.getGenesis_time());
    this.currentVotingPeriodStartTime = getVotingPeriodStartTime(genesisState.getSlot());
    this.onSlot(new SlotEvent(genesisState.getSlot()));
  }

  @Subscribe
  public void onCacheEth1BlockEvent(CacheEth1BlockEvent cacheEth1BlockEvent) {
    eth1ChainCache.put(
        cacheEth1BlockEvent.getBlockTimestamp(), createEth1Data(cacheEth1BlockEvent));
  }

  @Subscribe
  public void onTick(Date date) {
    if (genesisTime.isPresent()
        || !hasBeenApproximately(SECONDS_PER_ETH1_BLOCK, timeProvider.getTimeInSeconds())) {
      return;
    }
    prune();
  }

  // Called by ValidatorCoordinator not the event bus to ensure we process slot events in sync
  public void onSlot(SlotEvent slotEvent) {
    if (genesisTime.isEmpty()) {
      return;
    }

    UnsignedLong slot = slotEvent.getSlot();
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
    if (eth1ChainCache.isEmpty()) return;

    while (isBlockTooOld(eth1ChainCache.firstKey())) {
      eth1ChainCache.remove(eth1ChainCache.firstKey());
    }
  }

  private UnsignedLong getVotingPeriodStartTime(UnsignedLong slot) {
    UnsignedLong eth1VotingPeriodStartSlot =
        slot.minus(slot.mod(UnsignedLong.valueOf(SLOTS_PER_ETH1_VOTING_PERIOD)));
    return computeTimeAtSlot(eth1VotingPeriodStartSlot);
  }

  UnsignedLong getSpecRangeLowerBound() {
    return currentVotingPeriodStartTime.minus(
        ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK).times(UnsignedLong.valueOf(2)));
  }

  UnsignedLong getSpecRangeUpperBound() {
    return currentVotingPeriodStartTime.minus(ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK));
  }

  private UnsignedLong computeTimeAtSlot(UnsignedLong slot) {
    if (genesisTime.isEmpty())
      throw new RuntimeException("computeTimeAtSlot called without genesisTime being set");
    return genesisTime.get().plus(slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT)));
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
