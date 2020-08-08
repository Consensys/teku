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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;

public class Eth1DataCacheTest {

  private static final UInt64 CACHE_DURATION = UInt64.valueOf(10_000);

  // Note: The slot and genesis time won't line up with the voting period start and end
  // This is semi-deliberate - if you use the Eth1VotingPeriod instance it all works,
  // if you duplicate the logic to do the math or depend on some property of that math, it will fail
  // It also saves us doing a bunch of math in this test...
  private static final UInt64 VOTING_PERIOD_START = UInt64.valueOf(50_000);
  private static final UInt64 VOTING_PERIOD_END = UInt64.valueOf(55_000);
  private static final UInt64 SLOT = UInt64.valueOf(125);
  private static final UInt64 GENESIS_TIME = UInt64.valueOf(77777);
  public static final UInt64 IN_RANGE_TIMESTAMP_1 = UInt64.valueOf(51_000);
  public static final UInt64 IN_RANGE_TIMESTAMP_2 = UInt64.valueOf(52_000);
  public static final UInt64 IN_RANGE_TIMESTAMP_3 = UInt64.valueOf(53_000);
  private static final int STATE_DEPOSIT_COUNT = 10;

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Eth1Data stateEth1Data = createEth1Data(STATE_DEPOSIT_COUNT);
  private final Eth1VotingPeriod eth1VotingPeriod = mock(Eth1VotingPeriod.class);

  private Eth1DataCache eth1DataCache;

  @BeforeEach
  void setUp() {
    when(eth1VotingPeriod.getCacheDurationInSeconds()).thenReturn(CACHE_DURATION);
    when(eth1VotingPeriod.getSpecRangeLowerBound(SLOT, GENESIS_TIME))
        .thenReturn(VOTING_PERIOD_START);
    when(eth1VotingPeriod.getSpecRangeUpperBound(SLOT, GENESIS_TIME)).thenReturn(VOTING_PERIOD_END);
    eth1DataCache = new Eth1DataCache(eth1VotingPeriod);
  }

  // Add tests for eth1 block with no votes
  // - Same block as a deposit
  // - Received out of order (always after deposits though)
  // - Multiple in a row
  // - Voting for a block with no deposits

  @Test
  void shouldUseDepositDataFromPreviousBlockWhenNoDepositBlockAddedAsLatestBlock() {
    final Eth1Data eth1Data = createEth1Data(STATE_DEPOSIT_COUNT);
    final Bytes32 emptyBlockHash = dataStructureUtil.randomBytes32();
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_1, eth1Data);
    eth1DataCache.onEth1Block(emptyBlockHash, IN_RANGE_TIMESTAMP_2);

