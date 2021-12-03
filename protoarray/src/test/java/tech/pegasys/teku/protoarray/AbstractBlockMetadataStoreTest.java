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

package tech.pegasys.teku.protoarray;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

abstract class AbstractBlockMetadataStoreTest {

  protected final Spec spec = TestSpecFactory.createMinimalMerge();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();
  private final Checkpoint genesisCheckpoint =
      new Checkpoint(SpecConfig.GENESIS_EPOCH, genesis.getRoot());

  @Test
  void contains_shouldOnlyContainBlocksThatExist() {
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    assertThat(store.contains(genesis.getRoot())).isTrue();
    assertThat(store.contains(Bytes32.ZERO)).isFalse();
  }

  @Test
  void contains_shouldContainAddedBlocks() {
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    final BlockAndCheckpointEpochs block1 =
        BlockAndCheckpointEpochs.fromBlockAndState(chainBuilder.generateBlockAtSlot(1));
    final BlockAndCheckpointEpochs block2 =
        BlockAndCheckpointEpochs.fromBlockAndState(chainBuilder.generateBlockAtSlot(2));
    final BlockAndCheckpointEpochs block3 =
        BlockAndCheckpointEpochs.fromBlockAndState(chainBuilder.generateBlockAtSlot(3));

    store.applyUpdate(List.of(block1, block2, block3), Collections.emptySet(), genesisCheckpoint);

    chainBuilder
        .streamBlocksAndStates()
        .forEach(block -> assertThat(store.contains(block.getRoot())).isTrue());
  }

  @Test
  void contains_shouldNotContainRemovedBlocks() {
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    final BlockAndCheckpointEpochs block1 =
        BlockAndCheckpointEpochs.fromBlockAndState(chainBuilder.generateBlockAtSlot(1));
    final BlockAndCheckpointEpochs block2 =
        BlockAndCheckpointEpochs.fromBlockAndState(chainBuilder.generateBlockAtSlot(2));
    final BlockAndCheckpointEpochs block3 =
        BlockAndCheckpointEpochs.fromBlockAndState(chainBuilder.generateBlockAtSlot(3));

    store.applyUpdate(
        List.of(block1, block2, block3),
        Set.of(genesis.getRoot(), block1.getRoot()),
        new Checkpoint(UInt64.ONE, block2.getRoot()));

    assertThat(store.contains(genesis.getRoot())).isFalse();
    assertThat(store.contains(block1.getRoot())).isFalse();
    assertThat(store.contains(block2.getRoot())).isTrue();
    assertThat(store.contains(block3.getRoot())).isTrue();
  }

  @Test
  void processAllInOrder_shouldVisitAllBlocksInSingleChain() {
    final int lastSlot = 10;
    chainBuilder.generateBlocksUpToSlot(lastSlot);
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    final AtomicInteger expectedNextSlot = new AtomicInteger(0);
    store.processAllInOrder(
        (childRoot, slot, parentRoot) -> {
          assertThat(slot).isEqualTo(UInt64.valueOf(expectedNextSlot.getAndIncrement()));
          final SignedBeaconBlock block = chainBuilder.getBlockAtSlot(slot);
          assertThat(childRoot).isEqualTo(block.getRoot());
          assertThat(slot).isEqualTo(block.getSlot());
          assertThat(parentRoot).isEqualTo(block.getParentRoot());
        });
    // We should have processed the block in the last slot and now be expected the one after
    assertThat(expectedNextSlot).hasValue(lastSlot + 1);
  }

  @Test
  void processAllInOrder_shouldVisitParentBlocksBeforeChildBlocksInForks() {
    // First chain has all blocks up to 10
    // Fork chain has 0-5, skips 6 and then has 7-10
    chainBuilder.generateBlocksUpToSlot(5);
    final ChainBuilder forkBuilder = chainBuilder.fork();
    chainBuilder.generateBlocksUpToSlot(10);
    forkBuilder.generateBlockAtSlot(7);
    forkBuilder.generateBlocksUpToSlot(10);
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    store.applyUpdate(
        forkBuilder
            .streamBlocksAndStates()
            .map(BlockAndCheckpointEpochs::fromBlockAndState)
            .collect(toList()),
        Collections.emptySet(),
        genesisCheckpoint);

    final Set<Bytes32> seenBlocks = new HashSet<>();

    store.processAllInOrder(
        (childRoot, slot, parentRoot) -> {
          assertThat(seenBlocks).doesNotContain(childRoot);
          if (!seenBlocks.isEmpty()) {
            // First block won't have visited the parent
            assertThat(seenBlocks).contains(parentRoot);
          }

          seenBlocks.add(childRoot);

          final SignedBeaconBlock block =
              chainBuilder
                  .getBlock(childRoot)
                  .or(() -> forkBuilder.getBlock(childRoot))
                  .orElseThrow();
          assertThat(childRoot).isEqualTo(block.getRoot());
          assertThat(slot).isEqualTo(block.getSlot());
          assertThat(parentRoot).isEqualTo(block.getParentRoot());
        });
    // Check every block was seen
    final Set<Bytes32> expectedSeenRoots =
        Stream.concat(chainBuilder.streamBlocksAndStates(), forkBuilder.streamBlocksAndStates())
            .map(StateAndBlockSummary::getRoot)
            .collect(toSet());

    assertThat(seenBlocks).containsExactlyInAnyOrderElementsOf(expectedSeenRoots);
  }

