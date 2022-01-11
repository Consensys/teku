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
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ReexecutingExecutionPayloadBlockManager extends BlockManager {
  private static final Logger LOG = LogManager.getLogger();
  private static final int EXPIRES_IN_SLOT = 2;
  private static final Duration RECHECK_INTERVAL = Duration.ofSeconds(2);

  private final AtomicBoolean reexecutingExecutionPayload = new AtomicBoolean(false);
  private final Set<SignedBeaconBlock> pendingBlocksForEPReexecution = LimitedSet.create(10);
  private final AsyncRunner asyncRunner;

  private Optional<Cancellable> cancellable = Optional.empty();

  public ReexecutingExecutionPayloadBlockManager(
      RecentChainData recentChainData,
      BlockImporter blockImporter,
      PendingPool<SignedBeaconBlock> pendingBlocks,
      FutureItems<SignedBeaconBlock> futureBlocks,
      BlockValidator validator,
      final AsyncRunner asyncRunner) {
    super(recentChainData, blockImporter, pendingBlocks, futureBlocks, validator);
    this.asyncRunner = asyncRunner;
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
    cancellable.ifPresent(Cancellable::cancel);
    cancellable = Optional.empty();

    return super.doStop();
  }

  @Override
  public void onSlot(UInt64 slot) {
    super.onSlot(slot);
    pendingBlocksForEPReexecution.removeIf(
        block -> block.getSlot().isLessThan(slot.plus(EXPIRES_IN_SLOT)));
  }

  @Override
  public SafeFuture<BlockImportResult> importBlock(SignedBeaconBlock block) {
    return super.importBlock(block)
        .thenPeek(
            blockImportResult -> {
              if (requiresReexecution(blockImportResult)) {
                pendingBlocksForEPReexecution.add(block);
              }
            });
  }

  private void execute() {
    if (reexecutingExecutionPayload.compareAndSet(false, true)) {
      LOG.debug("re-executing execution payloads for queued blocks");
      SafeFuture.allOf(
              pendingBlocksForEPReexecution.stream()
                  .map(this::reexecuteExecutionPayload)
                  .toArray(SafeFuture[]::new))
          .alwaysRun(() -> reexecutingExecutionPayload.set(false))
          .reportExceptions();
    }
  }

  private SafeFuture<Void> reexecuteExecutionPayload(final SignedBeaconBlock block) {
    return super.importBlock(block)
        .thenAccept(
            blockImportResult -> {
              if (canBeRemoved(blockImportResult)) {
                pendingBlocksForEPReexecution.remove(block);
              }
            });
  }

  private boolean requiresReexecution(BlockImportResult blockImportResult) {
    return blockImportResult.hasFailedExecutingExecutionPayload();
  }

  private boolean canBeRemoved(BlockImportResult blockImportResult) {
    return blockImportResult.isSuccessful()
        || !blockImportResult.hasFailedExecutingExecutionPayload();
  }
}
