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
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.metrics.TekuMetricCategory;

public class StateGeneratorFactory {
  private final ConcurrentHashMap<Bytes32, SafeFuture<SignedBlockAndState>> inProgressGeneration =
      new ConcurrentHashMap<>();
  private final AtomicInteger activeRegenerations = new AtomicInteger(0);
  private final Queue<Supplier<SafeFuture<SignedBlockAndState>>> queuedRegenerations =
      new ConcurrentLinkedQueue<>();

  public StateGeneratorFactory(final MetricsSystem metricsSystem) {
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
      final BlockProvider blockProvider,
      final Consumer<SignedBlockAndState> cacheHandler) {
    final SafeFuture<SignedBlockAndState> future = new SafeFuture<>();
    final SafeFuture<SignedBlockAndState> inProgress =
        inProgressGeneration.putIfAbsent(blockRoot, future);
    if (inProgress != null) {
      return inProgress;
    }
    Optional<Bytes32> maybeAncestorRoot = tree.getParent(blockRoot);
    while (maybeAncestorRoot.isPresent()) {
      final Bytes32 ancestorRoot = maybeAncestorRoot.get();
      final SafeFuture<SignedBlockAndState> parentFuture = inProgressGeneration.get(ancestorRoot);
      if (parentFuture != null) {
        final HashTree treeFromAncestor = tree.withRoot(ancestorRoot).build();
        return parentFuture.thenCompose(
            ancestorState ->
                regenerate(
                    blockRoot,
                    treeFromAncestor,
                    ancestorState,
                    blockProvider,
                    cacheHandler,
                    future));
      }
      maybeAncestorRoot = maybeAncestorRoot.flatMap(tree::getParent);
    }
    return regenerate(blockRoot, tree, baseBlockAndState, blockProvider, cacheHandler, future);
  }

  private SafeFuture<SignedBlockAndState> regenerate(
      final Bytes32 blockRoot,
      final HashTree tree,
      final SignedBlockAndState baseBlockAndState,
      final BlockProvider blockProvider,
      final Consumer<SignedBlockAndState> cacheHandler,
      final SafeFuture<SignedBlockAndState> future) {

    queuedRegenerations.add(
        () -> {
          final StateGenerator stateGenerator =
              StateGenerator.create(tree, baseBlockAndState, blockProvider);
          stateGenerator
              .regenerateStateForBlock(blockRoot)
              .thenPeek(
                  result -> {
                    cacheHandler.accept(result);
                    inProgressGeneration.remove(blockRoot);
                  })
              .catchAndRethrow(error -> inProgressGeneration.remove(blockRoot))
              .propagateTo(future);
          return future;
        });
    tryProcessNext();
    return future;
  }

  private void tryProcessNext() {
    int currentActiveCount = activeRegenerations.get();
    while (currentActiveCount < getActiveRegenerationLimit() && !queuedRegenerations.isEmpty()) {
      if (activeRegenerations.compareAndSet(currentActiveCount, currentActiveCount + 1)) {
        processNext();
        return;
      }
      currentActiveCount = activeRegenerations.get();
    }
  }

  private void processNext() {
    final Supplier<SafeFuture<SignedBlockAndState>> nextRegeneration = queuedRegenerations.poll();
    if (nextRegeneration == null) {
      activeRegenerations.decrementAndGet();
      return;
    }
    nextRegeneration
        .get()
        .always(
            () -> {
              activeRegenerations.decrementAndGet();
              tryProcessNext();
            });
  }

  private int getActiveRegenerationLimit() {
    return Math.max(2, Runtime.getRuntime().availableProcessors());
  }
}
