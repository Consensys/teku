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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.pow.event.CacheEth1BlockEvent;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.config.Constants;

public class Eth1DataCacheTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = new EventBus();
  private final BeaconState genesisState = mock(BeaconState.class);

  private final UnsignedLong START_SLOT = UnsignedLong.valueOf(100);
  private final UnsignedLong NEXT_VOTING_PERIOD_SLOT = UnsignedLong.valueOf(102);
  private final UnsignedLong testStartTime = UnsignedLong.valueOf(1000);

  private Eth1DataCache eth1DataCache;

  // Voting Period Start Time
  // = genesisTime + ((slot - (slot % slots_per_eth1_voting_period)) * seconds_per_slot)
  // = 0 + (((100 - (100 % 6)) * 4)
  // = 384
  //
  // Spec Range:
  //    Lower Bound = 384 - (5 * 3 * 2) = 354
  //    Upper Bound = 384 - (5 * 3) = 369

  // Next Voting Period Start Slot = 102
  // Next Voting Period Start Time = 408
  // Next Voting Period Lower Bound = 378

  @BeforeAll
  static void setConstants() {
    Constants.SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(3);
    Constants.ETH1_FOLLOW_DISTANCE = UnsignedLong.valueOf(5);
    Constants.EPOCHS_PER_ETH1_VOTING_PERIOD = 1;
    Constants.SLOTS_PER_EPOCH = 6;
    Constants.SECONDS_PER_SLOT = 4;
  }

  @AfterAll
  static void restoreConstants() {
    Constants.setConstants("minimal");
  }

  @BeforeEach
  void setUp() {
    when(genesisState.getGenesis_time())
        .thenReturn(testStartTime.minus(UnsignedLong.valueOf(1000)));
    when(genesisState.getSlot()).thenReturn(UnsignedLong.ZERO);

    eth1DataCache = new Eth1DataCache(eventBus);
  }

  @Test
  void checkTimeValues() {
    eth1DataCache.startBeaconChainMode(genesisState);
    assertThat(eth1DataCache.getSpecRangeLowerBound(START_SLOT))
        .isEqualByComparingTo(UnsignedLong.valueOf(354));
    assertThat(eth1DataCache.getSpecRangeUpperBound(START_SLOT))
        .isEqualByComparingTo(UnsignedLong.valueOf(369));
    assertThat(eth1DataCache.getSpecRangeLowerBound(NEXT_VOTING_PERIOD_SLOT))
        .isEqualByComparingTo(UnsignedLong.valueOf(378));
  }

  @Test
  void checkTimeValuesStayAboveZero() {
    eth1DataCache.startBeaconChainMode(genesisState);
    assertThat(eth1DataCache.getSpecRangeLowerBound(UnsignedLong.ONE))
        .isEqualByComparingTo(UnsignedLong.ZERO);
    assertThat(eth1DataCache.getSpecRangeUpperBound(UnsignedLong.ONE))
        .isEqualByComparingTo(UnsignedLong.ZERO);
  }

  @Test
  void majorityVoteWins() {
    eth1DataCache.startBeaconChainMode(genesisState);

    // Both Eth1Data timestamp inside the spec range
    CacheEth1BlockEvent cacheEth1BlockEvent1 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(360));
    CacheEth1BlockEvent cacheEth1BlockEvent2 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(361));

    Eth1Data eth1Data1 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent1);
    Eth1Data eth1Data2 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent2);

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);

    SSZMutableList<Eth1Data> eth1DataVotes =
        SSZList.createMutable(List.of(eth1Data1, eth1Data2, eth1Data2), 10, Eth1Data.class);
    BeaconStateImpl beaconState = mock(BeaconStateImpl.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    when(beaconState.getSlot()).thenReturn(START_SLOT);
    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data2);
  }

  @Test
  void smallestDistanceWinsIfNoMajority() {
    eth1DataCache.startBeaconChainMode(genesisState);

    // Both Eth1Data timestamp inside the spec range
    CacheEth1BlockEvent cacheEth1BlockEvent1 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(360));
    CacheEth1BlockEvent cacheEth1BlockEvent2 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(361));

    Eth1Data eth1Data1 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent1);
    Eth1Data eth1Data2 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent2);

    SSZMutableList<Eth1Data> eth1DataVotes =
        SSZList.createMutable(List.of(eth1Data1, eth1Data2), 10, Eth1Data.class);
    BeaconStateImpl beaconState = mock(BeaconStateImpl.class);
    when(beaconState.getSlot()).thenReturn(START_SLOT);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);

    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void oldVoteDoesNotCount() {
    eth1DataCache.startBeaconChainMode(genesisState);

    // Eth1Data inside the range
    CacheEth1BlockEvent cacheEth1BlockEvent1 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(360));
    // Eth1Data timestamp lower than the lower bound
    CacheEth1BlockEvent cacheEth1BlockEvent2 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(352));

    Eth1Data eth1Data1 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent1);
    Eth1Data eth1Data2 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent2);

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);

    SSZMutableList<Eth1Data> eth1DataVotes =
        SSZList.createMutable(List.of(eth1Data1, eth1Data2, eth1Data2), 10, Eth1Data.class);
    BeaconStateImpl beaconState = mock(BeaconStateImpl.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    when(beaconState.getSlot()).thenReturn(START_SLOT);
    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void tooRecentVoteDoesNotCount() {
    eth1DataCache.startBeaconChainMode(genesisState);

    // Eth1Data inside the range
    CacheEth1BlockEvent cacheEth1BlockEvent1 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(360));
    // Eth1Data timestamp greater than the upper bound
    CacheEth1BlockEvent cacheEth1BlockEvent2 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(372));

    Eth1Data eth1Data1 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent1);
    Eth1Data eth1Data2 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent2);

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);

    SSZMutableList<Eth1Data> eth1DataVotes =
        SSZList.createMutable(List.of(eth1Data1, eth1Data2, eth1Data2), 10, Eth1Data.class);
    BeaconStateImpl beaconState = mock(BeaconStateImpl.class);
    when(beaconState.getSlot()).thenReturn(START_SLOT);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void noValidVotesInThisPeriod_eth1ChainLive() {
    eth1DataCache.startBeaconChainMode(genesisState);

    // Both Eth1Data timestamp inside the spec range
    CacheEth1BlockEvent cacheEth1BlockEvent1 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(359));
    CacheEth1BlockEvent cacheEth1BlockEvent2 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(360));

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);

    SSZMutableList<Eth1Data> eth1DataVotes = SSZList.createMutable(List.of(), 10, Eth1Data.class);
    BeaconStateImpl beaconState = mock(BeaconStateImpl.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    when(beaconState.getSlot()).thenReturn(START_SLOT);

    // The most recent Eth1Data in getVotesToConsider wins
    Eth1Data eth1Data2 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent2);
    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data2);
  }

  @Test
  void noValidVotesInThisPeriod_eth1ChainNotLive() {
    eth1DataCache.startBeaconChainMode(genesisState);

    Eth1Data eth1Data = dataStructureUtil.randomEth1Data();

    SSZMutableList<Eth1Data> eth1DataVotes =
        SSZList.createMutable(List.of(eth1Data), 10, Eth1Data.class);
    BeaconStateImpl beaconState = mock(BeaconStateImpl.class);
    when(beaconState.getSlot()).thenReturn(START_SLOT);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    when(beaconState.getEth1_data()).thenReturn(eth1Data);
    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data);
  }

  @Test
  void shouldPruneOldBlocksWhenNewerOnesReceived() {
    final UnsignedLong cacheDuration = eth1DataCache.getCacheDuration();
    final CacheEth1BlockEvent oldBlock = createRandomCacheEth1BlockEvent(UnsignedLong.ZERO);
    final CacheEth1BlockEvent newBlock =
        createRandomCacheEth1BlockEvent(
            oldBlock.getBlockTimestamp().plus(cacheDuration).plus(UnsignedLong.ONE));
    final CacheEth1BlockEvent newerBlock =
        createRandomCacheEth1BlockEvent(newBlock.getBlockTimestamp().plus(cacheDuration));

    eventBus.post(oldBlock);
    assertThat(eth1DataCache.size()).isEqualTo(1);

    // Next block causes the first to be pruned.
    eventBus.post(newBlock);
    assertThat(eth1DataCache.size()).isEqualTo(1);

    // Third block is close enough to the second that they are both kept
    eventBus.post(newerBlock);
    assertThat(eth1DataCache.size()).isEqualTo(2);
  }

  private CacheEth1BlockEvent createRandomCacheEth1BlockEvent(UnsignedLong timestamp) {
    return new CacheEth1BlockEvent(
        dataStructureUtil.randomUnsignedLong(),
        dataStructureUtil.randomBytes32(),
        timestamp,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUnsignedLong());
  }
}
