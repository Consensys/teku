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

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.datastructures.blocks.BlockTree;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;

public class StateGeneratorTest {
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);

  @ParameterizedTest(name = "cache size: {0}")
  @MethodSource("getCacheSize")
  public void shouldHandleValidChainFromGenesis(final int cacheSize)
      throws StateTransitionException {
    // Build a small chain
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);
    final List<SignedBlockAndState> newBlocksAndStates =
        chainBuilder
            .streamBlocksAndStates(
                genesis.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot())
            .collect(Collectors.toList());

    testRegenerateAllStates(cacheSize, genesis, newBlocksAndStates);
  }

  @ParameterizedTest(name = "cache size: {0}")
  @MethodSource("getCacheSize")
  public void shouldHandleValidPostGenesisChain(final int cacheSize)
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

    testRegenerateAllStates(cacheSize, baseBlock, newBlocksAndStates);
  }

  @ParameterizedTest(name = "cache size: {0}")
  @MethodSource("getCacheSize")
  public void shouldHandleInvalidForkBlocks(final int cacheSize) throws StateTransitionException {
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

    testRegenerateAllStates(cacheSize, baseBlock, newBlocksAndStates, newForkBlocks);
  }

  @ParameterizedTest(name = "cache size: {0}")
  @MethodSource("getCacheSize")
  public void shouldHandleForkBlocks(final int cacheSize) throws StateTransitionException {
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

    testRegenerateAllStates(cacheSize, baseBlock, newBlocksAndStates);
  }

  @ParameterizedTest(name = "cache size: {0}")
  @MethodSource("getCacheSize")
  public void shouldHandleMultipleForks(final int cacheSize) throws StateTransitionException {
    // Build a small chain
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(5);
    final SignedBlockAndState baseBlock = chainBuilder.getLatestBlockAndState();
    final ChainBuilder fork = chainBuilder.fork();

    chainBuilder.generateBlocksUpToSlot(10);
    // Fork chain skips a block
    final SignedBlockAndState forkBase = fork.generateBlockAtSlot(7);
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

    testRegenerateAllStates(cacheSize, baseBlock, newBlocksAndStates);
  }

  @Test
  public void produceStatesForBlocks_emptyNewBlockCollection() {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();

    testRegenerateAllStates(0, genesis, Collections.emptyList());
  }

  private void testRegenerateAllStates(
      final int cacheSize,
      final SignedBlockAndState rootBlockAndState,
      final List<SignedBlockAndState> descendantBlocksAndStates) {
    testRegenerateAllStates(
        cacheSize, rootBlockAndState, descendantBlocksAndStates, Collections.emptyList());
  }

  private void testRegenerateAllStates(
      final int cacheSize,
      final SignedBlockAndState rootBlockAndState,
      final List<SignedBlockAndState> descendantBlocksAndStates,
      final List<SignedBeaconBlock> unconnectedBlocks) {
    testRegenerateAllStates(
        cacheSize, rootBlockAndState, descendantBlocksAndStates, unconnectedBlocks, false);
    testRegenerateAllStates(
        cacheSize, rootBlockAndState, descendantBlocksAndStates, unconnectedBlocks, true);
  }

  private void testRegenerateAllStates(
      final int cacheSize,
      final SignedBlockAndState rootBlockAndState,
      final List<SignedBlockAndState> descendantBlocksAndStates,
      final List<SignedBeaconBlock> unconnectedBlocks,
      final boolean supplyAllKnownStates) {
    final List<SignedBeaconBlock> descendantBlocks =
        descendantBlocksAndStates.stream()
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    final Map<Bytes32, BeaconState> expectedResult =
        descendantBlocksAndStates.stream()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));

    // Create generator
    final BlockTree blockTree =
        BlockTree.builder()
            .rootBlock(rootBlockAndState.getBlock())
            .blocks(descendantBlocks)
            .blocks(unconnectedBlocks)
            .build();
    final StateGenerator generator =
        supplyAllKnownStates
            ? StateGenerator.create(blockTree, rootBlockAndState.getState(), expectedResult)
            : StateGenerator.create(blockTree, rootBlockAndState.getState());

    // Regenerate all states and collect results
    final List<StateAndBlockRoot> results = new ArrayList<>();
    generator.regenerateAllStates(
        (root, state) -> results.add(new StateAndBlockRoot(root, state)), cacheSize);

    // Verify results
    final Map<Bytes32, BeaconState> resultMap =
        results.stream()
            .collect(
                Collectors.toMap(StateAndBlockRoot::getBlockRoot, StateAndBlockRoot::getState));
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
        assertThat(resultMap.get(root)).isNotSameAs(expectedResult.get(root));
      }
    }

    // Test generating each expected state 1 by 1
    for (SignedBlockAndState descendant : descendantBlocksAndStates) {
      final BeaconState stateResult = generator.regenerateStateForBlock(descendant.getRoot());
      assertThat(stateResult)
          .isEqualToIgnoringGivenFields(
              descendant.getState(), "transitionCaches", "childrenViewCache", "backingNode");
    }
  }

  public static Stream<Arguments> getCacheSize() {
    return Stream.of(Arguments.of(0), Arguments.of(1), Arguments.of(100));
  }

  private static class StateAndBlockRoot {
    private final BeaconState state;
    private final Bytes32 blockRoot;

    private StateAndBlockRoot(final Bytes32 blockRoot, final BeaconState state) {
      this.state = state;
      this.blockRoot = blockRoot;
    }

    public BeaconState getState() {
      return state;
    }

    public Bytes32 getBlockRoot() {
      return blockRoot;
    }
  }
}
