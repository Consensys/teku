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

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.pow.event.CacheEth1BlockEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.config.Constants;

public class Eth1DataManagerTest {

  private final EventBus eventBus = new EventBus();
  private final UnsignedLong genesisTime = UnsignedLong.ZERO;

  static {
    Constants.SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(10);
    Constants.ETH1_FOLLOW_DISTANCE = UnsignedLong.valueOf(100);
    Constants.SLOTS_PER_ETH1_VOTING_PERIOD = 10;
    Constants.SECONDS_PER_SLOT = 10;
  }

  // RANGE_CONSTANT = 1000
  private final UnsignedLong RANGE_CONSTANT =
      Constants.SECONDS_PER_ETH1_BLOCK.times(Constants.ETH1_FOLLOW_DISTANCE);
  private Eth1DataManager eth1DataManager;

  @BeforeEach
  void setUp() {
    BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getGenesis_time()).thenReturn(genesisTime);
    eth1DataManager = new Eth1DataManager(beaconState, eventBus);
  }

  @Test
  void majorityVoteWins() {
    UnsignedLong slot = UnsignedLong.valueOf(200);
    UnsignedLong currentTime = slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT));
    eventBus.post(new SlotEvent(slot));

    CacheEth1BlockEvent eth1BlockEvent1 =
        new CacheEth1BlockEvent(
            UnsignedLong.ZERO,
            Bytes32.fromHexString("0x1111"),
            currentTime.minus(RANGE_CONSTANT),
            Bytes32.fromHexString("0x2222"),
            UnsignedLong.valueOf(10L));
    Eth1Data eth1Data1 = Eth1DataManager.getEth1Data(eth1BlockEvent1);

    CacheEth1BlockEvent eth1BlockEvent2 =
        new CacheEth1BlockEvent(
            UnsignedLong.ZERO,
            Bytes32.fromHexString("0x3333"),
            currentTime.minus(RANGE_CONSTANT),
            Bytes32.fromHexString("0x4444"),
            UnsignedLong.valueOf(10L));
    Eth1Data eth1Data2 = Eth1DataManager.getEth1Data(eth1BlockEvent2);

    eventBus.post(eth1BlockEvent1);
    eventBus.post(eth1BlockEvent2);

    SSZList<Eth1Data> eth1DataVotes =
        new SSZList<>(List.of(eth1Data1, eth1Data2, eth1Data2), 10, Eth1Data.class);
    BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    assertThat(eth1DataManager.get_eth1_vote(beaconState)).isEqualTo(eth1Data2);
  }

  @Test
  void oldVoteDoesNotCount() {
    UnsignedLong slot = UnsignedLong.valueOf(300);
    UnsignedLong currentTime = slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT));
    eventBus.post(new SlotEvent(slot));

    CacheEth1BlockEvent eth1BlockEvent1 =
        new CacheEth1BlockEvent(
            UnsignedLong.ZERO,
            Bytes32.fromHexString("0x1111"),
            currentTime.minus(RANGE_CONSTANT),
            Bytes32.fromHexString("0x2222"),
            UnsignedLong.valueOf(10L));
    Eth1Data eth1Data1 = Eth1DataManager.getEth1Data(eth1BlockEvent1);

    CacheEth1BlockEvent eth1BlockEvent2 =
        new CacheEth1BlockEvent(
            UnsignedLong.ZERO,
            Bytes32.fromHexString("0x3333"),
            currentTime.minus(RANGE_CONSTANT.times(UnsignedLong.valueOf(2).plus(UnsignedLong.ONE))),
            Bytes32.fromHexString("0x4444"),
            UnsignedLong.valueOf(10L));
    Eth1Data eth1Data2 = Eth1DataManager.getEth1Data(eth1BlockEvent2);

    eventBus.post(eth1BlockEvent1);
    eventBus.post(eth1BlockEvent2);

    SSZList<Eth1Data> eth1DataVotes =
        new SSZList<>(List.of(eth1Data1, eth1Data2, eth1Data2), 10, Eth1Data.class);
    BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    assertThat(eth1DataManager.get_eth1_vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void tooRecentVoteDoesNotCount() {
    UnsignedLong slot = UnsignedLong.valueOf(300);
    UnsignedLong currentTime = slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT));
    eventBus.post(new SlotEvent(slot));

    CacheEth1BlockEvent eth1BlockEvent1 =
        new CacheEth1BlockEvent(
            UnsignedLong.ZERO,
            Bytes32.fromHexString("0x1111"),
            currentTime.minus(RANGE_CONSTANT),
            Bytes32.fromHexString("0x2222"),
            UnsignedLong.valueOf(10L));
    Eth1Data eth1Data1 = Eth1DataManager.getEth1Data(eth1BlockEvent1);

    CacheEth1BlockEvent eth1BlockEvent2 =
        new CacheEth1BlockEvent(
            UnsignedLong.ZERO,
            Bytes32.fromHexString("0x3333"),
            currentTime.minus(RANGE_CONSTANT.minus(UnsignedLong.ONE)),
            Bytes32.fromHexString("0x4444"),
            UnsignedLong.valueOf(10L));
    Eth1Data eth1Data2 = Eth1DataManager.getEth1Data(eth1BlockEvent2);

    eventBus.post(eth1BlockEvent1);
    eventBus.post(eth1BlockEvent2);

    SSZList<Eth1Data> eth1DataVotes =
        new SSZList<>(List.of(eth1Data1, eth1Data2, eth1Data2), 10, Eth1Data.class);
    BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    assertThat(eth1DataManager.get_eth1_vote(beaconState)).isEqualTo(eth1Data1);
  }
}
