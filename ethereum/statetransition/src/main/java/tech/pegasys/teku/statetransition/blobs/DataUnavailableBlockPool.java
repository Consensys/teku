/*
 * Copyright ConsenSys Software Inc., 2023
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

import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.block.BlockManager;

public class DataUnavailableBlockPool {
  private static final Logger LOG = LogManager.getLogger();

  private static final Duration WAIT_BEFORE_RETRY = Duration.ofSeconds(1);

  private final Queue<SignedBeaconBlock> awaitingDataAvailabilityQueue =
      new ArrayBlockingQueue<>(10);
  private final BlockManager blockManager;
  private final BlobSidecarPool blobSidecarPool;
  private final AsyncRunner asyncRunner;

  private Optional<Cancellable> delayedRetryTask = Optional.empty();
  private boolean blockImportInProgress = false;
  private boolean inSync = false;

  public DataUnavailableBlockPool(
      final BlockManager blockManager,
      final BlobSidecarPool blobSidecarPool,
      final AsyncRunner asyncRunner) {
    this.blockManager = blockManager;
    this.blobSidecarPool = blobSidecarPool;
    this.asyncRunner = asyncRunner;
  }

  public synchronized void addDataUnavailableBlock(final SignedBeaconBlock block) {
    if (!inSync) {
      return;
    }

    boolean wasEmpty = awaitingDataAvailabilityQueue.isEmpty();
    if (!awaitingDataAvailabilityQueue.offer(block)) {
      LOG.info(
          "Discarding block {} as execution retry pool capacity exceeded", block.toLogString());
      return;
    }
    if (wasEmpty) {
      tryToReimport();
    }
  }

  public synchronized void onSyncingStatusChanged(boolean inSync) {
    this.inSync = inSync;

    delayedRetryTask.ifPresent(Cancellable::cancel);
    delayedRetryTask = Optional.empty();
    awaitingDataAvailabilityQueue.clear();
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
      awaitingDataAvailabilityQueue.remove(selectedBlock);

      blockImportInProgress = true;
      blockManager
          .importBlock(maybeSelectedBlock.get())
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
    return blobSidecarPool
        .getBlockBlobSidecarsTracker(block)
        .map(BlockBlobSidecarsTracker::isCompleted)
        .orElse(false);
  }
}
