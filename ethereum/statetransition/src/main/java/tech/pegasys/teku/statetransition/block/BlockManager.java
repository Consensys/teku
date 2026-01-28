/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.statetransition.blobs.BlockEventsListener;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockManager extends Service
    implements SlotEventsChannel, BlockImportChannel, ReceivedBlockEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final BlockImporter blockImporter;
  private final BlockEventsListener blockEventsListener;
  private final PendingPool<SignedBeaconBlock> pendingBlocks;
  private final BlockValidator blockValidator;
  private final TimeProvider timeProvider;
  private final EventLogger eventLogger;

  private final FutureItems<SignedBeaconBlock> futureBlocks;
  // in the invalidBlockRoots map we are going to store blocks whose import result is invalid
  // and will not require any further retry. Descendants of these blocks will be considered invalid
  // as well.
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots;
  private final Subscribers<FailedPayloadExecutionSubscriber> failedPayloadExecutionSubscribers =
      Subscribers.create(true);
  private final Subscribers<PreImportBlockListener> preImportBlockSubscribers =
      Subscribers.create(true);

  private final Optional<BlockImportMetrics> blockImportMetrics;

  public BlockManager(
      final RecentChainData recentChainData,
      final BlockImporter blockImporter,
      final BlockEventsListener blockEventsListener,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final FutureItems<SignedBeaconBlock> futureBlocks,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final BlockValidator blockValidator,
      final TimeProvider timeProvider,
      final EventLogger eventLogger,
      final Optional<BlockImportMetrics> blockImportMetrics) {
    this.recentChainData = recentChainData;
    this.blockImporter = blockImporter;
    this.blockEventsListener = blockEventsListener;
    this.pendingBlocks = pendingBlocks;
    this.futureBlocks = futureBlocks;
    this.invalidBlockRoots = invalidBlockRoots;
    this.blockValidator = blockValidator;
    this.timeProvider = timeProvider;
    this.eventLogger = eventLogger;
    this.blockImportMetrics = blockImportMetrics;
  }

  @Override
  public SafeFuture<?> doStart() {
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<BlockImportAndBroadcastValidationResults> importBlock(
      final SignedBeaconBlock block,
      final BroadcastValidationLevel broadcastValidationLevel,
      final Optional<RemoteOrigin> origin) {
    LOG.trace("Preparing to import block: {}", block::toLogString);

    final BlockBroadcastValidator blockBroadcastValidator =
        blockValidator.initiateBroadcastValidation(block, broadcastValidationLevel);

    final SafeFuture<BlockImportResult> importResult =
        doImportBlock(block, Optional.empty(), blockBroadcastValidator, origin);

    // we want to intercept any early import exceptions happening before the consensus validation is
    // completed
    blockBroadcastValidator.attachToBlockImport(importResult);

    return SafeFuture.completedFuture(
        new BlockImportAndBroadcastValidationResults(
            importResult, blockBroadcastValidator.getResult()));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<InternalValidationResult> validateAndImportBlock(
      final SignedBeaconBlock block, final Optional<UInt64> arrivalTimestamp) {

    final Optional<BlockImportPerformance> blockImportPerformance;

    arrivalTimestamp.ifPresent(
        arrivalTime -> recentChainData.setBlockTimelinessFromArrivalTime(block, arrivalTime));

    if (blockImportMetrics.isPresent()) {
      final BlockImportPerformance performance =
          new BlockImportPerformance(timeProvider, blockImportMetrics.get());
      performance.arrival(recentChainData, block.getSlot(), arrivalTimestamp);
      performance.gossipValidation();
      blockImportPerformance = Optional.of(performance);
    } else {
      blockImportPerformance = Optional.empty();
    }

    /*
     * [REJECT] The block's parent (defined by block.parent_root) passes validation.
     */
    if (propagateInvalidity(block).isPresent()) {
      return SafeFuture.completedFuture(
          InternalValidationResult.reject("Block (or its parent) previously marked as invalid"));
    }

    final SafeFuture<InternalValidationResult> validationResult =
        blockValidator.validateGossip(block);
    validationResult.thenAccept(
        result -> {
          switch (result.code()) {
            case ACCEPT, SAVE_FOR_FUTURE ->
                doImportBlock(
                        block,
                        blockImportPerformance,
                        BlockBroadcastValidator.NOOP,
                        Optional.of(RemoteOrigin.GOSSIP))
                    .finish(err -> LOG.error("Failed to process received block.", err));

            // block failed gossip validation, let's drop it from the pool, so it won't be served
            // via RPC anymore. This should not be done on ignore result (i.e. duplicate blocks
            // could cause an unwanted drop)
            case REJECT -> blockEventsListener.removeAllForBlock(block.getSlotAndBlockRoot());
            case IGNORE -> {}
          }
        });
    return validationResult;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    pendingBlocks.onSlot(slot);
    futureBlocks.onSlot(slot);
    futureBlocks.prune(slot).forEach(this::importBlockIgnoringResult);
  }

  public void subscribeFailedPayloadExecution(final FailedPayloadExecutionSubscriber subscriber) {
    failedPayloadExecutionSubscribers.subscribe(subscriber);
  }

  public void subscribePreImportBlocks(final PreImportBlockListener subscriber) {
    preImportBlockSubscribers.subscribe(subscriber);
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {
    // No-op
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    blockEventsListener.removeAllForBlock(block.getSlotAndBlockRoot());
    pendingBlocks.remove(block);
    // Check if any pending blocks can now be imported
    final List<SignedBeaconBlock> children =
        pendingBlocks.getItemsDependingOn(block.getRoot(), false);
    children.forEach(pendingBlocks::remove);
    children.forEach(this::importBlockIgnoringResult);
  }

  private void importBlockIgnoringResult(final SignedBeaconBlock block) {
    // we don't care about origin here because flow calls this function for retries only
    doImportBlock(block, Optional.empty(), BlockBroadcastValidator.NOOP, Optional.empty())
        .finishStackTrace();
  }

  private SafeFuture<BlockImportResult> doImportBlock(
      final SignedBeaconBlock block,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final BlockBroadcastValidator blockBroadcastValidator,
      final Optional<RemoteOrigin> origin) {
    return handleInvalidBlock(block)
        .or(() -> handleKnownBlock(block))
        .orElseGet(
            () ->
                handleBlockImport(block, blockImportPerformance, blockBroadcastValidator, origin)
                    .thenPeek(
                        result -> lateBlockImportCheck(blockImportPerformance, block, result)));
  }

  private Optional<BlockImportResult> propagateInvalidity(final SignedBeaconBlock block) {
    final Optional<BlockImportResult> blockImportResult =
        Optional.ofNullable(invalidBlockRoots.get(block.getRoot()))
            .or(
                () -> {
                  if (invalidBlockRoots.containsKey(block.getParentRoot())) {
                    return Optional.of(BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);
                  }
                  return Optional.empty();
                });

    blockImportResult.ifPresent(result -> dropInvalidBlock(block, result));

    return blockImportResult;
  }

  private Optional<SafeFuture<BlockImportResult>> handleInvalidBlock(
      final SignedBeaconBlock block) {
    return propagateInvalidity(block).map(SafeFuture::completedFuture);
  }

  private Optional<SafeFuture<BlockImportResult>> handleKnownBlock(final SignedBeaconBlock block) {
    if (pendingBlocks.contains(block) || futureBlocks.contains(block)) {
      // Pending and future blocks can't have been executed yet so must be marked optimistic
      return Optional.of(SafeFuture.completedFuture(BlockImportResult.knownBlock(block, true)));
    }
    return recentChainData
        .isBlockOptimistic(block.getRoot())
        .map(
            isOptimistic ->
                SafeFuture.completedFuture(BlockImportResult.knownBlock(block, isOptimistic)));
  }

  private SafeFuture<BlockImportResult> handleBlockImport(
      final SignedBeaconBlock block,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final BlockBroadcastValidator blockBroadcastValidator,
      final Optional<RemoteOrigin> origin) {
    blockEventsListener.onNewBlock(block, origin);
    preImportBlockSubscribers.deliver(l -> l.onNewBlock(block, origin));

    return blockImporter
        .importBlock(block, blockImportPerformance, blockBroadcastValidator)
        .thenPeek(
            result -> {
              if (result.isSuccessful()) {
                LOG.trace("Imported block: {}", block);
              } else {
                switch (result.getFailureReason()) {
                  case UNKNOWN_PARENT -> {
                    // Add to the pending pool so it is triggered once the parent is imported
                    pendingBlocks.add(block);
                    // Check if the parent was imported while we were trying to import
                    // this block and if so, remove from the pendingPool again
                    // and process now We must add the block
                    // to the pending pool before this check happens to avoid race
                    // conditions between performing the check and the parent importing.
                    if (recentChainData.containsBlock(block.getParentRoot())) {
                      pendingBlocks.remove(block);
                      importBlockIgnoringResult(block);
                    }
                  }
                  case BLOCK_IS_FROM_FUTURE -> futureBlocks.add(block);
                  case FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING -> {
                    LOG.warn(
                        "Unable to import block {} with execution payload {}: Execution Client is still syncing",
                        block.toLogString(),
                        getExecutionPayloadInfoForLog(block));
                    failedPayloadExecutionSubscribers.deliver(
                        FailedPayloadExecutionSubscriber::onPayloadExecutionFailed, block);
                  }
                  case FAILED_EXECUTION_PAYLOAD_EXECUTION -> {
                    LOG.error(
                        "Unable to import block: Execution Client returned an error: {}",
                        result.getFailureCause().map(Throwable::getMessage).orElse(""));
                    failedPayloadExecutionSubscribers.deliver(
                        FailedPayloadExecutionSubscriber::onPayloadExecutionFailed, block);
                  }
                  case FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE -> {
                    logFailedBlockImport(block, result.getFailureReason());
                    blockEventsListener.enableBlockImportOnCompletion(block);
                  }
                  case FAILED_DATA_AVAILABILITY_CHECK_INVALID -> {
                    // Block's commitments and known blobSidecars are not matching.
                    // To be able to recover from this situation we remove all blobSidecars from the
                    // pool and discard.
                    // If next block builds on top of this one, we will re-download all blobSidecars
                    // and block again via RPC by root.
                    logFailedBlockImport(block, result.getFailureReason());
                    blockEventsListener.removeAllForBlock(block.getSlotAndBlockRoot());
                  }
                  case FAILED_BROADCAST_VALIDATION ->
                      LOG.warn(
                          "Unable to import block {} due to failed broadcast validation",
                          block.toLogString());

                  // let's avoid default: so we don't forget to explicitly handle new cases
                  case DOES_NOT_DESCEND_FROM_LATEST_FINALIZED,
                      FAILED_STATE_TRANSITION,
                      FAILED_WEAK_SUBJECTIVITY_CHECKS,
                      DESCENDANT_OF_INVALID_BLOCK -> {
                    logFailedBlockImport(block, result.getFailureReason());
                    dropInvalidBlock(block, result);
                  }
                  case BUILDER_WITHHOLD -> {
                    // normal flow, nothing to do
                  }
                  case INTERNAL_ERROR -> {
                    logFailedBlockImport(block, result.getFailureReason());
                    if (result
                        .getFailureCause()
                        .map(this::internalErrorToBeConsiderAsInvalidBlock)
                        .orElse(false)) {
                      dropInvalidBlock(block, result);
                    }
                  }
                }
              }
            });
  }

  private boolean internalErrorToBeConsiderAsInvalidBlock(final Throwable internalError) {
    if (internalError instanceof RejectedExecutionException
        || ExceptionUtil.hasCause(internalError, RejectedExecutionException.class)) {
      return false;
    }
    return true;
  }

  private void logFailedBlockImport(
      final SignedBeaconBlock block, final FailureReason failureReason) {
    LOG.trace("Unable to import block for reason {}: {}", failureReason, block);
  }

  private String getExecutionPayloadInfoForLog(final SignedBeaconBlock block) {
    return block
        .getMessage()
        .getBody()
        .getOptionalExecutionPayloadSummary()
        .map(ExecutionPayloadSummary::toLogString)
        .orElse("<none>");
  }

  private void dropInvalidBlock(
      final SignedBeaconBlock block, final BlockImportResult blockImportResult) {
    invalidBlockRoots.put(block.getMessage().hashTreeRoot(), blockImportResult);
    pendingBlocks.remove(block);
    blockEventsListener.removeAllForBlock(block.getSlotAndBlockRoot());

    pendingBlocks
        .getItemsDependingOn(block.getRoot(), true)
        .forEach(
            blockToDrop -> {
              invalidBlockRoots.put(
                  blockToDrop.getMessage().hashTreeRoot(),
                  BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);
              pendingBlocks.remove(blockToDrop);
              blockEventsListener.removeAllForBlock(blockToDrop.getSlotAndBlockRoot());
            });
  }

  private void lateBlockImportCheck(
      final Optional<BlockImportPerformance> maybeBlockImportPerformance,
      final SignedBeaconBlock block,
      final BlockImportResult blockImportResult) {
    maybeBlockImportPerformance.ifPresent(
        blockImportPerformance ->
            blockImportPerformance.processingComplete(eventLogger, block, blockImportResult));
  }

  public interface FailedPayloadExecutionSubscriber {
    void onPayloadExecutionFailed(SignedBeaconBlock block);
  }

  public interface PreImportBlockListener {
    void onNewBlock(SignedBeaconBlock block, Optional<RemoteOrigin> remoteOrigin);
  }
}
