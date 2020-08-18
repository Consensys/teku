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
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.metrics.TekuMetricCategory;

public class StateGenerationQueue {
  private final StateAndBlockProvider stateAndBlockProvider;
  private final MetricsSystem metricsSystem;

  private final ConcurrentHashMap<Bytes32, SafeFuture<SignedBlockAndState>> inProgressGeneration =
      new ConcurrentHashMap<>();
  private final AtomicInteger activeRegenerations = new AtomicInteger(0);
  private final Queue<RegenerationTask> queuedRegenerations = new ConcurrentLinkedQueue<>();
  private final IntSupplier activeRegenerationLimit;
  private final Counter duplicateRegenerationCounter;
  private final Counter newRegenerationCounter;
  private final Counter rebasedRegenerationCounter;

  StateGenerationQueue(
      final StateAndBlockProvider stateAndBlockProvider,
      final MetricsSystem metricsSystem,
      final IntSupplier activeRegenerationLimit) {
    this.metricsSystem = metricsSystem;
    this.stateAndBlockProvider = stateAndBlockProvider;
    this.activeRegenerationLimit = activeRegenerationLimit;

    final LabelledMetric<Counter> labelledCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "regenerations_total",
            "Total number of regenerations requested",
            "type");
    duplicateRegenerationCounter = labelledCounter.labels("duplicate");
    newRegenerationCounter = labelledCounter.labels("new");
    rebasedRegenerationCounter = labelledCounter.labels("rebase");
  }

  public static StateGenerationQueue create(
      final StateAndBlockProvider stateProvider, final MetricsSystem metricsSystem) {
    return new StateGenerationQueue(
        stateProvider,
        metricsSystem,
        () -> Math.max(2, Runtime.getRuntime().availableProcessors()));
  }

  public void startMetrics() {
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.BEACON,
        "regenerations_requested",
        "Number of state regeneration tasks requested but not yet completed",
        inProgressGeneration::size);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.BEACON,
        "regenerations_active",
        "Number of state regeneration tasks actively being processed",
        activeRegenerations::get);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.BEACON,
        "regenerations_queued",
        "Number of state regeneration tasks queued for later processing",
        queuedRegenerations::size);
  }

  public SafeFuture<SignedBlockAndState> regenerateStateForBlock(
      final Bytes32 blockRoot,
      final HashTree tree,
      final SignedBlockAndState baseBlockAndState,
      final Optional<Bytes32> epochBoundaryRoot,
      final BlockProvider blockProvider,
      final Consumer<SignedBlockAndState> cacheHandler) {
    return regenerateStateForBlock(
        new RegenerationTask(
            blockRoot,
            tree,
            baseBlockAndState,
            epochBoundaryRoot,
            blockProvider,
            stateAndBlockProvider,
            cacheHandler));
  }

  public SafeFuture<SignedBlockAndState> regenerateStateForBlock(final RegenerationTask task) {
    final SafeFuture<SignedBlockAndState> future = new SafeFuture<>();
    final SafeFuture<SignedBlockAndState> inProgress =
        inProgressGeneration.putIfAbsent(task.getBlockRoot(), future);
    if (inProgress != null) {
      duplicateRegenerationCounter.inc();
      return inProgress;
    }
    Optional<Bytes32> maybeAncestorRoot = task.getTree().getParent(task.getBlockRoot());
    while (maybeAncestorRoot.isPresent()) {
      final Bytes32 ancestorRoot = maybeAncestorRoot.get();
      final SafeFuture<SignedBlockAndState> parentFuture = inProgressGeneration.get(ancestorRoot);
      if (parentFuture != null) {
        rebasedRegenerationCounter.inc();
        parentFuture
            .thenAccept(ancestorState -> queueRegeneration(task.rebase(ancestorState)))
            .finish(
                error -> {
                  // Remove if regeneration fails.
                  inProgressGeneration.remove(task.getBlockRoot(), future);
                  future.completeExceptionally(error);
                });
        return future;
      }
      maybeAncestorRoot =
          maybeAncestorRoot
              // Don't find ancestor of the root hash
              .filter(root -> !root.equals(task.getTree().getRootHash()))
              .flatMap(task.getTree()::getParent);
    }
    newRegenerationCounter.inc();
    queueRegeneration(task);
    return future;
  }

  private void queueRegeneration(final RegenerationTask task) {
    queuedRegenerations.add(task);
    tryProcessNext();
  }

  private void tryProcessNext() {
    int currentActiveCount = activeRegenerations.get();
    while (currentActiveCount < activeRegenerationLimit.getAsInt()
        && !queuedRegenerations.isEmpty()) {
      if (activeRegenerations.compareAndSet(currentActiveCount, currentActiveCount + 1)) {
        processNext();
      }
      currentActiveCount = activeRegenerations.get();
    }
  }

  private void processNext() {
    final RegenerationTask task = queuedRegenerations.poll();
    if (task == null) {
      activeRegenerations.decrementAndGet();
      return;
    }
    task.regenerate()
        .whenComplete(
            (result, error) -> {
              final SafeFuture<SignedBlockAndState> future =
                  inProgressGeneration.remove(task.getBlockRoot());
              if (error != null) {
                future.completeExceptionally(error);
              } else {
                future.complete(result);
              }
            })
        .alwaysRun(
            () -> {
              activeRegenerations.decrementAndGet();
              tryProcessNext();
            })
        .reportExceptions();
  }

  public static class RegenerationTask {
    private static final Logger LOG = LogManager.getLogger();
    private final HashTree tree;
    private final SignedBlockAndState baseBlockAndState;
    private final Optional<Bytes32> epochBoundaryRoot;
    private final BlockProvider blockProvider;
    private final StateAndBlockProvider stateAndBlockProvider;
    private final Bytes32 blockRoot;
    private final Consumer<SignedBlockAndState> cacheHandler;

    public RegenerationTask(
        final Bytes32 blockRoot,
        final HashTree tree,
        final SignedBlockAndState baseBlockAndState,
        final Optional<Bytes32> epochBoundaryRoot,
        final BlockProvider blockProvider,
        final StateAndBlockProvider stateAndBlockProvider,
        final Consumer<SignedBlockAndState> cacheHandler) {
      this.tree = tree;
      this.baseBlockAndState = baseBlockAndState;
      this.epochBoundaryRoot = epochBoundaryRoot;
      this.blockProvider = blockProvider;
      this.stateAndBlockProvider = stateAndBlockProvider;
      this.blockRoot = blockRoot;
      this.cacheHandler = cacheHandler;
    }

    public Bytes32 getBlockRoot() {
      return blockRoot;
    }

    public HashTree getTree() {
      return tree;
    }

    public RegenerationTask rebase(final SignedBlockAndState newBaseBlockAndState) {
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
      return new RegenerationTask(
          blockRoot,
          treeFromAncestor,
          newBaseBlockAndState,
          epochBoundaryRoot.filter(treeFromAncestor::contains),
          blockProvider,
          stateAndBlockProvider,
          cacheHandler);
    }

    private SafeFuture<RegenerationTask> resolveAgainstLatestEpochBoundary() {
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

    public SafeFuture<SignedBlockAndState> regenerate() {
      return resolveAgainstLatestEpochBoundary().thenCompose(RegenerationTask::regenerateState);
    }

    private SafeFuture<SignedBlockAndState> regenerateState() {
      final StateGenerator stateGenerator =
          StateGenerator.create(tree, baseBlockAndState, blockProvider);
      return stateGenerator.regenerateStateForBlock(blockRoot).thenPeek(cacheHandler);
    }
  }
}
