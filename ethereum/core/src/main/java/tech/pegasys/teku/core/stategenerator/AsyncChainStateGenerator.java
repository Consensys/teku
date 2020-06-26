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

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.util.async.SafeFuture;

class AsyncChainStateGenerator {
  public static final int DEFAULT_BLOCK_BATCH_SIZE = 250;

  private final HashTree blockTree;
  private final BlockProvider blockProvider;
  private final StateProvider stateProvider;
  private final int blockBatchSize;

  private AsyncChainStateGenerator(
      final HashTree blockTree,
      final BlockProvider blockProvider,
      final StateProvider stateProvider,
      final int blockBatchSize) {
    this.blockTree = blockTree;
    this.blockProvider = blockProvider;
    this.stateProvider = stateProvider;
    this.blockBatchSize = blockBatchSize;
  }

  public static AsyncChainStateGenerator create(
      final HashTree blockTree,
      final BlockProvider blockProvider,
      final StateProvider stateProvider) {
    return new AsyncChainStateGenerator(
        blockTree, blockProvider, stateProvider, DEFAULT_BLOCK_BATCH_SIZE);
  }

  public SafeFuture<BeaconState> generateTargetState(final Bytes32 targetRoot) {
    if (!blockTree.contains(targetRoot)) {
      return SafeFuture.failedFuture(
          new IllegalArgumentException("Target root is unknown: " + targetRoot));
    }

    final SafeFuture<BeaconState> lastState = new SafeFuture<>();
    generateStates(
            targetRoot,
            (block, state) -> {
              if (block.getRoot().equals(targetRoot)) {
                lastState.complete(state);
              }
            })
        .finish(
            // Make sure future is completed
            () ->
                lastState.completeExceptionally(
                    new IllegalStateException("Failed to generate state for " + targetRoot)),
            lastState::completeExceptionally);

    return lastState;
  }

  public SafeFuture<?> generateStates(final Bytes32 targetRoot, final StateHandler handler) {
    return SafeFuture.of(
        () -> {
          // Build chain from target root to the first ancestor with a known state
          final AtomicReference<BeaconState> baseState = new AtomicReference<>(null);
          final List<Bytes32> chain =
              blockTree.collectChainRoots(
                  targetRoot,
                  (currentRoot) -> {
                    stateProvider.getState(currentRoot).ifPresent(baseState::set);
                    return baseState.get() == null;
                  });

          if (baseState.get() == null) {
            throw new IllegalArgumentException("Unable to find base state to build on");
          }

          if (chain.size() == 0) {
            throw new IllegalStateException("Failed to retrieve chain");
          }

          // Process chain in batches
          final List<List<Bytes32>> blockBatches = Lists.partition(chain, blockBatchSize);
          // Request and process each batch of blocks in order
          SafeFuture<BeaconState> future =
              processBlockBatch(blockBatches.get(0), baseState.get(), handler);
          for (int i = 1; i < blockBatches.size(); i++) {
            final List<Bytes32> blockBatch = blockBatches.get(i);
            future = future.thenCompose(state -> processBlockBatch(blockBatch, state, handler));
          }
          return future;
        });
  }

  private SafeFuture<BeaconState> processBlockBatch(
      final List<Bytes32> blockRoots, final BeaconState startState, final StateHandler handler) {
    checkArgument(startState != null, "Must provide start state");
    return blockProvider
        .getBlocks(blockRoots)
        .thenApply(
            blocks -> {
              final List<SignedBeaconBlock> chainBlocks =
                  blockRoots.stream()
                      .map(blocks::get)
                      .filter(Objects::nonNull)
                      .collect(Collectors.toList());
              if (chainBlocks.size() < blockRoots.size()) {
                final String missingBlocks =
                    blockRoots.stream()
                        .filter(root -> !blocks.containsKey(root))
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                throw new IllegalStateException("Failed to retrieve blocks: " + missingBlocks);
              }

              final ChainStateGenerator chainStateGenerator =
                  ChainStateGenerator.create(chainBlocks, startState, true);
              final AtomicReference<BeaconState> lastState = new AtomicReference<>(null);
              chainStateGenerator.generateStates(
                  (block, state) -> {
                    lastState.set(state);
                    handler.handle(block, state);
                  });

              return lastState.get();
            });
  }
}
