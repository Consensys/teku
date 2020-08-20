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

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockProvider;
import tech.pegasys.teku.core.stategenerator.CachingTaskQueue.CacheableTask;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class StateGenerationTask implements CacheableTask<Bytes32, SignedBlockAndState> {
  private static final Logger LOG = LogManager.getLogger();
  private final HashTree tree;
  private final SignedBlockAndState baseBlockAndState;
  protected final Optional<Bytes32> epochBoundaryRoot;
  private final BlockProvider blockProvider;
  protected final StateAndBlockProvider stateAndBlockProvider;
  private final Bytes32 blockRoot;

  public StateGenerationTask(
      final Bytes32 blockRoot,
      final HashTree tree,
      final SignedBlockAndState baseBlockAndState,
      final Optional<Bytes32> epochBoundaryRoot,
      final BlockProvider blockProvider,
      final StateAndBlockProvider stateAndBlockProvider) {
    this.tree = tree;
    this.baseBlockAndState = baseBlockAndState;
    this.epochBoundaryRoot = epochBoundaryRoot;
    this.blockProvider = blockProvider;
    this.stateAndBlockProvider = stateAndBlockProvider;
    this.blockRoot = blockRoot;
  }

  @Override
  public Bytes32 getKey() {
    return blockRoot;
  }

  @Override
  public Stream<Bytes32> streamIntermediateSteps() {
    final Bytes32 rootParent = tree.getParent(tree.getRootHash()).orElseThrow();
    return Stream.iterate(
            tree.getParent(blockRoot),
            Optional::isPresent,
            current -> current.flatMap(tree::getParent))
        .flatMap(Optional::stream)
        // The root's parent is in the tree but is worse that the current starting point so exclude
        .filter(ancestorRoot -> !ancestorRoot.equals(rootParent));
  }

  @Override
  public StateGenerationTask rebase(final SignedBlockAndState newBaseBlockAndState) {
    final Bytes32 newBaseRoot = newBaseBlockAndState.getRoot();
    if (!tree.contains(newBaseRoot)) {
      LOG.warn(
          "Attempting to rebase a task for {} onto a starting state that is not a required ancestor ({} at slot {})",
          blockRoot,
          newBaseRoot,
          newBaseBlockAndState.getSlot());
      return this;
    }
    final HashTree treeFromAncestor =
        tree.withRoot(newBaseRoot).block(newBaseBlockAndState.getBlock()).build();
    return new StateGenerationTask(
        blockRoot,
        treeFromAncestor,
        newBaseBlockAndState,
        epochBoundaryRoot.filter(treeFromAncestor::contains),
        blockProvider,
        stateAndBlockProvider);
  }

  private SafeFuture<StateGenerationTask> resolveAgainstLatestEpochBoundary() {
    SafeFuture<Optional<SignedBlockAndState>> epochBoundaryBaseFuture =
        epochBoundaryRoot
            .map(stateAndBlockProvider::getBlockAndState)
            .orElseGet(() -> SafeFuture.completedFuture(Optional.empty()));

    return epochBoundaryBaseFuture.thenApply(
        newBase -> {
          if (newBase.isEmpty()) {
            return this;
          }
          return rebase(newBase.get());
        });
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> performTask() {
    return resolveAgainstLatestEpochBoundary()
        .thenCompose(StateGenerationTask::regenerateState)
        .thenApply(Optional::of);
  }

  protected SafeFuture<SignedBlockAndState> regenerateState() {
    final StateGenerator stateGenerator =
        StateGenerator.create(tree, baseBlockAndState, blockProvider);
    return stateGenerator.regenerateStateForBlock(blockRoot);
  }
}
