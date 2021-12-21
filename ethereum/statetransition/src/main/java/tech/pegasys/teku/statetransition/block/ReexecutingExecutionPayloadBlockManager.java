/*
 * Copyright 2021 ConsenSys AG.
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

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ReexecutingExecutionPayloadBlockManager extends BlockManager {
  private static final Logger LOG = LogManager.getLogger();
  private static final int EXPIRES_IN_SLOT = 2;
  private static final Duration RECHECK_INTERVAL = Duration.ofSeconds(2);

  private final AtomicBoolean reexecutingExecutionPayload = new AtomicBoolean(false);
  private final Set<SignedBeaconBlock> pendingBlocks = LimitedSet.create(10);
  private final AsyncRunner asyncRunner;

  private Optional<Cancellable> cancellable = Optional.empty();

  private ReexecutingExecutionPayloadBlockManager(
      RecentChainData recentChainData,
      BlockImporter blockImporter,
      PendingPool<SignedBeaconBlock> pendingBlocks,
      FutureItems<SignedBeaconBlock> futureBlocks,
      BlockValidator validator,
      final AsyncRunner asyncRunner) {
    super(recentChainData, blockImporter, pendingBlocks, futureBlocks, validator);
    this.asyncRunner = asyncRunner;
  }

  public static BlockManager create(
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final FutureItems<SignedBeaconBlock> futureBlocks,
      final RecentChainData recentChainData,
      final BlockImporter blockImporter,
      final BlockValidator validator,
      final AsyncRunner asyncRunner) {
    return new ReexecutingExecutionPayloadBlockManager(
        recentChainData, blockImporter, pendingBlocks, futureBlocks, validator, asyncRunner);
  }

  @Override
  public SafeFuture<?> doStart() {
    return super.doStart()
        .thenRun(
            () ->
                cancellable =
                    Optional.of(
                        asyncRunner.runWithFixedDelay(
                            this::execute,
                            RECHECK_INTERVAL,
                            error -> LOG.error("Failed to reexecute execution payloads", error))));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return super.doStop()
        .thenRun(
            () -> {
              cancellable.ifPresent(Cancellable::cancel);
              cancellable = Optional.empty();
            });
  }

  @Override
  public void onSlot(UInt64 slot) {
    super.onSlot(slot);
    pendingBlocks.removeIf(block -> block.getSlot().isLessThan(slot.plus(EXPIRES_IN_SLOT)));
  }

  @Override
  public SafeFuture<BlockImportResult> importBlock(SignedBeaconBlock block) {
    return super.importBlock(block)
        .thenPeek(
            blockImportResult -> {
              if (requiresReexecution(blockImportResult)) {
                pendingBlocks.add(block);
              }
            });
  }

  private void execute() {
    if (reexecutingExecutionPayload.compareAndSet(false, true)) {
      LOG.debug("re-executing execution payloads for queued blocks");
      SafeFuture.allOfFailFast(
              pendingBlocks.stream()
                  .map(this::reexecuteExecutionPayload)
                  .toArray(SafeFuture[]::new))
          .thenPeek(__ -> reexecutingExecutionPayload.set(false))
          .exceptionally(
              error -> {
                if (error instanceof StopExecutionError) {
                  LOG.error(
                      "An error occurred which prevents additional payload executions: {} reason: {}",
                      error.getMessage(),
                      error.getCause().getMessage());
                  return null;
                }
                throw new RuntimeException(error);
              })
          .reportExceptions();
    }
  }

  private SafeFuture<Void> reexecuteExecutionPayload(final SignedBeaconBlock block) {
    return super.importBlock(block)
        .thenAccept(
            blockImportResult -> {
              if (canBeRemoved(blockImportResult)) {
                pendingBlocks.remove(block);
              }
              applySubsequentExecutionPreventionCheck(blockImportResult);
            });
  }

  private boolean requiresReexecution(BlockImportResult blockImportResult) {
    final FailureReason failureReason = blockImportResult.getFailureReason();
    return failureReason == FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION
        || failureReason == FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING;
  }

  private boolean canBeRemoved(BlockImportResult blockImportResult) {
    if (blockImportResult.isSuccessful()) {
      return true;
    }
    final FailureReason failureReason = blockImportResult.getFailureReason();
    return failureReason != FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION
        && failureReason != FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING;
  }

  private void applySubsequentExecutionPreventionCheck(BlockImportResult blockImportResult) {
    final FailureReason failureReason = blockImportResult.getFailureReason();
    if (failureReason == FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION
        || failureReason == FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING) {
      throw new StopExecutionError(blockImportResult);
    }
  }

  private static class StopExecutionError extends Error {
    StopExecutionError(final BlockImportResult blockImportResult) {
      super(
          blockImportResult.getFailureReason().toString(),
          blockImportResult.getFailureCause().orElseThrow(RuntimeException::new));
    }
  }
}
