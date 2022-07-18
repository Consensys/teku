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

package tech.pegasys.teku.statetransition.block;

import com.google.common.base.Throwables;
import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.storage.server.ShuttingDownException;

public class FailedExecutionPool {
  private static final Logger LOG = LogManager.getLogger();
  static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);
  static final Duration SHORT_DELAY = Duration.ofSeconds(2);
  private final Queue<SignedBeaconBlock> awaitingExecution = new ArrayBlockingQueue<>(10);
  private final BlockManager blockManager;
  private final AsyncRunner asyncRunner;

  private Optional<SignedBeaconBlock> retryingBlock = Optional.empty();

  private Duration currentDelay = SHORT_DELAY;

  public FailedExecutionPool(final BlockManager blockManager, final AsyncRunner asyncRunner) {
    this.blockManager = blockManager;
    this.asyncRunner = asyncRunner;
  }

  public synchronized void addFailedBlock(final SignedBeaconBlock block) {
    if (retryingBlock.isEmpty()) {
      retryingBlock = Optional.of(block);
      scheduleNextRetry(block);
    } else {
      if (!awaitingExecution.offer(block)) {
        LOG.info(
            "Discarding block {} as execution retry pool capacity exceeded",
            LogFormatter.formatBlock(block.getSlot(), block.getRoot()));
      }
    }
  }

  private synchronized void handleExecutionResult(
      final SignedBeaconBlock block, final BlockImportResult importResult) {
    if (importResult.hasFailedExecutingExecutionPayload()) {
      currentDelay = currentDelay.multipliedBy(2);
      if (currentDelay.compareTo(MAX_RETRY_DELAY) > 0) {
        currentDelay = MAX_RETRY_DELAY;
      }
      if (awaitingExecution.isEmpty() || isTimeout(importResult)) {
        scheduleNextRetry(block);
      } else {
        // Try a different block
        final SignedBeaconBlock nextBlock = awaitingExecution.poll();
        awaitingExecution.remove(nextBlock);
        awaitingExecution.add(block);
        scheduleNextRetry(nextBlock);
      }
    } else {
      currentDelay = SHORT_DELAY;
      retryingBlock = Optional.ofNullable(awaitingExecution.poll());
      retryingBlock.ifPresent(
          nextBlock -> {
            awaitingExecution.remove(nextBlock);
            retryExecution(nextBlock);
          });
    }
  }

  private boolean isTimeout(final BlockImportResult importResult) {
    return importResult
        .getFailureCause()
        .map(error -> error instanceof TimeoutException)
        .orElse(false);
  }

  private synchronized void scheduleNextRetry(final SignedBeaconBlock block) {
    asyncRunner
        .runAfterDelay(() -> retryExecution(block), currentDelay)
        .ifExceptionGetsHereRaiseABug();
  }

  private synchronized void retryExecution(final SignedBeaconBlock block) {
    SafeFuture.of(() -> blockManager.importBlock(block))
        .exceptionally(BlockImportResult::internalError)
        .thenAccept(result -> handleExecutionResult(block, result))
        .finish(
            error -> {
              if (!(Throwables.getRootCause(error) instanceof ShuttingDownException)) {
                LOG.error("Failed to schedule payload re-execution", error);
              }
              retryingBlock.ifPresent(this::scheduleNextRetry);
            });
  }
}
