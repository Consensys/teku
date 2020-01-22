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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;

import java.time.Instant;
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

public class Eth1DataCacheTest {

  private final EventBus eventBus = new EventBus();
  private final UnsignedLong genesisTime = UnsignedLong.valueOf(1000000);

  static {
    Constants.SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(11);
    Constants.ETH1_FOLLOW_DISTANCE = UnsignedLong.valueOf(100);
    SLOTS_PER_ETH1_VOTING_PERIOD = 12;
    Constants.SECONDS_PER_SLOT = 8;
  }

  // RANGE_CONSTANT = 1000
  private final UnsignedLong RANGE_CONSTANT =
      Constants.SECONDS_PER_ETH1_BLOCK.times(Constants.ETH1_FOLLOW_DISTANCE);
  private Eth1DataCache eth1DataCache;
  private BeaconState genesisState;

  @BeforeEach
  void setUp() {
    genesisState = mock(BeaconState.class);
    when(genesisState.getGenesis_time()).thenReturn(genesisTime);
    when(genesisState.getSlot()).thenReturn(UnsignedLong.ZERO);
    eth1DataCache = new Eth1DataCache(eventBus);
  }

  @Test
  void majorityVoteWins() {
    eth1DataCache.startBeaconChainMode(genesisState);
    UnsignedLong slot = UnsignedLong.valueOf(3).times(RANGE_CONSTANT);
    UnsignedLong currentTime = genesisTime.plus(slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT)));
    eventBus.post(new SlotEvent(slot));

    CacheEth1BlockEvent cacheEth1BlockEvent1 = createRandomCacheEth1BlockEvent(
            currentTime.minus(RANGE_CONSTANT)
    );
    CacheEth1BlockEvent cacheEth1BlockEvent2 = createRandomCacheEth1BlockEvent(
            currentTime.minus(RANGE_CONSTANT).minus(UnsignedLong.ONE)
    );

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
    UnsignedLong slot = UnsignedLong.valueOf(3).times(RANGE_CONSTANT);
    UnsignedLong currentTime = genesisTime.plus(slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT)));
    eventBus.post(new SlotEvent(slot));

    CacheEth1BlockEvent cacheEth1BlockEvent1 = createRandomCacheEth1BlockEvent(
            currentTime.minus(RANGE_CONSTANT)
    );
    CacheEth1BlockEvent cacheEth1BlockEvent2 = createRandomCacheEth1BlockEvent(
            currentTime.minus(RANGE_CONSTANT).minus(UnsignedLong.ONE)
    );

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
    UnsignedLong slot = UnsignedLong.valueOf(300);
    UnsignedLong currentTime = genesisTime.plus(slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT)));
    eventBus.post(new SlotEvent(slot));

    CacheEth1BlockEvent cacheEth1BlockEvent1 = createRandomCacheEth1BlockEvent(
            currentTime.minus(RANGE_CONSTANT.times(UnsignedLong.valueOf(2)))
    );
    CacheEth1BlockEvent cacheEth1BlockEvent2 = createRandomCacheEth1BlockEvent(
            currentTime.minus(RANGE_CONSTANT.times(UnsignedLong.valueOf(2).plus(UnsignedLong.ONE)))
    );

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
    UnsignedLong slot = UnsignedLong.valueOf(300);
    UnsignedLong currentTime = genesisTime.plus(slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT)));
    eventBus.post(new SlotEvent(slot));

    CacheEth1BlockEvent cacheEth1BlockEvent1 = createRandomCacheEth1BlockEvent(
            currentTime.minus(RANGE_CONSTANT)
    );
    CacheEth1BlockEvent cacheEth1BlockEvent2 = createRandomCacheEth1BlockEvent(
            currentTime.minus(RANGE_CONSTANT.minus(UnsignedLong.ONE))
    );

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
  void pruneBeforeGenesis() {
    UnsignedLong currentTime = UnsignedLong.valueOf(Instant.now().getEpochSecond());
    UnsignedLong cacheRangeLowerBound = Eth1DataManager.getCacheRangeLowerBound();

    CacheEth1BlockEvent cacheEth1BlockEvent1 = createRandomCacheEth1BlockEvent(currentTime);
    CacheEth1BlockEvent cacheEth1BlockEvent2 = createRandomCacheEth1BlockEvent(cacheRangeLowerBound.minus(UnsignedLong.ONE));

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);
    eventBus.post(new Date(Constants.SECONDS_PER_ETH1_BLOCK.longValue() * 1000));

    Eth1Data eth1Data1 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent1);

    Waiter.waitFor(() -> assertThat(eth1DataCache.getMapForTesting().values().size() == 1));

    assertThat(eth1DataCache.getMapForTesting().values()).containsExactly(eth1Data1);
  }

  @Test
  void pruneAfterGenesis() {
    eth1DataCache.startBeaconChainMode(genesisState);
    UnsignedLong slotAtNextVotingPeriodStart = UnsignedLong.valueOf(SLOTS_PER_ETH1_VOTING_PERIOD);
    UnsignedLong timeAtNextVotingPeriodStart = genesisTime.plus(slotAtNextVotingPeriodStart.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT)));

    UnsignedLong specRangeLowerBound = eth1DataCache.getSpecRangeLowerBound();

    CacheEth1BlockEvent cacheEth1BlockEvent1 = createRandomCacheEth1BlockEvent(timeAtNextVotingPeriodStart.plus(UnsignedLong.ONE));
    CacheEth1BlockEvent cacheEth1BlockEvent2 = createRandomCacheEth1BlockEvent(specRangeLowerBound.minus(UnsignedLong.ONE));
    CacheEth1BlockEvent cacheEth1BlockEvent3 = createRandomCacheEth1BlockEvent(specRangeLowerBound.minus(UnsignedLong.valueOf(2)));

    eventBus.post(cacheEth1BlockEvent1);
    eventBus.post(cacheEth1BlockEvent2);
    eventBus.post(cacheEth1BlockEvent3);

    eventBus.post(new SlotEvent(slotAtNextVotingPeriodStart));

    Eth1Data eth1Data1 = Eth1DataCache.createEth1Data(cacheEth1BlockEvent1);

    Waiter.waitFor(() -> assertThat(eth1DataCache.getMapForTesting().values().size() == 1));

    assertThat(eth1DataCache.getMapForTesting().values()).containsExactly(eth1Data1);
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