  @Test
  void processHashesInChain_shouldWalkUpSpecifiedChain() {
    // First chain has all blocks up to 10
    // Fork chain has 0-5, skips 6 and then has 7-10
    chainBuilder.generateBlocksUpToSlot(5);
    final ChainBuilder forkBuilder = chainBuilder.fork();
    chainBuilder.generateBlocksUpToSlot(10);
    forkBuilder.generateBlockAtSlot(7);
    forkBuilder.generateBlocksUpToSlot(10);
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    store.applyUpdate(
        forkBuilder
            .streamBlocksAndStates()
            .map(BlockAndCheckpointEpochs::fromBlockAndState)
            .collect(toList()),
        Collections.emptySet(),
        genesisCheckpoint);

    verifyHashesInChain(
        store,
        forkBuilder,
        forkBuilder.getLatestBlockAndState().getRoot(),
        forkBuilder.streamBlocksAndStates());
    verifyHashesInChain(
        store,
        chainBuilder,
        chainBuilder.getLatestBlockAndState().getRoot(),
        chainBuilder.streamBlocksAndStates());

    // And check we can start from part way along the chain
    verifyHashesInChain(
        store,
        chainBuilder,
        chainBuilder.getBlockAtSlot(6).getRoot(),
        chainBuilder.streamBlocksAndStates(0, 6));
  }

  @Test
  void processHashesInChainWhile_shouldStopProcessingWhenProcessorReturnsFalse() {
    chainBuilder.generateBlocksUpToSlot(10);
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);

    // And check we can start from part way along the chain
    final Bytes32 headRoot = chainBuilder.getBlockAtSlot(6).getRoot();
    final Stream<SignedBlockAndState> expectedBlocks = chainBuilder.streamBlocksAndStates(3, 6);

    final Set<Bytes32> seenBlocks = new HashSet<>();

    final AtomicReference<Bytes32> expectedBlock = new AtomicReference<>(headRoot);
    store.processHashesInChainWhile(
        headRoot,
        (childRoot, slot, parentRoot) -> {
          assertThat(seenBlocks).doesNotContain(childRoot);
          seenBlocks.add(childRoot);

          final SignedBeaconBlock block = chainBuilder.getBlock(expectedBlock.get()).orElseThrow();
          assertThat(childRoot).isEqualTo(block.getRoot());
          assertThat(slot).isEqualTo(block.getSlot());
          assertThat(parentRoot).isEqualTo(block.getParentRoot());

          expectedBlock.set(block.getParentRoot());
          return !slot.equals(UInt64.valueOf(3));
        });
    // Check all fork blocks were seen
    final Set<Bytes32> expectedSeenRoots =
        expectedBlocks.map(StateAndBlockSummary::getRoot).collect(toSet());

    assertThat(seenBlocks).containsExactlyInAnyOrderElementsOf(expectedSeenRoots);
  }

  private void verifyHashesInChain(
      final BlockMetadataStore store,
      final ChainBuilder chain,
      final Bytes32 headRoot,
      final Stream<SignedBlockAndState> expectedBlocks) {
    final Set<Bytes32> seenBlocks = new HashSet<>();

    final AtomicReference<Bytes32> expectedBlock = new AtomicReference<>(headRoot);
    store.processHashesInChain(
        headRoot,
        (childRoot, slot, parentRoot) -> {
          assertThat(seenBlocks).doesNotContain(childRoot);
          seenBlocks.add(childRoot);

          final SignedBeaconBlock block = chain.getBlock(expectedBlock.get()).orElseThrow();
          assertThat(childRoot).isEqualTo(block.getRoot());
          assertThat(slot).isEqualTo(block.getSlot());
          assertThat(parentRoot).isEqualTo(block.getParentRoot());

          expectedBlock.set(block.getParentRoot());
        });
    // Check all fork blocks were seen
    final Set<Bytes32> expectedSeenRoots =
        expectedBlocks.map(StateAndBlockSummary::getRoot).collect(toSet());

    assertThat(seenBlocks).containsExactlyInAnyOrderElementsOf(expectedSeenRoots);
  }

  protected abstract BlockMetadataStore createBlockMetadataStore(final ChainBuilder chainBuilder);
}
