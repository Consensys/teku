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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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

  public StateGeneratorFactory(final MetricsSystem metricsSystem) {
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.BEACON,
        "inprogress_regenerations",
        "Number of state regeneration tasks currently in progress",
        inProgressGeneration::size);
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
  }
}
