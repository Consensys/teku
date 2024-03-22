/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.blobs;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

/**
 * This pool is designed to track chain tips blocks that has been attempted to import but failed due
 * to data unavailability. These are chain tips only because blocks with unknown parent will fail
 * with UNKNOWN_BLOCK and will be tracked in the block pendingPool.
 */
public class DataUnavailableBlockPool implements FinalizedCheckpointChannel {
  public static int MAX_CAPACITY = 10;
  private static final Logger LOG = LogManager.getLogger();

  private static final Duration WAIT_BEFORE_RETRY = Duration.ofSeconds(1);

  // this is a queue of chain tips
  private final Queue<SignedBeaconBlock> awaitingDataAvailabilityQueue =
      new ArrayBlockingQueue<>(MAX_CAPACITY);
  private final Spec spec;
  private final BlockManager blockManager;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final AsyncRunner asyncRunner;

  private Optional<Cancellable> delayedRetryTask = Optional.empty();
  private boolean blockImportInProgress = false;
  private boolean inSync = false;

  public DataUnavailableBlockPool(
      final Spec spec,
      final BlockManager blockManager,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.blockManager = blockManager;
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.asyncRunner = asyncRunner;
  }

  public synchronized void addDataUnavailableBlock(final SignedBeaconBlock block) {
    if (!inSync) {
      return;
    }

    boolean wasEmpty = awaitingDataAvailabilityQueue.isEmpty();
    if (!awaitingDataAvailabilityQueue.offer(block)) {
      final SignedBeaconBlock oldestBlock = awaitingDataAvailabilityQueue.poll();
      awaitingDataAvailabilityQueue.add(block);
      LOG.info(
          "Discarding block {} as data unavailable retry pool capacity exceeded",
          oldestBlock::toLogString);
      return;
    }
    if (wasEmpty) {
      tryToReimport();
    }
  }

  @Override
  public synchronized void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    final UInt64 latestFinalizedSlot = checkpoint.getEpochStartSlot(spec);
    awaitingDataAvailabilityQueue.removeIf(
        block -> block.getSlot().isLessThanOrEqualTo(latestFinalizedSlot));
  }

  public synchronized void onSyncingStatusChanged(boolean inSync) {
    this.inSync = inSync;

    delayedRetryTask.ifPresent(Cancellable::cancel);
    delayedRetryTask = Optional.empty();
    awaitingDataAvailabilityQueue.clear();
  }

  @VisibleForTesting
  boolean containsBlock(final SignedBeaconBlock block) {
    return awaitingDataAvailabilityQueue.contains(block);
  }

  private synchronized void tryToReimport() {

    if (blockImportInProgress) {
      return;
    }

    if (awaitingDataAvailabilityQueue.isEmpty()) {
      delayedRetryTask = Optional.empty();
      return;
    }

    // first block in queue with data available
    final Optional<SignedBeaconBlock> maybeSelectedBlock =
        awaitingDataAvailabilityQueue.stream().filter(this::isBlockTrackerCompleted).findFirst();

    if (maybeSelectedBlock.isPresent()) {
      final SignedBeaconBlock selectedBlock = maybeSelectedBlock.get();

      LOG.debug("Retrying import of block {}", selectedBlock::toLogString);
      awaitingDataAvailabilityQueue.remove(selectedBlock);

      blockImportInProgress = true;
      blockManager
          .importBlock(maybeSelectedBlock.get(), BroadcastValidationLevel.NOT_REQUIRED)
          .thenCompose(BlockImportAndBroadcastValidationResults::blockImportResult)
          .thenAccept(this::handleImportResult)
          .finish(error -> LOG.error("An error occurred during block import", error));

    } else {
      // the queue is not empty but no block has data available yet. Wait before recheck.
      delayedRetryTask =
          Optional.of(
              asyncRunner.runCancellableAfterDelay(
                  this::tryToReimport,
                  WAIT_BEFORE_RETRY,
                  throwable ->
                      LOG.error(
                          "An error occurred while executing deferred block import", throwable)));
    }
  }

  private synchronized void handleImportResult(final BlockImportResult blockImportResult) {
    blockImportInProgress = false;

    tryToReimport();
  }

  private boolean isBlockTrackerCompleted(final SignedBeaconBlock block) {
    return blockBlobSidecarsTrackersPool
        .getBlockBlobSidecarsTracker(block)
        .map(BlockBlobSidecarsTracker::isCompleted)
        .orElse(false);
  }
}
