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

import static tech.pegasys.teku.statetransition.util.PendingPoolFactory.BLOCK_HASH_TREE_ROOT_FUNCTION;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ReexecutingExecutionPayloadBlockManager extends BlockManager {
  private static final Logger LOG = LogManager.getLogger();
  protected static final int RETRY_SLOTS = 2;
  private static final int EXPIRES_IN_SLOTS = 50;
  private volatile UInt64 currentSlot = UInt64.ZERO;
  private static final Duration RECHECK_INTERVAL = Duration.ofSeconds(2);

  private final AtomicBoolean reexecutingExecutionPayload = new AtomicBoolean(false);
  private final Map<SignedBeaconBlock, UInt64> pendingBlocksForEPReexecution =
      LimitedMap.createIterable(10);
  private final AsyncRunner asyncRunner;
  private final PendingPool<SignedBeaconBlock> pendingBlocks;

  private Optional<Cancellable> cancellable = Optional.empty();

  public ReexecutingExecutionPayloadBlockManager(
      final RecentChainData recentChainData,
      final BlockImporter blockImporter,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final FutureItems<SignedBeaconBlock> futureBlocks,
      final BlockValidator validator,
      final TimeProvider timeProvider,
      final EventLogger eventLogger,
      final AsyncRunner asyncRunner,
      final Optional<BlockImportMetrics> blockImportMetrics) {
    super(
        recentChainData,
        blockImporter,
        pendingBlocks,
        futureBlocks,
        validator,
        timeProvider,
        eventLogger,
        blockImportMetrics);
    this.asyncRunner = asyncRunner;
    this.pendingBlocks = pendingBlocks;
    pendingBlocks.subscribeRequiredBlockRoot(this::requireAncestor);
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
  public void onSlot(final UInt64 slot) {
    super.onSlot(slot);
    this.currentSlot = slot;
    final List<SignedBeaconBlock> pendingBlocks =
        new ArrayList<>(pendingBlocksForEPReexecution.keySet());
    pendingBlocks.stream()
        .filter(block -> block.getSlot().plus(EXPIRES_IN_SLOTS).isLessThan(slot))
        .forEach(pendingBlocksForEPReexecution::remove);
  }

  @Override
  public SafeFuture<BlockImportResult> importBlock(SignedBeaconBlock block) {
    return super.importBlock(block)
        .thenPeek(
            blockImportResult -> {
              if (requiresReexecution(blockImportResult)) {
                pendingBlocksForEPReexecution.put(block, block.getSlot().plus(RETRY_SLOTS));
              }
            });
  }

  public void requireAncestor(final Bytes32 root) {
    if (pendingBlocksForEPReexecution.isEmpty()) {
      return;
    }
    final Bytes32 parentForFirstPending = pendingBlocks.getFirstRequiredAncestor(root);
    final Optional<SignedBeaconBlock> reExecuteBlock =
        pendingBlocksForEPReexecution.keySet().stream()
            .filter(
                block -> BLOCK_HASH_TREE_ROOT_FUNCTION.apply(block).equals(parentForFirstPending))
            .findFirst();
    reExecuteBlock.ifPresent(
        block -> pendingBlocksForEPReexecution.put(block, currentSlot.plus(RETRY_SLOTS)));
  }

  private void execute() {
    if (reexecutingExecutionPayload.compareAndSet(false, true)) {
      LOG.debug("re-executing execution payloads for queued blocks");
      SafeFuture.allOf(
              pendingBlocksForEPReexecution.entrySet().stream()
                  .filter(entry -> entry.getValue().isGreaterThanOrEqualTo(currentSlot))
                  .map(Entry::getKey)
                  .map(this::reexecuteExecutionPayload)
                  .toArray(SafeFuture[]::new))
          .alwaysRun(() -> reexecutingExecutionPayload.set(false))
          .ifExceptionGetsHereRaiseABug();
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
