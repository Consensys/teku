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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.pow.Eth1DataManager;
import tech.pegasys.artemis.pow.event.CacheEth1BlockEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.Waiter;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.StubTimeProvider;

public class Eth1DataCacheTest {

  private final EventBus eventBus = new EventBus();
  private final BeaconState genesisState = mock(BeaconState.class);

  static {
    Constants.SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(3);
    Constants.ETH1_FOLLOW_DISTANCE = UnsignedLong.valueOf(5);
    SLOTS_PER_ETH1_VOTING_PERIOD = 6;
    Constants.SECONDS_PER_SLOT = 4;
  }

  private final UnsignedLong START_SLOT = UnsignedLong.valueOf(100);
  private final UnsignedLong NEXT_VOTING_PERIOD_SLOT = UnsignedLong.valueOf(102);
  private final UnsignedLong testStartTime = UnsignedLong.valueOf(1000);

  private StubTimeProvider timeProvider;
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

  @BeforeEach
  void setUp() {
    when(genesisState.getGenesis_time())
        .thenReturn(testStartTime.minus(UnsignedLong.valueOf(1000)));
    when(genesisState.getSlot()).thenReturn(UnsignedLong.ZERO);

    timeProvider = StubTimeProvider.withTimeInSeconds(testStartTime);
    eth1DataCache = new Eth1DataCache(eventBus, timeProvider);
  }

  @Test
  void checkTimeValues() {
    eth1DataCache.startBeaconChainMode(genesisState);
    eventBus.post(new SlotEvent(START_SLOT));
    assertThat(eth1DataCache.getSpecRangeLowerBound())
        .isEqualByComparingTo(UnsignedLong.valueOf(354));
    assertThat(eth1DataCache.getSpecRangeUpperBound())
        .isEqualByComparingTo(UnsignedLong.valueOf(369));
    eventBus.post(new SlotEvent(NEXT_VOTING_PERIOD_SLOT));
    assertThat(eth1DataCache.getSpecRangeLowerBound())
        .isEqualByComparingTo(UnsignedLong.valueOf(378));
  }

