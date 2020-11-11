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

package tech.pegasys.teku.core;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_next_epoch_boundary;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ProtoArrayForkChoiceStrategy;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;

class ForkChoiceUtilTest {

  private static final UInt64 GENESIS_TIME = UInt64.valueOf("1591924193");
  static final UInt64 SLOT_50 = GENESIS_TIME.plus(SECONDS_PER_SLOT * 50L);
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(16);
  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();
  private final ReadOnlyStore store = new TestStoreFactory().createGenesisStore(genesis.getState());
  private final ProtoArrayForkChoiceStrategy forkChoiceStrategy =
      ProtoArrayForkChoiceStrategy.initializeAndMigrateStorage(
              store, ProtoArrayStorageChannel.NO_OP)
          .join();

  @Test
  void getAncestors_shouldGetSimpleSequenceOfAncestors() {
    chainBuilder.generateBlocksUpToSlot(10).forEach(this::addBlock);

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        ForkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getLatestBlockAndState().getRoot(),
            UInt64.ONE,
            UInt64.ONE,
            UInt64.valueOf(8));

    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(1, 2, 3, 4, 5, 6, 7, 8));
  }

  @Test
  void getAncestors_shouldGetSequenceOfRootsWhenSkipping() {
    chainBuilder.generateBlocksUpToSlot(10).forEach(this::addBlock);

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        ForkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getLatestBlockAndState().getRoot(),
            UInt64.ONE,
            UInt64.valueOf(2),
            UInt64.valueOf(4));

    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(1, 3, 5, 7));
  }

  @Test
  void getAncestors_shouldGetSequenceOfRootsWhenStartIsPriorToFinalizedCheckpoint() {
    chainBuilder.generateBlocksUpToSlot(10).forEach(this::addBlock);
    forkChoiceStrategy.setPruneThreshold(0);
    forkChoiceStrategy.applyUpdate(
        emptyList(), emptySet(), createCheckpointFromBlock(chainBuilder.getBlockAtSlot(4)));

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        ForkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getLatestBlockAndState().getRoot(),
            UInt64.ONE,
            UInt64.valueOf(2),
            UInt64.valueOf(4));

    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(5, 7));
  }

  private Checkpoint createCheckpointFromBlock(final SignedBeaconBlock block) {
    final UInt64 epoch = compute_next_epoch_boundary(block.getSlot());
    return new Checkpoint(epoch, block.getRoot());
  }

  @Test
  void getAncestors_shouldGetSequenceOfRootsWhenEndIsAfterChainHead() {
    chainBuilder.generateBlocksUpToSlot(10).forEach(this::addBlock);

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        ForkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getLatestBlockAndState().getRoot(),
            UInt64.valueOf(6),
            UInt64.valueOf(2),
            UInt64.valueOf(20));

    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(6, 8, 10));
  }

  @Test
  void getAncestors_shouldNotIncludeEntryForEmptySlots() {
    addBlock(chainBuilder.generateBlockAtSlot(3));
    addBlock(chainBuilder.generateBlockAtSlot(5));

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        ForkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getLatestBlockAndState().getRoot(),
            UInt64.ZERO,
            UInt64.ONE,
            UInt64.valueOf(10));
    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(0, 3, 5));
  }

  @Test
  void getAncestorsOnFork_shouldIncludeHeadBlockAndExcludeStartSlot() {
    chainBuilder.generateBlocksUpToSlot(10).forEach(this::addBlock);

    final NavigableMap<UInt64, Bytes32> ancestorsOnFork =
        ForkChoiceUtil.getAncestorsOnFork(
            forkChoiceStrategy, chainBuilder.getLatestBlockAndState().getRoot(), UInt64.valueOf(5));
    assertThat(ancestorsOnFork).doesNotContainKey(UInt64.valueOf(5));
  }

  @Test
  void getAncestorsOnFork_shouldNotIncludeHeadBlockWhenItIsAtStartSlot() {
    chainBuilder.generateBlocksUpToSlot(3).forEach(this::addBlock);

    final SignedBlockAndState headBlock = chainBuilder.getLatestBlockAndState();
    final NavigableMap<UInt64, Bytes32> ancestorsOnFork =
        ForkChoiceUtil.getAncestorsOnFork(
            forkChoiceStrategy, headBlock.getRoot(), headBlock.getSlot());
    assertThat(ancestorsOnFork).isEmpty();
  }

  @Test
  public void getCurrentSlot_shouldGetZeroAtGenesis() {
    assertThat(ForkChoiceUtil.getCurrentSlot(GENESIS_TIME, GENESIS_TIME)).isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getCurrentSlot_shouldGetNonZeroPastGenesis() {

    assertThat(ForkChoiceUtil.getCurrentSlot(SLOT_50, GENESIS_TIME)).isEqualTo(UInt64.valueOf(50L));
  }

  @Test
  public void getCurrentSlot_shouldGetZeroPriorToGenesis() {
    assertThat(ForkChoiceUtil.getCurrentSlot(GENESIS_TIME.minus(1), GENESIS_TIME))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getSlotStartTime_shouldGetGenesisTimeForBlockZero() {
    assertThat(ForkChoiceUtil.getSlotStartTime(UInt64.ZERO, GENESIS_TIME)).isEqualTo(GENESIS_TIME);
  }

  @Test
  public void getSlotStartTime_shouldGetCorrectTimePastGenesis() {
    assertThat(ForkChoiceUtil.getSlotStartTime(UInt64.valueOf(50L), GENESIS_TIME))
        .isEqualTo(SLOT_50);
  }

  @Test
  void on_tick_shouldExitImmediatelyWhenCurrentTimeIsBeforeGenesisTime() {
    final MutableStore store = mock(MutableStore.class);
    when(store.getGenesisTime()).thenReturn(UInt64.valueOf(3000));
    when(store.getTime()).thenReturn(UInt64.ZERO);
    ForkChoiceUtil.on_tick(store, UInt64.valueOf(2000));

    verify(store, never()).setTime(any());
  }

  @Test
  void on_tick_shouldExitImmediatelyWhenCurrentTimeIsBeforeStoreTime() {
    final MutableStore store = mock(MutableStore.class);
    when(store.getGenesisTime()).thenReturn(UInt64.valueOf(3000));
    when(store.getTime()).thenReturn(UInt64.valueOf(5000));
    ForkChoiceUtil.on_tick(store, UInt64.valueOf(4000));

    verify(store, never()).setTime(any());
  }

  private Map<UInt64, Bytes32> getRootsForBlocks(final int... blockNumbers) {
    return IntStream.of(blockNumbers)
        .mapToObj(chainBuilder::getBlockAtSlot)
        .collect(Collectors.toMap(SignedBeaconBlock::getSlot, SignedBeaconBlock::getRoot));
  }

  private void addBlock(final SignedBlockAndState blockAndState) {

    forkChoiceStrategy.applyUpdate(
        List.of(BlockAndCheckpointEpochs.fromBlockAndState(blockAndState)),
        emptySet(),
        new Checkpoint(
            compute_next_epoch_boundary(blockAndState.getSlot()), blockAndState.getRoot()));
  }
}
