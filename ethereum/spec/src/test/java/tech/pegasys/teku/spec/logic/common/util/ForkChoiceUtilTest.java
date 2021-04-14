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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.util.RandomChainBuilder;
import tech.pegasys.teku.spec.util.RandomChainBuilderForkChoiceStrategy;

class ForkChoiceUtilTest {

  private static final UInt64 GENESIS_TIME = UInt64.valueOf("1591924193");
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final RandomChainBuilder chainBuilder = new RandomChainBuilder(spec);
  private final RandomChainBuilderForkChoiceStrategy forkChoiceStrategy =
      new RandomChainBuilderForkChoiceStrategy(chainBuilder);

  private final ForkChoiceUtil forkChoiceUtil = spec.getGenesisSpec().getForkChoiceUtil();
  private final UInt64 slot50Time =
      GENESIS_TIME.plus(spec.getGenesisSpecConfig().getSecondsPerSlot() * 50L);

  @Test
  void getAncestors_shouldGetSimpleSequenceOfAncestors() {
    chainBuilder.generateBlocksUpToSlot(10);

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        forkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getChainHead().orElseThrow().getRoot(),
            UInt64.ONE,
            UInt64.ONE,
            UInt64.valueOf(8));

    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(1, 2, 3, 4, 5, 6, 7, 8));
  }

  @Test
  void getAncestors_shouldGetSequenceOfRootsWhenSkipping() {
    chainBuilder.generateBlocksUpToSlot(10);

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        forkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getChainHead().orElseThrow().getRoot(),
            UInt64.ONE,
            UInt64.valueOf(2),
            UInt64.valueOf(4));

    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(1, 3, 5, 7));
  }

  @Test
  void getAncestors_shouldGetSequenceOfRootsWhenStartIsPriorToFinalizedCheckpoint() {
    chainBuilder.generateBlocksUpToSlot(10);
    forkChoiceStrategy.prune(UInt64.valueOf(4));

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        forkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getChainHead().orElseThrow().getRoot(),
            UInt64.ONE,
            UInt64.valueOf(2),
            UInt64.valueOf(4));

    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(5, 7));
  }

  @Test
  void getAncestors_shouldGetSequenceOfRootsWhenEndIsAfterChainHead() {
    chainBuilder.generateBlocksUpToSlot(10);

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        forkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getChainHead().orElseThrow().getRoot(),
            UInt64.valueOf(6),
            UInt64.valueOf(2),
            UInt64.valueOf(20));

    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(6, 8, 10));
  }

  @Test
  void getAncestors_shouldNotIncludeEntryForEmptySlots() {
    chainBuilder.generateBlockAtSlot(3);
    chainBuilder.generateBlockAtSlot(5);

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        forkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getChainHead().orElseThrow().getRoot(),
            UInt64.ZERO,
            UInt64.ONE,
            UInt64.valueOf(10));
    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(0, 3, 5));
  }

  @Test
  void getAncestorsOnFork_shouldIncludeHeadBlockAndExcludeStartSlot() {
    chainBuilder.generateBlocksUpToSlot(10);

    final NavigableMap<UInt64, Bytes32> ancestorsOnFork =
        forkChoiceUtil.getAncestorsOnFork(
            forkChoiceStrategy,
            chainBuilder.getChainHead().orElseThrow().getRoot(),
            UInt64.valueOf(5));
    assertThat(ancestorsOnFork).doesNotContainKey(UInt64.valueOf(5));
  }

  @Test
  void getAncestorsOnFork_shouldNotIncludeHeadBlockWhenItIsAtStartSlot() {
    chainBuilder.generateBlocksUpToSlot(3);

    final SignedBlockAndState headBlock = chainBuilder.getChainHead().orElseThrow();
    final NavigableMap<UInt64, Bytes32> ancestorsOnFork =
        forkChoiceUtil.getAncestorsOnFork(
            forkChoiceStrategy, headBlock.getRoot(), headBlock.getSlot());
    assertThat(ancestorsOnFork).isEmpty();
  }

  @Test
  public void getCurrentSlot_shouldGetZeroAtGenesis() {
    assertThat(forkChoiceUtil.getCurrentSlot(GENESIS_TIME, GENESIS_TIME)).isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getCurrentSlot_shouldGetNonZeroPastGenesis() {

    assertThat(forkChoiceUtil.getCurrentSlot(slot50Time, GENESIS_TIME))
        .isEqualTo(UInt64.valueOf(50L));
  }

  @Test
  public void getCurrentSlot_shouldGetZeroPriorToGenesis() {
    assertThat(forkChoiceUtil.getCurrentSlot(GENESIS_TIME.minus(1), GENESIS_TIME))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getSlotStartTime_shouldGetGenesisTimeForBlockZero() {
    assertThat(forkChoiceUtil.getSlotStartTime(UInt64.ZERO, GENESIS_TIME)).isEqualTo(GENESIS_TIME);
  }

  @Test
  public void getSlotStartTime_shouldGetCorrectTimePastGenesis() {
    assertThat(forkChoiceUtil.getSlotStartTime(UInt64.valueOf(50L), GENESIS_TIME))
        .isEqualTo(slot50Time);
  }

  @Test
  void onTick_shouldExitImmediatelyWhenCurrentTimeIsBeforeGenesisTime() {
    final MutableStore store = mock(MutableStore.class);
    when(store.getGenesisTime()).thenReturn(UInt64.valueOf(3000));
    when(store.getTime()).thenReturn(UInt64.ZERO);
    forkChoiceUtil.onTick(store, UInt64.valueOf(2000));

    verify(store, never()).setTime(any());
  }

  @Test
  void onTick_shouldExitImmediatelyWhenCurrentTimeIsBeforeStoreTime() {
    final MutableStore store = mock(MutableStore.class);
    when(store.getGenesisTime()).thenReturn(UInt64.valueOf(3000));
    when(store.getTime()).thenReturn(UInt64.valueOf(5000));
    forkChoiceUtil.onTick(store, UInt64.valueOf(4000));

    verify(store, never()).setTime(any());
  }

  private Map<UInt64, Bytes32> getRootsForBlocks(final int... blockNumbers) {
    return IntStream.of(blockNumbers)
        .mapToObj(chainBuilder::getBlock)
        .flatMap(Optional::stream)
        .collect(Collectors.toMap(SignedBeaconBlock::getSlot, SignedBeaconBlock::getRoot));
  }
}
