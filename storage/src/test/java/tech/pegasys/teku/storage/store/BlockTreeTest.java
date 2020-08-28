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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockTreeTest {
  final ChainBuilder chainBuilder = ChainBuilder.createDefault();

  @Test
  public void create_missingSlotLookup() {
    final Bytes32 rootHash = Bytes32.fromHexStringLenient("0x01");
    final Bytes32 rootParent = Bytes32.fromHexStringLenient("0x00");
    final Bytes32 childHash = Bytes32.fromHexStringLenient("0x02");

    final Map<Bytes32, Bytes32> childToParent =
        Map.of(
            rootHash, rootParent,
            childHash, rootHash);

    final Map<Bytes32, UInt64> slotLookup = Map.of(rootHash, UInt64.ONE);

    final HashTree hashTree =
        HashTree.builder().rootHash(rootHash).childAndParentRoots(childToParent).build();

    assertThatThrownBy(() -> BlockTree.create(hashTree, slotLookup))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Slot lookup and hash tree must contain the same number of elements");
  }

  @Test
  public void create_extraSlotLookup() {
    final Bytes32 rootHash = Bytes32.fromHexStringLenient("0x01");
    final Bytes32 rootParent = Bytes32.fromHexStringLenient("0x00");
    final Bytes32 childHash = Bytes32.fromHexStringLenient("0x02");
    final Bytes32 otherHash = Bytes32.fromHexStringLenient("0x03");

    final Map<Bytes32, Bytes32> childToParent =
        Map.of(
            rootHash, rootParent,
            childHash, rootHash);

    final Map<Bytes32, UInt64> slotLookup =
        Map.of(
            rootHash, UInt64.ONE,
            childHash, UInt64.valueOf(2),
            otherHash, UInt64.valueOf(3));

    final HashTree hashTree =
        HashTree.builder().rootHash(rootHash).childAndParentRoots(childToParent).build();

    assertThatThrownBy(() -> BlockTree.create(hashTree, slotLookup))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Slot lookup and hash tree must contain the same number of elements");
  }

  @Test
  public void create_mismatchInSlotLookup() {
    final Bytes32 rootHash = Bytes32.fromHexStringLenient("0x01");
    final Bytes32 rootParent = Bytes32.fromHexStringLenient("0x00");
    final Bytes32 childHash = Bytes32.fromHexStringLenient("0x02");
    final Bytes32 otherHash = Bytes32.fromHexStringLenient("0x03");

    final Map<Bytes32, Bytes32> childToParent =
        Map.of(
            rootHash, rootParent,
            childHash, rootHash);

    final Map<Bytes32, UInt64> slotLookup =
        Map.of(
            childHash, UInt64.valueOf(2),
            otherHash, UInt64.valueOf(3));

    final HashTree hashTree =
        HashTree.builder().rootHash(rootHash).childAndParentRoots(childToParent).build();

    assertThatThrownBy(() -> BlockTree.create(hashTree, slotLookup))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Slot lookup and hash tree must contain the same roots");
  }

  @Test
  public void isRootAtNthEpochBoundary_invalidNParameter_zero() {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SignedBlockAndState block = chainBuilder.generateNextBlock();
    final BlockTree blockTree = createBlockTree(genesis.getSlot());

    assertThatThrownBy(() -> blockTree.isRootAtNthEpochBoundary(block.getRoot(), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @Test
  public void isRootAtNthEpochBoundary_invalidNParameter_negative() {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SignedBlockAndState block = chainBuilder.generateNextBlock();
    final BlockTree blockTree = createBlockTree(genesis.getSlot());

    assertThatThrownBy(() -> blockTree.isRootAtNthEpochBoundary(block.getRoot(), -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isRootAtNthEpochBoundary_treeRootedAtGenesis(final int n) {
    final UInt64 epochs = UInt64.valueOf(n * 3);

    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    while (chainBuilder.getLatestEpoch().isLessThan(epochs)) {
      chainBuilder.generateNextBlock();
    }

    final BlockTree blockTree = createBlockTree(genesis.getSlot());

    for (int i = 0; i < chainBuilder.getLatestSlot().intValue(); i++) {
      final boolean expected = i % (n * SLOTS_PER_EPOCH) == 0 && i != 0;
      final SignedBeaconBlock block = chainBuilder.getBlockAtSlot(i);
      assertThat(blockTree.isRootAtNthEpochBoundary(block.getRoot(), n))
          .describedAs("Block at %d should %sbe at epoch boundary", i, expected ? "" : "not ")
          .isEqualTo(expected);
    }
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isRootAtNthEpochBoundary_treeRootedAfterGenesis(final int n) {
    final UInt64 epochs = UInt64.valueOf(3 * n);

    chainBuilder.generateGenesis();
    while (chainBuilder.getLatestEpoch().isLessThan(epochs)) {
      chainBuilder.generateNextBlock();
    }

    final UInt64 rootSlot = compute_start_slot_at_epoch(UInt64.ONE);
    final BlockTree blockTree = createBlockTree(rootSlot);

    for (int i = rootSlot.intValue(); i < chainBuilder.getLatestSlot().intValue(); i++) {
      final boolean expected = i % (n * SLOTS_PER_EPOCH) == 0 && i != rootSlot.intValue();
      final SignedBeaconBlock block = chainBuilder.getBlockAtSlot(i);
      assertThat(blockTree.isRootAtNthEpochBoundary(block.getRoot(), n))
          .describedAs("Block at %d should %sbe at epoch boundary", i, expected ? "" : "not ")
          .isEqualTo(expected);
    }
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isRootAtNthEpochBoundary_withSkippedBlock(final int n) {
    final int nthStartSlot = compute_start_slot_at_epoch(UInt64.valueOf(n)).intValue();

    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SignedBlockAndState block1 = chainBuilder.generateBlockAtSlot(nthStartSlot + 1);
    final SignedBlockAndState block2 = chainBuilder.generateNextBlock();

    final BlockTree blockTree = createBlockTree(genesis.getSlot());

    assertThat(blockTree.isRootAtNthEpochBoundary(block1.getRoot(), 1)).isTrue();
    assertThat(blockTree.isRootAtNthEpochBoundary(block2.getRoot(), 1)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isRootAtNthEpochBoundary_withSkippedEpochs_oneEpochAndSlotSkipped(final int n) {
    final int nthStartSlot = compute_start_slot_at_epoch(UInt64.valueOf(n)).intValue();

    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SignedBlockAndState block1 =
        chainBuilder.generateBlockAtSlot(nthStartSlot + SLOTS_PER_EPOCH + 1);
    final SignedBlockAndState block2 = chainBuilder.generateNextBlock();

    final BlockTree blockTree = createBlockTree(genesis.getSlot());

    assertThat(blockTree.isRootAtNthEpochBoundary(block1.getRoot(), n)).isTrue();
    assertThat(blockTree.isRootAtNthEpochBoundary(block2.getRoot(), n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isRootAtNthEpochBoundary_withSkippedEpochs_nearlyNEpochsSkipped(final int n) {
    final int startSlotAt2N = compute_start_slot_at_epoch(UInt64.valueOf(n * 2)).intValue();

    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SignedBlockAndState block1 = chainBuilder.generateBlockAtSlot(startSlotAt2N - 1);
    final SignedBlockAndState block2 = chainBuilder.generateNextBlock();
    final SignedBlockAndState block3 = chainBuilder.generateNextBlock();

    final BlockTree blockTree = createBlockTree(genesis.getSlot());

    assertThat(blockTree.isRootAtNthEpochBoundary(block1.getRoot(), n)).isTrue();
    assertThat(blockTree.isRootAtNthEpochBoundary(block2.getRoot(), n)).isTrue();
    assertThat(blockTree.isRootAtNthEpochBoundary(block3.getRoot(), n)).isFalse();
  }

  public static Stream<Arguments> getNValues() {
    return Stream.of(
        Arguments.of(1), Arguments.of(2), Arguments.of(3), Arguments.of(4), Arguments.of(5));
  }

  @Test
  public void getEpoch() {
    final UInt64 epochs = UInt64.valueOf(3);

    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    while (chainBuilder.getLatestEpoch().isLessThan(epochs)) {
      chainBuilder.generateNextBlock();
    }

    final BlockTree blockTree = createBlockTree(genesis.getSlot());

    for (int i = 0; i < chainBuilder.getLatestSlot().intValue(); i++) {
      final UInt64 expected = compute_epoch_at_slot(UInt64.valueOf(i));
      final SignedBeaconBlock block = chainBuilder.getBlockAtSlot(i);
      assertThat(blockTree.getEpoch(block.getRoot()))
          .describedAs("Block at %d should have epoch %s", i, expected)
          .isEqualTo(expected);
    }
  }

  private BlockTree createBlockTree(final UInt64 rootSlot) {
    final Bytes32 rootHash = chainBuilder.getBlockAtSlot(rootSlot).getRoot();
    final List<SignedBeaconBlock> blocks =
        chainBuilder
            .streamBlocksAndStates(rootSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    final HashTree hashTree = HashTree.builder().rootHash(rootHash).blocks(blocks).build();
    final Map<Bytes32, UInt64> rootToSlot =
        blocks.stream()
            .collect(Collectors.toMap(SignedBeaconBlock::getRoot, SignedBeaconBlock::getSlot));

    return BlockTree.create(hashTree, rootToSlot);
  }
}
