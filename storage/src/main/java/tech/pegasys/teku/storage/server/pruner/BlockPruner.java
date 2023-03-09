/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.pruner;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.ShuttingDownException;

public class BlockPruner extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Database database;
  private final AsyncRunner asyncRunner;
  private final Duration pruneInterval;

  private Optional<Cancellable> scheduledPruner = Optional.empty();

  public BlockPruner(
      final Spec spec,
      final Database database,
      final AsyncRunner asyncRunner,
      final Duration pruneInterval) {
    this.spec = spec;
    this.database = database;
    this.asyncRunner = asyncRunner;
    this.pruneInterval = pruneInterval;
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    scheduledPruner =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::pruneBlocks,
                Duration.ZERO,
                pruneInterval,
                error -> LOG.error("Failed to prune old blocks", error)));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    scheduledPruner.ifPresent(Cancellable::cancel);
    return SafeFuture.COMPLETE;
  }

  private void pruneBlocks() {
    final Optional<Checkpoint> finalizedCheckpoint = database.getFinalizedCheckpoint();
    if (finalizedCheckpoint.isEmpty()) {
      LOG.debug("Not pruning as no finalized checkpoint is available.");
      return;
    }
    final UInt64 finalizedEpoch = finalizedCheckpoint.get().getEpoch();
    final UInt64 earliestEpochToKeep = finalizedEpoch.minusMinZero(getEpochsToKeep(finalizedEpoch));
    final UInt64 earliestSlotToKeep = spec.computeStartSlotAtEpoch(earliestEpochToKeep);
    if (earliestSlotToKeep.isZero()) {
      LOG.debug("Not pruning as epochs to keep includes genesis");
      return;
    }
    LOG.info("Pruning finalized blocks before slot {}", earliestSlotToKeep);
    try {
      database.pruneFinalizedBlocks(earliestSlotToKeep.decrement());
      LOG.info("Finalized blocks before slot {} have been pruned.", earliestSlotToKeep);
    } catch (ShuttingDownException | RejectedExecutionException ex) {
      LOG.debug("Shutting down", ex);
    }
  }

  private int getEpochsToKeep(final UInt64 finalizedEpoch) {
    return spec.getSpecConfig(finalizedEpoch).getMinEpochsForBlockRequests();
  }
}
