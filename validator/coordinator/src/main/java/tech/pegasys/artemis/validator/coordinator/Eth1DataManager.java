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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.pow.event.CacheEth1BlockEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.config.Constants;

public class Eth1DataManager {

  private final EventBus eventBus;
  private final UnsignedLong genesisTime;

  private NavigableMap<UnsignedLong, Eth1Data> eth1ChainCache = new ConcurrentSkipListMap<>();
  private final UnsignedLong RANGE_CONSTANT =
      Constants.SECONDS_PER_ETH1_BLOCK.times(Constants.ETH1_FOLLOW_DISTANCE);
  private volatile UnsignedLong currentVotingPeriodStartTime;

  static Eth1Data getEth1Data(CacheEth1BlockEvent cacheEth1BlockEvent) {
    return new Eth1Data(
        cacheEth1BlockEvent.getDepositRoot(),
        cacheEth1BlockEvent.getDepositCount(),
        cacheEth1BlockEvent.getBlockHash());
  }

  public Eth1DataManager(BeaconState genesisState, EventBus eventBus) {
    this.eventBus = eventBus;
    this.genesisTime = genesisState.getGenesis_time();
    this.eventBus.register(this);
  }

  @Subscribe
  public void onCacheEth1BlockEvent(CacheEth1BlockEvent cacheEth1BlockEvent) {
    eth1ChainCache.put(cacheEth1BlockEvent.getBlockTimestamp(), getEth1Data(cacheEth1BlockEvent));
  }

  @Subscribe
  public void onSlot(SlotEvent slotEvent) {
    UnsignedLong slot = slotEvent.getSlot();
    UnsignedLong voting_period_start_time = voting_period_start_time(slot);

    if (voting_period_start_time.equals(currentVotingPeriodStartTime)) {
      return;
    }

    currentVotingPeriodStartTime = voting_period_start_time;
    prune(voting_period_start_time);
  }

  public Eth1Data get_eth1_vote(BeaconState state) {
    Collection<Eth1Data> votesToConsider = getVotesToConsider();
    List<Eth1Data> validVotes =
        state.getEth1_data_votes().stream()
            .filter(votesToConsider::contains)
            .collect(Collectors.toList());

    return validVotes.stream()
        .max(
            Comparator.comparing(vote -> Collections.frequency(validVotes, vote))
                .thenComparing(vote -> -validVotes.indexOf(vote)))
        .get();
  }

  private Collection<Eth1Data> getVotesToConsider() {
    NavigableMap<UnsignedLong, Eth1Data> consideredSubMap =
        eth1ChainCache.subMap(
            currentVotingPeriodStartTime.minus(RANGE_CONSTANT.times(UnsignedLong.valueOf(2))),
            true,
            currentVotingPeriodStartTime.minus(RANGE_CONSTANT),
            true);

    return consideredSubMap.values();
  }

  private void prune(UnsignedLong periodStart) {
    if (eth1ChainCache.isEmpty()) {
      return;
    }

    while (isBlockTooOld(eth1ChainCache.firstKey(), periodStart)) {
      eth1ChainCache.remove(eth1ChainCache.firstKey());
    }
  }

  private UnsignedLong compute_time_at_slot(UnsignedLong slot) {
    return genesisTime.plus(slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT)));
  }

  private UnsignedLong voting_period_start_time(UnsignedLong slot) {
    UnsignedLong eth1_voting_period_start_slot =
        slot.minus(slot.mod(UnsignedLong.valueOf(Constants.SLOTS_PER_ETH1_VOTING_PERIOD)));
    return compute_time_at_slot(eth1_voting_period_start_slot);
  }

  private boolean isBlockTooOld(UnsignedLong blockTimestamp, UnsignedLong periodStart) {
    return blockTimestamp.compareTo(
            periodStart.minus(RANGE_CONSTANT.times(UnsignedLong.valueOf(2))))
        < 0;
  }
}
