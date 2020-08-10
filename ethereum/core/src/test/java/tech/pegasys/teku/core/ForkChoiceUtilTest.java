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

import static org.assertj.core.api.Assertions.assertThat;
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
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ProtoArrayForkChoiceStrategy;
import tech.pegasys.teku.protoarray.StubProtoArrayStorageChannel;

class ForkChoiceUtilTest {

  private static final UInt64 GENESIS_TIME = UInt64.valueOf("1591924193");
  static final UInt64 SLOT_50 =
      GENESIS_TIME.plus(UInt64.valueOf(SECONDS_PER_SLOT).times(UInt64.valueOf(50L)));
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(16);
  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();
  private final ReadOnlyStore store = new TestStoreFactory().createGenesisStore(genesis.getState());
  private final ProtoArrayForkChoiceStrategy forkChoiceStrategy =
      ProtoArrayForkChoiceStrategy.initialize(store, new StubProtoArrayStorageChannel()).join();

  @Test
  void getAncestors_shouldGetSimpleSequenceOfAncestors() throws Exception {
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
  void getAncestors_shouldGetSequenceOfRootsWhenSkipping() throws Exception {
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
  void getAncestors_shouldGetSequenceOfRootsWhenStartIsPriorToFinalizedCheckpoint()
      throws Exception {
    chainBuilder.generateBlocksUpToSlot(10).forEach(this::addBlock);
    forkChoiceStrategy.setPruneThreshold(0);
    forkChoiceStrategy.maybePrune(chainBuilder.getBlockAtSlot(4).getRoot());

    final NavigableMap<UInt64, Bytes32> rootsBySlot =
        ForkChoiceUtil.getAncestors(
            forkChoiceStrategy,
            chainBuilder.getLatestBlockAndState().getRoot(),
            UInt64.ONE,
            UInt64.valueOf(2),
            UInt64.valueOf(4));

    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(5, 7));
  }

  @Test
  void getAncestors_shouldGetSequenceOfRootsWhenEndIsAfterChainHead() throws Exception {
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
  void getAncestors_shouldNotIncludeEntryForEmptySlots() throws Exception {
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
  public void getCurrentSlot_shouldGetZeroAtGenesis() {
    assertThat(ForkChoiceUtil.getCurrentSlot(GENESIS_TIME, GENESIS_TIME)).isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getCurrentSlot_shouldGetNonZeroPastGenesis() {

    assertThat(ForkChoiceUtil.getCurrentSlot(SLOT_50, GENESIS_TIME)).isEqualTo(UInt64.valueOf(50L));
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

  private Map<UInt64, Bytes32> getRootsForBlocks(final int... blockNumbers) {
    return IntStream.of(blockNumbers)
        .mapToObj(chainBuilder::getBlockAtSlot)
        .collect(Collectors.toMap(SignedBeaconBlock::getSlot, SignedBeaconBlock::getRoot));
  }

  private void addBlock(final SignedBlockAndState blockAndState) {
    forkChoiceStrategy.onBlock(blockAndState.getBlock().getMessage(), blockAndState.getState());
  }
}
