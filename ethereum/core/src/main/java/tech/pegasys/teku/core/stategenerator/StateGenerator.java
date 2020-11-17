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
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
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
      final StateAndBlockSummary rootBlockAndState,
      final BlockProvider blockProvider) {
    return create(blockTree, rootBlockAndState, blockProvider, Collections.emptyMap());
  }

  public static StateGenerator create(
      final HashTree blockTree,
      final StateAndBlockSummary rootBlockAndState,
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
      final StateAndBlockSummary rootBlockAndState,
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
}
