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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BlockRootAndState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class StateGenerator {
  public static final int DEFAULT_STATE_CACHE_SIZE = 100;
  private static final Logger LOG = LogManager.getLogger();

  private final BlockProcessor blockProcessor = new BlockProcessor();
  private final HashTree blockTree;
  private final BlockProvider blockProvider;
  private final AsyncChainStateGenerator chainStateGenerator;

  private final StateCache stateCache;
  private final int blockBatchSize;

  private StateGenerator(
      final HashTree blockTree,
      final BlockProvider blockProvider,
      final AsyncChainStateGenerator chainStateGenerator,
      final StateCache stateCache,
      final int blockBatchSize) {
    checkArgument(blockBatchSize > 0, "Must provide a block batch size > 0");
    checkArgument(
        stateCache.containsKnownState(blockTree.getRootHash()), "Root state must be available");

    this.blockTree = blockTree;
    this.blockProvider = blockProvider;
    this.stateCache = stateCache;
    this.blockBatchSize = blockBatchSize;
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
        blockTree, blockProvider, chainStateGenerator, stateCache, blockBatchSize);
  }

  public SafeFuture<SignedBlockAndState> regenerateStateForBlock(final Bytes32 blockRoot) {
    final int blockCount = blockTree.size() - 1;
    LOG.info("Regenerate state for block {} by replaying {} blocks", blockRoot, blockCount);
    final long startTime = System.currentTimeMillis();

    return chainStateGenerator
        .generateTargetState(blockRoot)
        .thenPeek(
            result ->
                LOG.info(
                    "Completed regeneration of block {} at slot {} by replaying {} blocks. Took {}ms",
                    blockRoot,
                    result.getSlot(),
                    blockCount,
                    System.currentTimeMillis() - startTime));
  }

  public SafeFuture<Void> regenerateAllStates(final StateHandler stateHandler) {
    LOG.debug(
        "Regenerate all states for block tree of size {} rooted at block {}",
        blockTree.size(),
        blockTree.getRootHash());
    return regenerateAllStatesInternal(stateHandler).thenAccept(__ -> stateCache.clear());
  }

  @VisibleForTesting
  SafeFuture<?> regenerateAllStatesInternal(final StateHandler stateHandler) {
    final List<Bytes32> blockRoots = blockTree.preOrderStream().collect(Collectors.toList());
    if (blockRoots.size() == 0) {
      return SafeFuture.completedFuture(null);
    }

    // Break up blocks into batches
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

    return future;
  }

  @VisibleForTesting
  int countCachedStates() {
    return stateCache.countCachedStates();
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
              LOG.debug("Process {} blocks", blocks.size());
              BlockRootAndState currentState = lastProcessedState;
              for (int i = 0; i < blockRoots.size(); i++) {
                final Bytes32 blockRoot = blockRoots.get(i);
                final SignedBeaconBlock currentBlock = blocks.get(blockRoot);
                if (currentBlock == null) {
                  throw new IllegalStateException(
                      String.format(
                          "Failed to retrieve required block %s. Last processed block was at slot %s.",
                          blockRoot, lastProcessedState.getState().getSlot()));
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
                      LOG.debug("Regenerate missing state for block {}", parentRoot);
                      final List<Bytes32> remainingRoots = blockRoots.subList(i, blockRoots.size());
                      return chainStateGenerator
                          .generateTargetState(parentRoot)
                          .thenApply(SignedBlockAndState::getState)
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
                stateHandler.handle(currentBlock, postState);
                currentState = new BlockRootAndState(currentBlock.getRoot(), postState);
              }

              return SafeFuture.completedFuture(currentState);
            });
  }
}
