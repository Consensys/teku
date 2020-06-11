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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BlockTree;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.util.collections.LimitStrategy;
import tech.pegasys.teku.util.collections.LimitedMap;

/** Utility for regenerating block states given a block tree and a root state. */
public class StateGenerator {
  private static final int DEFAULT_STATE_CACHE_SIZE = 50;

  private final BlockTree blockTree;
  private final Map<Bytes32, BeaconState> knownStates = new HashMap<>();

  private StateGenerator(
      final BlockTree blockTree,
      final BeaconState rootState,
      final Map<Bytes32, BeaconState> knownStates) {
    final SignedBeaconBlock rootBlock = blockTree.getRootBlock();
    checkArgument(
        rootBlock.getStateRoot().equals(rootState.hash_tree_root()),
        "Root state must match the root block of the blockTree");
    this.blockTree = blockTree;
    this.knownStates.putAll(knownStates);
    this.knownStates.put(rootBlock.getRoot(), rootState);
  }

  public static StateGenerator create(final BlockTree blockTree, final BeaconState rootState) {
    return new StateGenerator(blockTree, rootState, Collections.emptyMap());
  }

  public static StateGenerator create(
      final BlockTree blockTree,
      final BeaconState rootState,
      final Map<Bytes32, BeaconState> knownStates) {
    return new StateGenerator(blockTree, rootState, knownStates);
  }

  /**
   * Regenerate a state for a single block.
   *
   * @param blockRoot The root of the block
   * @return
   */
  public BeaconState regenerateStateForBlock(final Bytes32 blockRoot) {
    return regenerateStateForBlock(blockRoot, new StateCache(0, knownStates));
  }

  private BeaconState regenerateStateForBlock(
      final Bytes32 blockRoot, final StateCache stateCache) {
    final Optional<BeaconState> knownState = stateCache.get(blockRoot);
    if (knownState.isPresent()) {
      return knownState.get();
    }

    // Walk from target block towards root of the tree, stopping when we find an available state
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    Optional<SignedBeaconBlock> curBlock = blockTree.getBlock(blockRoot);
    Optional<BeaconState> baseState = Optional.empty();
    while (curBlock.isPresent()) {
      final Bytes32 root = curBlock.get().getRoot();
      baseState = stateCache.get(root);
      if (baseState.isPresent()) {
        break;
      }
      blocks.add(curBlock.get());
      curBlock = blockTree.getBlock(curBlock.get().getParent_root());
    }
    checkArgument(
        blocks.size() > 0,
        "Block %s does not belong to this %s.",
        blockRoot,
        getClass().getSimpleName());

    // Process blocks in order
    BeaconState state = baseState.orElseThrow();
    SignedBeaconBlock block = null;
    for (int i = blocks.size() - 1; i >= 0; i--) {
      block = blocks.get(i);
      state = processBlock(state, block);
    }

    // Validate result and return
    if (!block.getStateRoot().equals(state.hash_tree_root())) {
      final String msg =
          String.format(
              "Failed to regenerate state for block root %s.  Generated state root %s does not match expected state root %s",
              blockRoot, state.hash_tree_root(), block.getStateRoot());
      throw new IllegalStateException(msg);
    }
    return state;
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
    final StateCache stateCache = new StateCache(maxCachedStates, knownStates);

    final Deque<SignedBeaconBlock> branchesToProcess = new ArrayDeque<>();
    final SignedBeaconBlock rootBlock = blockTree.getRootBlock();
    blockTree.getChildren(rootBlock).forEach(branchesToProcess::push);

    while (!branchesToProcess.isEmpty()) {
      SignedBeaconBlock branchBlock = branchesToProcess.pop();
      BeaconState preState =
          stateCache.getOrGenerate(
              branchBlock.getParent_root(), root -> regenerateStateForBlock(root, stateCache));
      while (branchBlock != null) {
        // Produce state for the current branch block
        final BeaconState state = preState;
        final SignedBeaconBlock block = branchBlock;
        final BeaconState branchBlockState =
            stateCache.get(branchBlock.getRoot()).orElseGet(() -> processBlock(state, block));
        stateHandler.handle(branchBlock.getRoot(), branchBlockState);

        // Process children
        final List<SignedBeaconBlock> children =
            new ArrayList<>(blockTree.getChildren(branchBlock.getRoot()));
        // Save branches for later processing
        if (children.size() > 1) {
          // Only cache the current state if there are other branches we need to come back to later
          stateCache.put(branchBlock.getRoot(), branchBlockState);
          children.subList(1, children.size()).forEach(branchesToProcess::push);
        }
        // Continue processing the first child
        branchBlock = children.isEmpty() ? null : children.get(0);
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

  private static class StateCache {
    private final Map<Bytes32, BeaconState> cache;
    private final Map<Bytes32, BeaconState> knownStates;

    public StateCache(final int maxCachedStates, final Map<Bytes32, BeaconState> knownStates) {
      this.cache = LimitedMap.create(maxCachedStates, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
      this.knownStates = knownStates;
    }

    /**
     * Get the state or generate and cache it.
     *
     * @param blockRoot The block root the state corresponds to
     * @param stateSupplier A state generator that will be invoked if the state isn't found
     * @return
     */
    public BeaconState getOrGenerate(
        final Bytes32 blockRoot, Function<Bytes32, BeaconState> stateSupplier) {
      return Optional.ofNullable(knownStates.get(blockRoot))
          .orElseGet(() -> cache.computeIfAbsent(blockRoot, stateSupplier));
    }

    public Optional<BeaconState> get(final Bytes32 blockRoot) {
      return Optional.ofNullable(knownStates.get(blockRoot))
          .or(() -> Optional.ofNullable(cache.get(blockRoot)));
    }

    public void put(final Bytes32 blockRoot, final BeaconState state) {
      if (!knownStates.containsKey(blockRoot)) {
        cache.put(blockRoot, state);
      }
    }
  }
}
