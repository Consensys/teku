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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.core.stategenerator.AsyncChainStateGenerator.DEFAULT_BLOCK_BATCH_SIZE;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BlockRootAndState;
import tech.pegasys.teku.util.async.SafeFuture;

public class StateGenerator {
  public static final int DEFAULT_STATE_CACHE_SIZE = 50;

  private final BlockProcessor blockProcessor = new BlockProcessor();
  private final HashTree blockTree;
  private final BlockProvider blockProvider;
  private final StateCache stateCache;
  private final AsyncChainStateGenerator chainStateGenerator;
  private final int blockBatchSize;

  private StateGenerator(
      final HashTree blockTree,
      final BlockProvider blockProvider,
      final StateCache stateCache,
      final AsyncChainStateGenerator chainStateGenerator,
      final int blockBatchSize) {
    this.blockBatchSize = blockBatchSize;
    checkArgument(
        stateCache.containsKnownState(blockTree.getRootHash()), "Root state must be available");

    this.blockTree = blockTree;
    this.blockProvider = blockProvider;
    this.stateCache = stateCache;
    this.chainStateGenerator = chainStateGenerator;
  }

  public static StateGenerator create(
      final HashTree blockTree,
      final SignedBlockAndState rootBlockAndState,
      final BlockProvider blockProvider) {
    return create(blockTree, rootBlockAndState, blockProvider, Collections.emptyMap());
  }

  public static StateGenerator create(
      final HashTree blockTree,
      final SignedBlockAndState rootBlockAndState,
      final BlockProvider blockProvider,
      final Map<Bytes32, BeaconState> knownStates) {
    return create(
        blockTree,
        rootBlockAndState,
        blockProvider,
        knownStates,
        DEFAULT_BLOCK_BATCH_SIZE,
        DEFAULT_STATE_CACHE_SIZE);
  }

  public static StateGenerator create(
      final HashTree blockTree,
      final SignedBlockAndState rootBlockAndState,
      final BlockProvider blockProvider,
      final Map<Bytes32, BeaconState> knownStates,
      final int blockBatchSize,
      final int stateCacheSize) {
    checkArgument(
        rootBlockAndState.getRoot().equals(blockTree.getRootHash()),
        "Provided root block must match the root of the provided block tree");

    final Map<Bytes32, BeaconState> availableStates = new HashMap<>(knownStates);
    availableStates.put(rootBlockAndState.getRoot(), rootBlockAndState.getState());
    final StateCache stateCache = new StateCache(stateCacheSize, availableStates);

    final AsyncChainStateGenerator chainStateGenerator =
        AsyncChainStateGenerator.create(blockTree, blockProvider, stateCache::get);
    return new StateGenerator(
        blockTree, blockProvider, stateCache, chainStateGenerator, blockBatchSize);
  }

  public SafeFuture<BeaconState> regenerateStateForBlock(final Bytes32 blockRoot) {
    return chainStateGenerator.generateTargetState(blockRoot);
  }

  public SafeFuture<?> regenerateAllStates(final StateHandler stateHandler) {
    // Skip 1 root because we must already have this state
    final List<Bytes32> blockRoots =
        blockTree.preOrderStream().skip(1).collect(Collectors.toList());
    if (blockRoots.size() == 0) {
      return SafeFuture.completedFuture(null);
    }

    // Break up blocks into batches
    // TODO - is there a way to partition the stream directly?
    final List<List<Bytes32>> blockBatches = Lists.partition(blockRoots, blockBatchSize);
    // Request and process each batch of blocks in order
    final Bytes32 rootHash = blockTree.getRootHash();
    final BeaconState rootState = stateCache.get(rootHash).orElseThrow();
    final BlockRootAndState rootAndState = new BlockRootAndState(rootHash, rootState);
    SafeFuture<BlockRootAndState> future =
        regenerateAllStatesForBatch(blockBatches.get(0), stateCache, rootAndState, stateHandler);
    for (int i = 1; i < blockBatches.size(); i++) {
      final List<Bytes32> batch = blockBatches.get(i);
      future =
          future.thenCompose(
              state -> regenerateAllStatesForBatch(batch, stateCache, state, stateHandler));
    }

    stateCache.clear();
    return future;
  }

  private SafeFuture<BlockRootAndState> regenerateAllStatesForBatch(
      final List<Bytes32> blockRoots,
      final StateCache stateCache,
      final BlockRootAndState lastProcessedState,
      final StateHandler stateHandler) {
    return blockProvider
        .getBlocks(blockRoots)
        .thenCompose(
            (blocks) -> {
              BlockRootAndState currentState = lastProcessedState;
              for (int i = 0; i < blockRoots.size(); i++) {
                final Bytes32 blockRoot = blockRoots.get(i);
                final SignedBeaconBlock currentBlock = blocks.get(blockRoot);
                if (currentBlock == null) {
                  throw new IllegalStateException(
                      "Failed to retrieve required block: " + blockRoot);
                }

                BeaconState postState = stateCache.get(currentBlock.getRoot()).orElse(null);
                if (postState == null) {
                  // Generate post state
                  final Bytes32 parentRoot = currentBlock.getParent_root();
                  // Find pre-state to build on
                  final BeaconState preState;
                  if (currentState.getBlockRoot().equals(parentRoot)) {
                    preState = currentState.getState();
                  } else {
                    final Optional<BeaconState> maybePreState = stateCache.get(parentRoot);
                    if (maybePreState.isPresent()) {
                      preState = maybePreState.get();
                    } else {
                      final List<Bytes32> remainingRoots = blockRoots.subList(i, blockRoots.size());
                      return chainStateGenerator
                          .generateTargetState(parentRoot)
                          .thenApply(parentState -> new BlockRootAndState(parentRoot, parentState))
                          .thenCompose(
                              (lastState) ->
                                  regenerateAllStatesForBatch(
                                      remainingRoots, stateCache, lastState, stateHandler));
                    }
                  }

                  // Cache state if other branches exist that might need it
                  if (blockTree.countChildren(parentRoot) > 1) {
                    stateCache.put(parentRoot, preState);
                  }
                  postState = blockProcessor.process(preState, currentBlock);
                }

                blockProcessor.assertBlockAndStateMatch(currentBlock, postState);
                stateHandler.handle(currentBlock.getRoot(), postState);
                currentState = new BlockRootAndState(currentBlock.getRoot(), postState);
              }

              return SafeFuture.completedFuture(currentState);
            });
  }
}
