package tech.pegasys.teku.statetransition.blobs;

import com.google.common.base.Throwables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.storage.server.ShuttingDownException;

import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class DataUnavailableBlockPool {
  private static final Logger LOG = LogManager.getLogger();

  private final Queue<SignedBeaconBlock> awaitingDataAvailabilityQueue = new ArrayBlockingQueue<>(10);
  private final BlockManager blockManager;
  private final BlobSidecarPool blobSidecarPool;
  private final AsyncRunner asyncRunner;

  private Optional<SignedBeaconBlock> retryingBlock = Optional.empty();

  private Duration currentDelay = Duration.ofSeconds(4);

  private boolean inSync = false;

  public DataUnavailableBlockPool(final BlockManager blockManager, final BlobSidecarPool blobSidecarPool, final  AsyncRunner asyncRunner) {
    this.blockManager = blockManager;
    this.blobSidecarPool = blobSidecarPool;
    this.asyncRunner = asyncRunner;
  }

  public synchronized void addDataUnavailableBlockBlock(final SignedBeaconBlock block) {
    if (retryingBlock.isEmpty()) {
      retryingBlock = Optional.of(block);
      scheduleNextRetry();
    } else {
      if (retryingBlock.get().equals(block) || awaitingDataAvailabilityQueue.contains(block)) {
        // Already retrying this block.
        return;
      }
      if (!awaitingDataAvailabilityQueue.offer(block)) {
        LOG.info(
                "Discarding block {} as execution retry pool capacity exceeded", block.toLogString());
      }
    }

    Optional<BlockBlobSidecarsTracker> asd = blobSidecarPool.getBlockBlobSidecarsTracker(block);

  }

  private synchronized void scheduleNextRetry() {
    retryingBlock.ifPresent(
            block ->
                    asyncRunner
                            .runAfterDelay(() -> retryExecution(block), currentDelay)
                            .ifExceptionGetsHereRaiseABug());
  }

  private synchronized void retryExecution(final SignedBeaconBlock block) {
    LOG.info("Retrying execution of block {}", block.toLogString());
    SafeFuture.of(() -> blockManager.importBlock(block))
            .exceptionally(BlockImportResult::internalError)
            .thenAccept(result -> handleExecutionResult(block, result))
            .finish(
                    error -> {
                      if (!(Throwables.getRootCause(error) instanceof ShuttingDownException)) {
                        LOG.error("Failed to schedule payload re-execution", error);
                      }
                      scheduleNextRetry();
                    });
  }

  private boolean isDataNotAvailableResult(final BlockImportResult importResult) {
    return Optional.ofNullable(importResult.getFailureReason()).map(failureReason -> failureReason.equals(FailureReason.FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE))
            .orElse(false);
  }

  private synchronized void handleExecutionResult(
          final SignedBeaconBlock block, final BlockImportResult importResult) {
    if (importResult.hasFailedExecutingExecutionPayload()) {

      if (awaitingDataAvailabilityQueue.isEmpty() || isDataNotAvailableResult(importResult)) {
        scheduleNextRetry();
      } else {
        // Try a different block
        final SignedBeaconBlock nextBlock = awaitingDataAvailabilityQueue.remove();
        awaitingDataAvailabilityQueue.add(block);
        retryingBlock = Optional.of(nextBlock);
        scheduleNextRetry();
      }
    } else {
      retryingBlock = Optional.ofNullable(awaitingDataAvailabilityQueue.poll());
      retryingBlock.ifPresent(this::retryExecution);
    }
  }


  void onSyncingStatusChanged(boolean inSync) {
    this.inSync = inSync;
  }
}