  @Test
  void majorityVoteWins() {
    eth1DataCache.startBeaconChainMode(genesisState);
    eventBus.post(new SlotEvent(START_SLOT));

    // Both Eth1Data timestamp inside the spec range
    CacheEth1BlockEvent cacheEth1BlockEvent1 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(360));
    CacheEth1BlockEvent cacheEth1BlockEvent2 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(361));

    Eth1Data eth1Data1 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent1);
    Eth1Data eth1Data2 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent2);

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);

    SSZList<Eth1Data> eth1DataVotes =
        new SSZList<>(List.of(eth1Data1, eth1Data2, eth1Data2), 10, Eth1Data.class);
    BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data2);
  }

  @Test
  void smallestDistanceWinsIfNoMajority() {
    eth1DataCache.startBeaconChainMode(genesisState);
    eventBus.post(new SlotEvent(START_SLOT));

    // Both Eth1Data timestamp inside the spec range
    CacheEth1BlockEvent cacheEth1BlockEvent1 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(360));
    CacheEth1BlockEvent cacheEth1BlockEvent2 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(361));

    Eth1Data eth1Data1 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent1);
    Eth1Data eth1Data2 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent2);

    SSZList<Eth1Data> eth1DataVotes =
        new SSZList<>(List.of(eth1Data1, eth1Data2), 10, Eth1Data.class);
    BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);

    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void oldVoteDoesNotCount() {
    eth1DataCache.startBeaconChainMode(genesisState);
    eventBus.post(new SlotEvent(START_SLOT));

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

    SSZList<Eth1Data> eth1DataVotes =
        new SSZList<>(List.of(eth1Data1, eth1Data2, eth1Data2), 10, Eth1Data.class);
    BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void tooRecentVoteDoesNotCount() {
    eth1DataCache.startBeaconChainMode(genesisState);
    eventBus.post(new SlotEvent(START_SLOT));

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

    SSZList<Eth1Data> eth1DataVotes =
        new SSZList<>(List.of(eth1Data1, eth1Data2, eth1Data2), 10, Eth1Data.class);
    BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void noValidVotesInThisPeriod_eth1ChainLive() {
    eth1DataCache.startBeaconChainMode(genesisState);
    eventBus.post(new SlotEvent(START_SLOT));

    // Both Eth1Data timestamp inside the spec range
    CacheEth1BlockEvent cacheEth1BlockEvent1 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(359));
    CacheEth1BlockEvent cacheEth1BlockEvent2 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(360));

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);

    SSZList<Eth1Data> eth1DataVotes = new SSZList<>(List.of(), 10, Eth1Data.class);
    BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);

    // The most recent Eth1Data in getVotesToConsider wins
    Eth1Data eth1Data2 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent2);
    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data2);
  }

  @Test
  void noValidVotesInThisPeriod_eth1ChainNotLive() {
    eth1DataCache.startBeaconChainMode(genesisState);
    eventBus.post(new SlotEvent(START_SLOT));

    Eth1Data eth1Data = DataStructureUtil.randomEth1Data(10);

    SSZList<Eth1Data> eth1DataVotes = new SSZList<>(List.of(eth1Data), 10, Eth1Data.class);
    BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    when(beaconState.getEth1_data()).thenReturn(eth1Data);
    assertThat(eth1DataCache.get_eth1_vote(beaconState)).isEqualTo(eth1Data);
  }

  @Test
  void pruneBeforeGenesis() {
    CacheEth1BlockEvent cacheEth1BlockEvent1 =
        createRandomCacheEth1BlockEvent(Eth1DataManager.getCacheMidRangeTimestamp(testStartTime));
    CacheEth1BlockEvent cacheEth1BlockEvent2 =
        createRandomCacheEth1BlockEvent(
            Eth1DataManager.getCacheRangeLowerBound(testStartTime).minus(UnsignedLong.ONE));

    // Make sure hasBeenApproximately returns true
    timeProvider.advanceTimeBySeconds(2);

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);
    eventBus.post(new Date());

    Eth1Data eth1Data1 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent1);

    Waiter.waitFor(() -> assertThat(eth1DataCache.getMapForTesting().values().size()).isEqualTo(1));

    assertThat(eth1DataCache.getMapForTesting().values()).containsExactly(eth1Data1);
  }

  @Test
  void pruneAfterGenesis() {
    eth1DataCache.startBeaconChainMode(genesisState);
    eventBus.post(new SlotEvent(START_SLOT));

    // First two Eth1Data timestamps inside the spec range for this voting period
    CacheEth1BlockEvent cacheEth1BlockEvent1 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(359));
    CacheEth1BlockEvent cacheEth1BlockEvent2 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(360));

    // This Eth1Data timestamp inside the spec range for the next voting period
    CacheEth1BlockEvent cacheEth1BlockEvent3 =
        createRandomCacheEth1BlockEvent(UnsignedLong.valueOf(379));

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);
    eventBus.post(cacheEth1BlockEvent3);

    eventBus.post(new SlotEvent(NEXT_VOTING_PERIOD_SLOT));

    Eth1Data eth1Data3 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent3);

    Waiter.waitFor(() -> assertThat(eth1DataCache.getMapForTesting().values().size()).isEqualTo(1));

    assertThat(eth1DataCache.getMapForTesting().values()).containsExactly(eth1Data3);
  }

  @Test
  void onSlotBeingCalled_withoutGenesisTimeBeingSet() {
    assertDoesNotThrow(() -> eth1DataCache.onSlot(new SlotEvent(START_SLOT)));
  }

  private CacheEth1BlockEvent createRandomCacheEth1BlockEvent(UnsignedLong timestamp) {
    long seed = 0;
    return new CacheEth1BlockEvent(
        DataStructureUtil.randomUnsignedLong(++seed),
        DataStructureUtil.randomBytes32(++seed),
        timestamp,
        DataStructureUtil.randomBytes32(++seed),
        DataStructureUtil.randomUnsignedLong(++seed));
  }
}
