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

package tech.pegasys.teku.core.stategenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.util.async.SafeFuture;

public class StateGeneratorTest {
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);

  @ParameterizedTest(name = "cache size: {0}, block batch size: {1}")
  @MethodSource("getParameters")
  public void shouldHandleValidChainFromGenesis(final int cacheSize, final int blockBatchSize)
      throws StateTransitionException {
    // Build a small chain
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);
    final List<SignedBlockAndState> newBlocksAndStates =
        chainBuilder
            .streamBlocksAndStates(
                genesis.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot())
            .collect(Collectors.toList());

    testRegenerateAllStates(cacheSize, blockBatchSize, genesis, 0, newBlocksAndStates);
  }

  @ParameterizedTest(name = "cache size: {0}, block batch size: {1}")
  @MethodSource("getParameters")
  public void shouldHandleValidPostGenesisChain(final int cacheSize, final int blockBatchSize)
      throws StateTransitionException {
    // Build a small chain
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);
    final SignedBlockAndState baseBlock = chainBuilder.getBlockAndStateAtSlot(5);
    final List<SignedBlockAndState> newBlocksAndStates =
        chainBuilder
            .streamBlocksAndStates(
                baseBlock.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot())
            .collect(Collectors.toList());

    testRegenerateAllStates(cacheSize, blockBatchSize, baseBlock, 0, newBlocksAndStates);
  }

  @ParameterizedTest(name = "cache size: {0}, block batch size: {1}")
  @MethodSource("getParameters")
  public void shouldHandleInvalidForkBlocks(final int cacheSize, final int blockBatchSize)
      throws StateTransitionException {
    // Build a small chain
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(5);
    final ChainBuilder fork = chainBuilder.fork();

    chainBuilder.generateBlocksUpToSlot(10);
    // Fork chain skips a block
    fork.generateBlockAtSlot(7);
    fork.generateBlocksUpToSlot(10);

    // Set base block so that fork blocks cannot be reconstructed
    final SignedBlockAndState baseBlock = chainBuilder.getBlockAndStateAtSlot(6);

    final List<SignedBlockAndState> newBlocksAndStates =
        chainBuilder
            .streamBlocksAndStates(
                baseBlock.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot())
            .collect(Collectors.toList());
    final List<SignedBeaconBlock> newForkBlocks =
        fork.streamBlocksAndStates(
                baseBlock.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot())
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    testRegenerateAllStates(
        cacheSize, blockBatchSize, baseBlock, 0, newBlocksAndStates, newForkBlocks);
  }

  @ParameterizedTest(name = "cache size: {0}, block batch size: {1}")
  @MethodSource("getParameters")
  public void shouldHandleForkBlocks(final int cacheSize, final int blockBatchSize)
      throws StateTransitionException {
    // Build a small chain
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(5);
    final SignedBlockAndState baseBlock = chainBuilder.getLatestBlockAndState();
    final ChainBuilder fork = chainBuilder.fork();

    chainBuilder.generateBlocksUpToSlot(10);
    // Fork chain skips a block
    fork.generateBlockAtSlot(7);
    fork.generateBlocksUpToSlot(10);

    final List<SignedBlockAndState> newBlocksAndStates =
        chainBuilder
            .streamBlocksAndStates(
                baseBlock.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot())
            .collect(Collectors.toList());
    fork.streamBlocksAndStates(baseBlock.getSlot().plus(UnsignedLong.ONE), fork.getLatestSlot())
        .forEach(newBlocksAndStates::add);

    testRegenerateAllStates(cacheSize, blockBatchSize, baseBlock, 1, newBlocksAndStates);
  }

  @ParameterizedTest(name = "cache size: {0}, block batch size: {1}")
  @MethodSource("getParameters")
  public void shouldHandleMultipleForks(final int cacheSize, final int blockBatchSize)
      throws StateTransitionException {
    // Build a small chain
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(5);
    final SignedBlockAndState baseBlock = chainBuilder.getLatestBlockAndState();
    // Branch at current block
    final ChainBuilder fork = chainBuilder.fork();

    chainBuilder.generateBlocksUpToSlot(10);
    // Fork chain skips a block
    final SignedBlockAndState forkBase = fork.generateBlockAtSlot(7);
    // Branch at current block
    final ChainBuilder fork2 = fork.fork();
    final ChainBuilder fork3 = fork.fork();
    final ChainBuilder fork4 = fork.fork();
    fork2.generateBlockAtSlot(9);
    fork3.generateBlockAtSlot(10);
    fork3.generateBlockAtSlot(11);
    fork.generateBlocksUpToSlot(10);

    final List<SignedBlockAndState> newBlocksAndStates =
        Streams.concat(
                chainBuilder.streamBlocksAndStates(
                    baseBlock.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot()),
                fork.streamBlocksAndStates(
                    baseBlock.getSlot().plus(UnsignedLong.ONE), fork.getLatestSlot()),
                fork2.streamBlocksAndStates(
                    forkBase.getSlot().plus(UnsignedLong.ONE), fork2.getLatestSlot()),
                fork3.streamBlocksAndStates(
                    forkBase.getSlot().plus(UnsignedLong.ONE), fork3.getLatestSlot()),
                fork4.streamBlocksAndStates(
                    forkBase.getSlot().plus(UnsignedLong.ONE), fork4.getLatestSlot()))
            .collect(Collectors.toList());

    testRegenerateAllStates(cacheSize, blockBatchSize, baseBlock, 2, newBlocksAndStates);
  }

  @Test
  public void produceStatesForBlocks_emptyNewBlockCollection() {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();

    testRegenerateAllStates(0, 10, genesis, 0, Collections.emptyList());
  }

  @Test
  public void regenerateAllStates_failOnMissingBlocks() throws StateTransitionException {
    testGeneratorWithMissingBlock(
        (generator, missingBlock) -> {
          SafeFuture<Void> result = generator.regenerateAllStates(StateHandler.NOOP);
          assertThatThrownBy(result::get)
              .hasCauseInstanceOf(IllegalStateException.class)
              .hasMessageContaining(
                  "Failed to retrieve required block "
                      + missingBlock.getRoot()
                      + ". Last processed block was at slot 0.");
        });
  }

  @Test
  public void regenerateStateForBlock_failOnMissingBlocks() throws StateTransitionException {
    testGeneratorWithMissingBlock(
        (generator, missingBlock) -> {
          SafeFuture<BeaconState> result =
              generator.regenerateStateForBlock(missingBlock.getRoot());
          assertThatThrownBy(result::get)
              .hasCauseInstanceOf(IllegalStateException.class)
              .hasMessageContaining(
                  "Failed to retrieve 1 / 3 blocks building on state at slot 0: "
                      + missingBlock.getRoot());
        });
  }

  @Test
  public void regenerateStateForBlock_blockPastTargetIsMissing() throws StateTransitionException {
    testGeneratorWithMissingBlock(
        (generator, missingBlock) -> {
          SignedBlockAndState target =
              chainBuilder.getBlockAndStateAtSlot(missingBlock.getSlot().minus(UnsignedLong.ONE));
          SafeFuture<BeaconState> result = generator.regenerateStateForBlock(target.getRoot());
          assertThat(result).isCompletedWithValue(target.getState());
        });
  }

  private void testGeneratorWithMissingBlock(
      BiConsumer<StateGenerator, SignedBeaconBlock> processor) throws StateTransitionException {
    // Build a small chain
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(5);
    final Map<Bytes32, SignedBeaconBlock> blockMap =
        chainBuilder
            .streamBlocksAndStates()
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));

    final HashTree tree =
        HashTree.builder().rootHash(genesis.getRoot()).blocks(blockMap.values()).build();
    // Create block provider that is missing some blocks
    final SignedBeaconBlock missingBlock =
        chainBuilder.getBlockAtSlot(genesis.getSlot().plus(UnsignedLong.valueOf(2)));
    blockMap.remove(missingBlock.getRoot());
    final BlockProvider blockProvider = BlockProvider.fromMap(blockMap);

    final StateGenerator generator = StateGenerator.create(tree, genesis, blockProvider);
    processor.accept(generator, missingBlock);
  }

  private void testRegenerateAllStates(
      final int cacheSize,
      final int blockBatchSize,
      final SignedBlockAndState rootBlockAndState,
      final int expectedCachedStateCount,
      final List<SignedBlockAndState> descendantBlocksAndStates) {
    testRegenerateAllStates(
        cacheSize,
        blockBatchSize,
        rootBlockAndState,
        expectedCachedStateCount,
        descendantBlocksAndStates,
        Collections.emptyList());
  }

  private void testRegenerateAllStates(
      final int cacheSize,
      final int blockBatchSize,
      final SignedBlockAndState rootBlockAndState,
      final int expectedCachedStateCount,
      final List<SignedBlockAndState> descendantBlocksAndStates,
      final List<SignedBeaconBlock> unconnectedBlocks) {
    testRegenerateAllStates(
        cacheSize,
        blockBatchSize,
        rootBlockAndState,
        expectedCachedStateCount,
        descendantBlocksAndStates,
        unconnectedBlocks,
        false);
    testRegenerateAllStates(
        cacheSize,
        blockBatchSize,
        rootBlockAndState,
        expectedCachedStateCount,
        descendantBlocksAndStates,
        unconnectedBlocks,
        true);
  }

  private void testRegenerateAllStates(
      final int cacheSize,
      final int blockBatchSize,
      final SignedBlockAndState rootBlockAndState,
      final int expectedCachedStateCount,
      final List<SignedBlockAndState> descendantBlocksAndStates,
      final List<SignedBeaconBlock> unconnectedBlocks,
      final boolean supplyAllKnownStates) {
    final List<SignedBeaconBlock> descendantBlocks =
        descendantBlocksAndStates.stream()
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    final List<SignedBlockAndState> allBlocksAndStates = new ArrayList<>();
    allBlocksAndStates.add(rootBlockAndState);
    allBlocksAndStates.addAll(descendantBlocksAndStates);
    final List<SignedBeaconBlock> allBlocks =
        allBlocksAndStates.stream().map(SignedBlockAndState::getBlock).collect(Collectors.toList());
    final Map<Bytes32, BeaconState> expectedResult =
        allBlocksAndStates.stream()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));

    // Create generator
    final HashTree blockTree =
        HashTree.builder()
            .rootHash(rootBlockAndState.getRoot())
            .block(rootBlockAndState.getBlock())
            .blocks(descendantBlocks)
            .blocks(unconnectedBlocks)
            .build();
    final BlockProvider blockProvider = BlockProvider.fromList(allBlocks);
    final StateGenerator generator =
        supplyAllKnownStates
            ? StateGenerator.create(
                blockTree, rootBlockAndState, blockProvider, expectedResult, 1000, cacheSize)
            : StateGenerator.create(
                blockTree,
                rootBlockAndState,
                blockProvider,
                Collections.emptyMap(),
                blockBatchSize,
                cacheSize);

    // Regenerate all states and collect results
    final List<SignedBlockAndState> results = new ArrayList<>();
    generator
        .regenerateAllStatesInternal(
            (block, state) -> results.add(new SignedBlockAndState(block, state)))
        .join();
    // Check that we don't cache more states than we're expected
    // We should only cache states for blocks that have multiple descendants
    assertThat(generator.countCachedStates()).isLessThanOrEqualTo(expectedCachedStateCount);

    // Verify results
    final Map<Bytes32, BeaconState> resultMap =
        results.stream()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
    // We shouldn't process any duplicates
    assertThat(resultMap.size()).isEqualTo(results.size());
    // Check that our expectations are met
    assertThat(resultMap.size()).isEqualTo(expectedResult.size());
    assertThat(resultMap).containsExactlyInAnyOrderEntriesOf(expectedResult);
    // Check states were / were not regenerated as expected
    if (supplyAllKnownStates) {
      // No states should be regenerated - they should all match the known state
      for (Bytes32 root : expectedResult.keySet()) {
        assertThat(resultMap.get(root)).isSameAs(expectedResult.get(root));
      }
    } else if (cacheSize == 0) {
      // All states should be regenerated and should not match the known states
      for (Bytes32 root : expectedResult.keySet()) {
        // Skip root state
        if (root.equals(rootBlockAndState.getRoot())) {
          continue;
        }
        assertThat(resultMap.get(root)).isNotSameAs(expectedResult.get(root));
      }
    }

    try {
      // Test generating each expected state 1 by 1
      for (SignedBlockAndState descendant : descendantBlocksAndStates) {
        final BeaconState stateResult =
            generator.regenerateStateForBlock(descendant.getRoot()).get();
        assertThat(stateResult)
            .isEqualToIgnoringGivenFields(
                descendant.getState(), "transitionCaches", "childrenViewCache", "backingNode");
      }
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static Stream<Arguments> getParameters() {
    Stream.Builder<Arguments> builder = Stream.builder();

    final List<Integer> stateCacheSizes = List.of(0, 1, 100);
    final List<Integer> blockBatchSizes = List.of(1, 5, 1000);
    for (Integer stateCacheSize : stateCacheSizes) {
      for (Integer blockBatchSize : blockBatchSizes) {
        builder.add(Arguments.of(stateCacheSize, blockBatchSize));
      }
    }

    return builder.build();
  }
}
