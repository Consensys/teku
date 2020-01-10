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
    NavigableMap<UnsignedLong, Eth1Data> votesToConsider = getVotesToConsider();
    Map<Eth1Data, Eth1Vote> validVotes = new HashMap<>();

    int i = 0;
    for (Eth1Data eth1Data : state.getEth1_data_votes()) {
      if (!votesToConsider.containsValue(eth1Data)) {
        continue;
      }

      int finalI = i;
      Eth1Vote vote =
          validVotes.computeIfAbsent(
              eth1Data,
              key -> {
                Eth1Vote newVote = new Eth1Vote();
                newVote.setIndex(finalI);
                return newVote;
              });
      vote.incrementVotes();
      i++;
    }

    Eth1Data defaultVote =
        !votesToConsider.isEmpty() ? votesToConsider.lastEntry().getValue() : state.getEth1_data();

    Optional<Eth1Data> vote =
        validVotes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);

    return vote.orElse(defaultVote);
  }

  public static class Eth1Vote implements Comparable<Eth1Vote> {

    private int vote = 0;
    private int index = -1;

    public void incrementVotes() {
      vote++;
    }

    public void setIndex(int i) {
      index = i;
    }

    @Override
    public int compareTo(Eth1Vote eth1Vote) {
      if (this.vote > eth1Vote.vote) {
        return 1;
      } else if (this.vote < eth1Vote.vote) {
        return -1;
      } else {
        if (this.index < eth1Vote.index) {
          return 1;
        } else if (this.index > eth1Vote.index) {
          return -1;
        } else {
          return 0;
        }
      }
    }
  }

  private NavigableMap<UnsignedLong, Eth1Data> getVotesToConsider() {
    NavigableMap<UnsignedLong, Eth1Data> consideredSubMap =
        eth1ChainCache.subMap(
            currentVotingPeriodStartTime.minus(RANGE_CONSTANT.times(UnsignedLong.valueOf(2))),
            true,
            currentVotingPeriodStartTime.minus(RANGE_CONSTANT),
            true);

    return consideredSubMap;
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
