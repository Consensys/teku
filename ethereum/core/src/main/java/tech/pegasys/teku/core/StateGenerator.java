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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BlockTree;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.util.collections.LimitStrategy;
import tech.pegasys.teku.util.collections.LimitedMap;

/** Utility for regenerating block states given a block tree and a root state. */
public class StateGenerator {
  private static final int DEFAULT_STATE_CACHE_SIZE = 50;

  final BlockTree blockTree;
  final BeaconState rootState;

  public StateGenerator(final BlockTree blockTree, final BeaconState rootState) {
    checkArgument(
        blockTree.getRootBlock().getStateRoot().equals(rootState.hash_tree_root()),
        "Base state must match the base block of the blockTree");
    this.blockTree = blockTree;
    this.rootState = rootState;
  }

  /**
   * Regenerate a state for a single block.
   *
   * @param blockRoot The root of the block
   * @return
   */
  public BeaconState regenerateStateForBlock(final Bytes32 blockRoot) {
    return regenerateStateForBlock(blockRoot, Collections.emptyMap());
  }

  private BeaconState regenerateStateForBlock(
      final Bytes32 blockRoot, final Map<Bytes32, BeaconState> stateCache) {
    final Bytes32 rootBlockHash = blockTree.getRootBlock().getRoot();
    if (rootBlockHash.equals(blockRoot)) {
      return rootState;
    }

    // Walk from target block towards root of the tree, stopping when we find an available state
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    Optional<SignedBeaconBlock> block = blockTree.getBlock(blockRoot);
    BeaconState baseState = null;
    while (block.isPresent()) {
      final Bytes32 root = block.get().getRoot();
      baseState = root.equals(rootBlockHash) ? rootState : stateCache.get(block.get().getRoot());
      if (baseState != null) {
        break;
      }
      blocks.add(block.get());
      block = blockTree.getBlock(block.get().getParent_root());
    }
    checkArgument(
        blocks.size() > 0,
        "Block %s does not belong to this %s.",
        blockRoot,
        getClass().getSimpleName());

    // Process blocks in order
    Collections.reverse(blocks);
    BeaconState prevState = baseState;
    SignedBeaconBlock currentBlock = null;
    BeaconState currentState = null;
    for (int i = 0; i < blocks.size(); i++) {
      currentBlock = blocks.get(i);
      currentState = processBlock(prevState, currentBlock);
      prevState = currentState;
    }

    // Validate result and return
    if (!currentBlock.getStateRoot().equals(currentState.hash_tree_root())) {
      final String msg =
          String.format(
              "Failed to regenerate state for block root %s.  Generated state root %s does not match expected state root %s",
              blockRoot, prevState.hash_tree_root(), currentBlock.getStateRoot());
      throw new IllegalStateException(msg);
    }
    return prevState;
  }

  /**
   * Regenerate all states in the block tree.
   *
   * @param stateHandler A handler to process each state as it is generated.
   */
  public void regenerateAllStates(final StateHandler stateHandler) {
    regenerateAllStates(stateHandler, DEFAULT_STATE_CACHE_SIZE);
  }

  void regenerateAllStates(final StateHandler stateHandler, final int maxCachedStates) {
    final Map<Bytes32, BeaconState> branchStateCache =
        LimitedMap.create(maxCachedStates, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);

    final Deque<SignedBeaconBlock> branchesToProcess = new ArrayDeque<>();
    final SignedBeaconBlock rootBlock = blockTree.getRootBlock();
    blockTree.getChildren(rootBlock).forEach(branchesToProcess::push);

    while (!branchesToProcess.isEmpty()) {
      SignedBeaconBlock branchBlock = branchesToProcess.pop();
      BeaconState preState =
          branchStateCache.computeIfAbsent(
              branchBlock.getParent_root(),
              root -> regenerateStateForBlock(root, branchStateCache));
      while (branchBlock != null) {
        // Produce state for the current branch block
        final BeaconState branchBlockState = processBlock(preState, branchBlock);
        stateHandler.handle(branchBlock.getRoot(), branchBlockState);

        // Process children
        final List<SignedBeaconBlock> children =
            new ArrayList<>(blockTree.getChildren(branchBlock.getRoot()));
        // Save branches for later processing
        if (children.size() > 1) {
          branchStateCache.put(branchBlock.getRoot(), branchBlockState);
          children.stream().skip(1).forEach(branchesToProcess::push);
        }
        // Continue processing the first child
        branchBlock = children.stream().findFirst().orElse(null);
        preState = branchBlockState;
      }
    }
  }

  private BeaconState processBlock(final BeaconState preState, final SignedBeaconBlock block) {
    StateTransition stateTransition = new StateTransition();
    try {
      final BeaconState postState = stateTransition.initiate(preState, block);
      // Validate that state matches expectation
      if (!block.getMessage().getState_root().equals(postState.hash_tree_root())) {
        throw new IllegalStateException(getFailedStateGenerationError(block));
      }
      return postState;
    } catch (StateTransitionException e) {
      throw new IllegalStateException(getFailedStateGenerationError(block), e);
    }
  }

  private String getFailedStateGenerationError(final SignedBeaconBlock block) {
    return String.format(
        "Unable to produce state for block at slot %s (%s)", block.getSlot(), block.getRoot());
  }

  public interface StateHandler {
    void handle(final Bytes32 blockRoot, final BeaconState state);
  }
}
