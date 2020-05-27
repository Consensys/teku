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

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.state.BeaconState;

public class Eth1DataCache {
  private static final Logger LOG = LogManager.getLogger();

  private final UnsignedLong cacheDuration;
  private final Eth1VotingPeriod eth1VotingPeriod;

  private final NavigableMap<UnsignedLong, Eth1Data> eth1ChainCache = new ConcurrentSkipListMap<>();

  public Eth1DataCache(final Eth1VotingPeriod eth1VotingPeriod) {
    this.eth1VotingPeriod = eth1VotingPeriod;
    cacheDuration = eth1VotingPeriod.getCacheDurationInSeconds();
  }

  public void onBlockWithDeposit(final UnsignedLong blockTimestamp, final Eth1Data eth1Data) {
    eth1ChainCache.put(blockTimestamp, eth1Data);
    prune(blockTimestamp);
  }

  public void onEth1Block(final Bytes32 blockHash, final UnsignedLong blockTimestamp) {
    final Entry<UnsignedLong, Eth1Data> previousBlock = eth1ChainCache.floorEntry(blockTimestamp);
    if (previousBlock == null) {
      // This block is either before any deposits so will never be voted for
      // or before the cache period so would be immediately pruned anyway.
      LOG.debug(
          "Not adding eth1 block {} with timestamp {} to cache because it is before all current entries",
          blockHash,
          blockTimestamp);
      return;
    }
    final Eth1Data data = previousBlock.getValue();
    eth1ChainCache.put(blockTimestamp, data.withBlockHash(blockHash));
    prune(blockTimestamp);
  }

  public Eth1Data getEth1Vote(BeaconState state) {
    NavigableMap<UnsignedLong, Eth1Data> votesToConsider =
        getVotesToConsider(state.getSlot(), state.getGenesis_time());
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

  private NavigableMap<UnsignedLong, Eth1Data> getVotesToConsider(
      final UnsignedLong slot, final UnsignedLong genesisTime) {
    return eth1ChainCache.subMap(
        eth1VotingPeriod.getSpecRangeLowerBound(slot, genesisTime),
        true,
        eth1VotingPeriod.getSpecRangeUpperBound(slot, genesisTime),
        true);
  }

  private void prune(final UnsignedLong latestBlockTimestamp) {
    if (latestBlockTimestamp.compareTo(cacheDuration) <= 0 || eth1ChainCache.isEmpty()) {
      // Keep everything
      return;
    }
    final UnsignedLong earliestBlockTimestampToKeep = latestBlockTimestamp.minus(cacheDuration);
    // Make sure we have at least one entry prior to the cache period so that if we get an empty
    // block before any deposit in the cached period, we can look back and get the deposit info
    final UnsignedLong earliestKeyToKeep = eth1ChainCache.floorKey(earliestBlockTimestampToKeep);
    if (earliestKeyToKeep == null) {
      return;
    }
    eth1ChainCache.headMap(earliestKeyToKeep, false).clear();
  }

  int size() {
    return eth1ChainCache.size();
  }
}
