/*
 * Copyright Consensys Software Inc., 2022
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
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreImpl;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.spec.util.RandomChainBuilder;
import tech.pegasys.teku.spec.util.RandomChainBuilderForkChoiceStrategy;

class ForkChoiceUtilTest {

  private static final UInt64 GENESIS_TIME = UInt64.valueOf("1591924193");
  private static final UInt64 GENESIS_TIME_MILLIS = GENESIS_TIME.times(1000L);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RandomChainBuilder chainBuilder = new RandomChainBuilder(dataStructureUtil);
  private final RandomChainBuilderForkChoiceStrategy forkChoiceStrategy =
      new RandomChainBuilderForkChoiceStrategy(chainBuilder);

  private final ForkChoiceUtil forkChoiceUtil = spec.getGenesisSpec().getForkChoiceUtil();
  private final UInt64 slot50Time =
      GENESIS_TIME.plus(spec.getGenesisSpecConfig().getSecondsPerSlot() * 50L);
  private final UInt64 slot50TimeMillis = secondsToMillis(slot50Time);

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
  public void getCurrentSlotForMillis_shouldGetZeroAtGenesisMillis() {
    assertThat(forkChoiceUtil.getCurrentSlotForMillis(GENESIS_TIME_MILLIS, GENESIS_TIME_MILLIS))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getCurrentSlot_shouldGetNonZeroPastGenesis() {
    assertThat(forkChoiceUtil.getCurrentSlot(slot50Time, GENESIS_TIME))
        .isEqualTo(UInt64.valueOf(50L));
  }

  @Test
  public void getCurrentSlotForMillis_shouldGetNonZeroPastGenesisMillis() {
    assertThat(forkChoiceUtil.getCurrentSlotForMillis(slot50TimeMillis, GENESIS_TIME_MILLIS))
        .isEqualTo(UInt64.valueOf(50L));
  }

  @Test
  public void getCurrentSlot_shouldGetZeroPriorToGenesis() {
    assertThat(forkChoiceUtil.getCurrentSlot(GENESIS_TIME.minus(1), GENESIS_TIME))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getCurrentSlotForMillis_shouldGetZeroPriorToGenesisMillis() {
    assertThat(
            forkChoiceUtil.getCurrentSlotForMillis(
                GENESIS_TIME_MILLIS.minus(1000), GENESIS_TIME_MILLIS))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getSlotStartTime_shouldGetGenesisTimeForBlockZero() {
    assertThat(forkChoiceUtil.getSlotStartTime(UInt64.ZERO, GENESIS_TIME)).isEqualTo(GENESIS_TIME);
  }

  @Test
  public void getSlotStartTimeMillis_shouldGetGenesisTimeForBlockZeroMillis() {
    assertThat(forkChoiceUtil.getSlotStartTimeMillis(UInt64.ZERO, GENESIS_TIME_MILLIS))
        .isEqualTo(GENESIS_TIME_MILLIS);
  }

  @Test
  public void getSlotStartTime_shouldGetCorrectTimePastGenesis() {
    assertThat(forkChoiceUtil.getSlotStartTime(UInt64.valueOf(50L), GENESIS_TIME))
        .isEqualTo(slot50Time);
  }

  @Test
  public void getSlotStartTimeMillis_shouldGetCorrectTimePastGenesisMillis() {
    assertThat(forkChoiceUtil.getSlotStartTimeMillis(UInt64.valueOf(50L), GENESIS_TIME_MILLIS))
        .isEqualTo(slot50TimeMillis);
  }

  @Test
  void onTick_shouldExitImmediatelyWhenCurrentTimeIsBeforeGenesisTime() {
    final MutableStore store = mock(MutableStore.class);
    when(store.getGenesisTimeMillis()).thenReturn(UInt64.valueOf(3000000));
    when(store.getTimeInMillis()).thenReturn(UInt64.ZERO);
    forkChoiceUtil.onTick(store, UInt64.valueOf(2000000));

    verify(store, never()).setTimeMillis(any());
  }

  @Test
  void onTick_shouldExitImmediatelyWhenCurrentTimeIsBeforeStoreTime() {
    final MutableStore store = mock(MutableStore.class);
    when(store.getGenesisTimeMillis()).thenReturn(UInt64.valueOf(3000000));
    when(store.getTimeInMillis()).thenReturn(UInt64.valueOf(5000000));
    forkChoiceUtil.onTick(store, UInt64.valueOf(4000000));

    verify(store, never()).setTimeMillis(any());
  }

  @Test
  void onTick_setsStoreTimeMillis() {
    final TestStoreImpl store = new TestStoreFactory(spec).createGenesisStore();
    final UInt64 expectedMillis = store.getTimeInMillis().plus(123456);
    forkChoiceUtil.onTick(store, expectedMillis);
    assertThat(store.getTimeInMillis()).isEqualTo(expectedMillis);
  }

  @Test
  void onTick_shouldProcessSkippedEpochTransitions() {
    final TestStoreImpl store = new TestStoreFactory(spec).createGenesisStore();

    // Setup a block in epoch 1 with checkpoints that should be pulled up when transitioning to
    // epoch 2.
    final UInt64 headBlockSlot = spec.computeStartSlotAtEpoch(UInt64.ONE).plus(3);
    chainBuilder.generateBlocksUpToSlot(headBlockSlot);
    final SignedBlockAndState headBlockAndState =
        chainBuilder.getBlockAndState(headBlockSlot).orElseThrow();
    final Checkpoint justifiedCheckpoint =
        headBlockAndState.getState().getCurrentJustifiedCheckpoint();
    final Checkpoint finalizedCheckpoint = headBlockAndState.getState().getFinalizedCheckpoint();
    final BlockCheckpoints checkpoints =
        new BlockCheckpoints(
            justifiedCheckpoint,
            finalizedCheckpoint,
            new Checkpoint(justifiedCheckpoint.getEpoch().plus(1), justifiedCheckpoint.getRoot()),
            new Checkpoint(finalizedCheckpoint.getEpoch().plus(1), finalizedCheckpoint.getRoot()));
    store.putBlockAndState(headBlockAndState, checkpoints);

    // Transition straight to epoch 3
    final UInt64 finalMillis =
        spec.getSlotStartTimeMillis(
            spec.computeStartSlotAtEpoch(UInt64.valueOf(3)), store.getGenesisTimeMillis());
    forkChoiceUtil.onTick(store, finalMillis);
    assertThat(store.getTimeInMillis()).isEqualTo(finalMillis);

    // Check the pull-up for epoch 2 was still performed
    assertThat(store.getJustifiedCheckpoint())
        .isEqualTo(checkpoints.getUnrealizedJustifiedCheckpoint());
    assertThat(store.getFinalizedCheckpoint())
        .isEqualTo(checkpoints.getUnrealizedFinalizedCheckpoint());
  }

  @Test
  void canOptimisticallyImport_shouldBeFalseWhenBlockToImportIsTheMergeBlock() {
    final SignedBeaconBlock blockToImport = dataStructureUtil.randomSignedBeaconBlock(9);
    final ReadOnlyStore store = mockStore(10, blockToImport.getRoot());

    assertThat(forkChoiceUtil.canOptimisticallyImport(store, blockToImport)).isFalse();
  }

  @Test
  void canOptimisticallyImport_shouldBeTrueWhenParentBlockHasRealPayload() {
    final SignedBeaconBlock blockToImport = dataStructureUtil.randomSignedBeaconBlock(11);
    final ReadOnlyStore store =
        mockStore(15, blockToImport.getParentRoot(), blockToImport.getRoot());
    assertThat(forkChoiceUtil.canOptimisticallyImport(store, blockToImport)).isTrue();
  }

  @Test
  void canOptimisticallyImport_shouldBeFalseWhenBlockIsMergeButNotOldEnough() {
    final int blockSlot = 11;
    final SignedBeaconBlock blockToImport = dataStructureUtil.randomSignedBeaconBlock(blockSlot);
    final ReadOnlyStore store =
        mockStore(blockSlot + getSafeSyncDistance() - 1, blockToImport.getRoot());

    assertThat(forkChoiceUtil.canOptimisticallyImport(store, blockToImport)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("isShufflingStableConditions")
  void isShufflingStable(final int slot, final boolean expectedResult) {
    assertThat(forkChoiceUtil.isShufflingStable(UInt64.valueOf(slot))).isEqualTo(expectedResult);
  }

  public static Stream<Arguments> isShufflingStableConditions() {
    // 8 slots per epoch for test conditions
    final int epochStart = 10240 * 8;
    final int nextEpochStart = 10241 * 8;
    // slot , expectedResult
    final ArrayList<Arguments> args = new ArrayList<>();

    // shuffling is not stable at any epoch boundary
    args.add(Arguments.of(0, false));
    args.add(Arguments.of(epochStart, false));
    args.add(Arguments.of(nextEpochStart, false));
    for (int i = epochStart + 1; i < nextEpochStart; i++) {
      // all non epoch boundary slots are considered stable
      args.add(Arguments.of(i, true));
    }

    return args.stream();
  }

  @ParameterizedTest
  @MethodSource("isFinalizationOkConditions")
  void isFinalizationOk(final int epoch, final int testSlot, final boolean isFinalizationOk) {
    final UInt64 finalizedEpoch = UInt64.valueOf(epoch);
    final UInt64 slot = UInt64.valueOf(testSlot);

    final ReadOnlyStore myStore = mock(ReadOnlyStore.class);
    final Checkpoint myCheckpoint = mock(Checkpoint.class);
    when(myStore.getFinalizedCheckpoint()).thenReturn(myCheckpoint);
    when(myCheckpoint.getEpoch()).thenReturn(finalizedEpoch);
    assertThat(forkChoiceUtil.isFinalizationOk(myStore, slot)).isEqualTo(isFinalizationOk);
  }

  // slots per epoch in this suite is 8
  // reorgMaxEpochsSinceFinalization is 2
  private static Stream<Arguments> isFinalizationOkConditions() {
    // epoch 10 starts at slot (8 x 10) 80
    // epoch 13 starts at slot (8 x 13) 104
    // epoch , slot , true/false
    ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(10, 0, true));
    args.add(Arguments.of(10, 80, true));
    args.add(Arguments.of(10, 96, true));
    args.add(Arguments.of(10, 103, true));
    args.add(Arguments.of(10, 104, false));

    return args.stream();
  }

  @Test
  void canOptimisticallyImport_shouldBeTrueWhenBlockIsMergeAndIsOldEnough() {
    final int blockSlot = 11;
    final SignedBeaconBlock blockToImport = dataStructureUtil.randomSignedBeaconBlock(blockSlot);
    final ReadOnlyStore store =
        mockStore(blockSlot + getSafeSyncDistance(), blockToImport.getRoot());

    assertThat(forkChoiceUtil.canOptimisticallyImport(store, blockToImport)).isTrue();
  }

  private ReadOnlyStore mockStore(
      final long currentSlot, final Bytes32... blocksWithNonDefaultPayloads) {
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = mock(ReadOnlyForkChoiceStrategy.class);
    when(store.getForkChoiceStrategy()).thenReturn(forkChoiceStrategy);

    when(forkChoiceStrategy.executionBlockHash(any())).thenReturn(Optional.of(Bytes32.ZERO));
    for (Bytes32 blocksWithNonDefaultPayload : blocksWithNonDefaultPayloads) {
      when(forkChoiceStrategy.executionBlockHash(blocksWithNonDefaultPayload))
          .thenReturn(Optional.of(dataStructureUtil.randomBytes32()));
    }

    final UInt64 genesisTime = UInt64.valueOf(1982239L);
    when(store.getTimeSeconds())
        .thenReturn(spec.getSlotStartTime(UInt64.valueOf(currentSlot), genesisTime));
    when(store.getGenesisTime()).thenReturn(genesisTime);
    return store;
  }

  private int getSafeSyncDistance() {
    return spec.getGenesisSpecConfig()
        .toVersionBellatrix()
        .orElseThrow()
        .getSafeSlotsToImportOptimistically();
  }

  private Map<UInt64, Bytes32> getRootsForBlocks(final int... blockNumbers) {
    return IntStream.of(blockNumbers)
        .mapToObj(chainBuilder::getBlock)
        .flatMap(Optional::stream)
        .collect(Collectors.toMap(SignedBeaconBlock::getSlot, SignedBeaconBlock::getRoot));
  }
}