    final Eth1Data eth1Vote = eth1DataCache.getEth1Vote(createBeaconStateWithVotes());
    assertThat(eth1Vote).isEqualTo(eth1Data.withBlockHash(emptyBlockHash));
  }

  @Test
  void shouldUseDepositFromPreviousBlockWhenNoDepositBlockAddedBetweenDeposits() {
    final Bytes32 emptyBlockHash = dataStructureUtil.randomBytes32();
    final Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT + 1);
    final Eth1Data eth1Data2 = createEth1Data(eth1Data1.getDeposit_count());
    final Eth1Data emptyBlockData = eth1Data1.withBlockHash(emptyBlockHash);
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_1, eth1Data1);
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_3, eth1Data2);
    eth1DataCache.onEth1Block(emptyBlockHash, IN_RANGE_TIMESTAMP_2);

    final Eth1Data eth1Vote = eth1DataCache.getEth1Vote(createBeaconStateWithVotes(emptyBlockData));
    assertThat(eth1Vote).isEqualTo(emptyBlockData);
  }

  @Test
  void shouldIgnoreEmptyBlocksWithNoEarlierDeposits() {
    final Bytes32 emptyBlockHash = dataStructureUtil.randomBytes32();
    eth1DataCache.onEth1Block(emptyBlockHash, IN_RANGE_TIMESTAMP_1);

    assertThat(eth1DataCache.size()).isZero();
  }

  @Test
  void shouldAcceptEmptyBlocksWhenAllPreviousDepositsHaveBeenPruned() {
    final Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    final Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);
    final Bytes32 emptyBlockHash = dataStructureUtil.randomBytes32();
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_1.minus(CACHE_DURATION), eth1Data1);

    // New block would normally prune the first one, but doesn't because we preserve one item
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_2, eth1Data2);
    assertThat(eth1DataCache.size()).isEqualTo(2);

    // Then register an empty block within the voting period but prior to the latest deposit
    eth1DataCache.onEth1Block(emptyBlockHash, IN_RANGE_TIMESTAMP_1);

    // And it should be recorded
    final Eth1Data emptyBlockData = eth1Data1.withBlockHash(emptyBlockHash);
    final BeaconState beaconState = createBeaconStateWithVotes(emptyBlockData);
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(emptyBlockData);
  }

  @Test
  void majorityVoteWins() {
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);

    // Both Eth1Data timestamp inside the spec range
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_1, eth1Data1);
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_2, eth1Data2);

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, eth1Data2, eth1Data2);
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data2);
  }

  @Test
  void smallestDistanceWinsIfNoMajority() {
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, eth1Data2);

    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_1, eth1Data1);
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_2, eth1Data2);

    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void oldVoteDoesNotCount() {
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);

    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_1, eth1Data1);
    eth1DataCache.onBlockWithDeposit(VOTING_PERIOD_START.minus(ONE), eth1Data2);

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, eth1Data2, eth1Data2);
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void tooRecentVoteDoesNotCount() {
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);

    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_1, eth1Data1);
    eth1DataCache.onBlockWithDeposit(VOTING_PERIOD_END.plus(ONE), eth1Data2);

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, eth1Data2, eth1Data2);
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void voteWithFewerDepositsThanCurrentStateDoesNotCount() {
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT - 1);

    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_1, eth1Data1);
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_2, eth1Data2);

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, eth1Data2, eth1Data2);
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void noValidVotesInThisPeriod_eth1ChainLive() {
    final Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    final Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_1, eth1Data1);
    eth1DataCache.onBlockWithDeposit(IN_RANGE_TIMESTAMP_2, eth1Data2);

    BeaconState beaconState = createBeaconStateWithVotes();

    // The most recent Eth1Data in getVotesToConsider wins
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data2);
  }

  @Test
  void noValidVotesInThisPeriod_eth1ChainNotLive() {
    BeaconState beaconState = createBeaconStateWithVotes(createEth1Data(STATE_DEPOSIT_COUNT));
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(stateEth1Data);
  }

  @Test
  void shouldPruneOldBlocksWhenNewerOnesReceived() {
    final UInt64 olderBlockTimestamp = ZERO;
    final UInt64 oldBlockTimestamp = olderBlockTimestamp.plus(ONE);
    final UInt64 newBlockTimestamp = oldBlockTimestamp.plus(CACHE_DURATION).plus(ONE);
    final UInt64 newerBlockTimestamp = newBlockTimestamp.plus(CACHE_DURATION);

    eth1DataCache.onBlockWithDeposit(olderBlockTimestamp, createEth1Data(STATE_DEPOSIT_COUNT));
    eth1DataCache.onBlockWithDeposit(oldBlockTimestamp, createEth1Data(STATE_DEPOSIT_COUNT));
    assertThat(eth1DataCache.size()).isEqualTo(2);

    // Push both old blocks out of the cache period
    eth1DataCache.onBlockWithDeposit(newBlockTimestamp, createEth1Data(STATE_DEPOSIT_COUNT));
    // But only the oldest block gets pruned because we need at least one event prior to the cache
    // period so empty blocks right at the start of the period can lookup the
    assertThat(eth1DataCache.size()).isEqualTo(2);

    // Third block is close enough to the second that they are both kept.
    // newBlockTimestamp is now exactly at the start of the cache period so we can remove oldBlock
    eth1DataCache.onBlockWithDeposit(newerBlockTimestamp, createEth1Data(STATE_DEPOSIT_COUNT));
    assertThat(eth1DataCache.size()).isEqualTo(2);
  }

  private BeaconState createBeaconStateWithVotes(final Eth1Data... votes) {
    SSZMutableList<Eth1Data> eth1DataVotes =
        SSZList.createMutable(List.of(votes), votes.length, Eth1Data.class);
    final BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getSlot()).thenReturn(SLOT);
    when(beaconState.getGenesis_time()).thenReturn(GENESIS_TIME);
    when(beaconState.getEth1_data_votes()).thenReturn(eth1DataVotes);
    when(beaconState.getEth1_data()).thenReturn(stateEth1Data);
    return beaconState;
  }

  private Eth1Data createEth1Data(final int depositCount) {
    return createEth1Data(UInt64.valueOf(depositCount));
  }

  private Eth1Data createEth1Data(final UInt64 depositCount) {
    return new Eth1Data(
        dataStructureUtil.randomBytes32(), depositCount, dataStructureUtil.randomBytes32());
  }
}
