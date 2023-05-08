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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.datastructures.blocks.ImportedBlockListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarPool;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockManager extends Service
    implements SlotEventsChannel, BlockImportChannel, BlockImportNotifications {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final BlockImporter blockImporter;
  private final BlobSidecarPool blobSidecarPool;
  private final PendingPool<SignedBeaconBlock> pendingBlocks;
  private final BlockValidator validator;
  private final TimeProvider timeProvider;
  private final EventLogger eventLogger;

  private final FutureItems<SignedBeaconBlock> futureBlocks;
  // in the invalidBlockRoots map we are going to store blocks whose import result is invalid
  // and will not require any further retry. Descendants of these blocks will be considered invalid
  // as well.
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots;
  private final Subscribers<ImportedBlockListener> receivedBlockSubscribers =
      Subscribers.create(true);
  private final Subscribers<FailedPayloadExecutionSubscriber> failedPayloadExecutionSubscribers =
      Subscribers.create(true);

  private final Subscribers<DataUnavailableSubscriber> dataUnavailableSubscribers =
      Subscribers.create(true);

  private final Optional<BlockImportMetrics> blockImportMetrics;

  public BlockManager(
      final RecentChainData recentChainData,
      final BlockImporter blockImporter,
      final BlobSidecarPool blobSidecarPool,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final FutureItems<SignedBeaconBlock> futureBlocks,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final BlockValidator validator,
      final TimeProvider timeProvider,
      final EventLogger eventLogger,
      final Optional<BlockImportMetrics> blockImportMetrics) {
    this.recentChainData = recentChainData;
    this.blockImporter = blockImporter;
    this.blobSidecarPool = blobSidecarPool;
    this.pendingBlocks = pendingBlocks;
    this.futureBlocks = futureBlocks;
    this.invalidBlockRoots = invalidBlockRoots;
    this.validator = validator;
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
  public SafeFuture<BlockImportResult> importBlock(final SignedBeaconBlock block) {
    LOG.trace("Preparing to import block: {}", block::toLogString);
    return doImportBlock(block, Optional.empty());
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<InternalValidationResult> validateAndImportBlock(
      final SignedBeaconBlock block) {

    final Optional<BlockImportPerformance> blockImportPerformance;

    if (blockImportMetrics.isPresent()) {
      final BlockImportPerformance performance =
          new BlockImportPerformance(timeProvider, blockImportMetrics.get());
      performance.arrival(recentChainData, block.getSlot());
      blockImportPerformance = Optional.of(performance);
    } else {
      blockImportPerformance = Optional.empty();
    }

    if (propagateInvalidity(block).isPresent()) {
      return SafeFuture.completedFuture(
          InternalValidationResult.reject("Block (or its parent) previously marked as invalid"));
    }

    SafeFuture<InternalValidationResult> validationResult = validator.validate(block);
    validationResult.thenAccept(
        result -> {
          if (result.code().equals(ValidationResultCode.ACCEPT)
              || result.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
            doImportBlock(block, blockImportPerformance)
                .finish(err -> LOG.error("Failed to process received block.", err));
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

  public void subscribeToReceivedBlocks(ImportedBlockListener importedBlockListener) {
    receivedBlockSubscribers.subscribe(importedBlockListener);
  }

  private void notifyReceivedBlockSubscribers(
      final SignedBeaconBlock signedBeaconBlock, final boolean executionOptimistic) {
    receivedBlockSubscribers.forEach(
        s -> s.onBlockImported(signedBeaconBlock, executionOptimistic));
  }

  public void subscribeFailedPayloadExecution(final FailedPayloadExecutionSubscriber subscriber) {
    failedPayloadExecutionSubscribers.subscribe(subscriber);
  }

  public void subscribeDataUnavailable(final DataUnavailableSubscriber subscriber) {
    dataUnavailableSubscribers.subscribe(subscriber);
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block) {
    final Bytes32 blockRoot = block.getRoot();
    blobSidecarPool.removeAllForBlock(blockRoot);
    pendingBlocks.remove(block);
    // Check if any pending blocks can now be imported
    final List<SignedBeaconBlock> children = pendingBlocks.getItemsDependingOn(blockRoot, false);
    children.forEach(pendingBlocks::remove);
    children.forEach(this::importBlockIgnoringResult);
  }

  private void importBlockIgnoringResult(final SignedBeaconBlock block) {
    doImportBlock(block, Optional.empty()).ifExceptionGetsHereRaiseABug();
  }

  private SafeFuture<BlockImportResult> doImportBlock(
      final SignedBeaconBlock block,
      final Optional<BlockImportPerformance> blockImportPerformance) {
    return handleInvalidBlock(block)
        .or(() -> handleKnownBlock(block))
        .orElseGet(
            () ->
                handleBlockImport(block, blockImportPerformance)
                    .thenPeek(
                        result -> lateBlockImportCheck(blockImportPerformance, block, result)))
        .thenPeek(
            result -> {
              if (result.isSuccessful()) {
                notifyReceivedBlockSubscribers(block, result.isImportedOptimistically());
              }
            });
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
      final Optional<BlockImportPerformance> blockImportPerformance) {

    blobSidecarPool.onNewBlock(block);

    return blockImporter
        .importBlock(block, blockImportPerformance)
        .thenPeek(
            result -> {
              if (result.isSuccessful()) {
                LOG.trace("Imported block: {}", block);
              } else {
                switch (result.getFailureReason()) {
                  case UNKNOWN_PARENT:
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
                    break;
                  case BLOCK_IS_FROM_FUTURE:
                    futureBlocks.add(block);
                    break;
                  case FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING:
                    LOG.warn(
                        "Unable to import block {} with execution payload {}: Execution Client is still syncing",
                        block.toLogString(),
                        getExecutionPayloadInfoForLog(block));
                    failedPayloadExecutionSubscribers.deliver(
                        FailedPayloadExecutionSubscriber::onPayloadExecutionFailed, block);
                    break;
                  case FAILED_EXECUTION_PAYLOAD_EXECUTION:
                    LOG.error(
                        "Unable to import block: Execution Client returned an error: {}",
                        result.getFailureCause().map(Throwable::getMessage).orElse(""));
                    failedPayloadExecutionSubscribers.deliver(
                        FailedPayloadExecutionSubscriber::onPayloadExecutionFailed, block);
                    break;
                  case FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE:
                    LOG.warn(
                        "Unable to import block {} due to data unavailability",
                        block.toLogString());
                    dataUnavailableSubscribers.deliver(
                        DataUnavailableSubscriber::onDataUnavailable, block);
                    break;
                  default:
                    LOG.trace(
                        "Unable to import block for reason {}: {}",
                        result.getFailureReason(),
                        block);
                    dropInvalidBlock(block, result);
                }
              }
            });
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
    final Bytes32 blockRoot = block.getRoot();

    invalidBlockRoots.put(block.getMessage().hashTreeRoot(), blockImportResult);
    pendingBlocks.remove(block);
    blobSidecarPool.removeAllForBlock(blockRoot);

    pendingBlocks
        .getItemsDependingOn(blockRoot, true)
        .forEach(
            blockToDrop -> {
              invalidBlockRoots.put(
                  blockToDrop.getMessage().hashTreeRoot(),
                  BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);
              pendingBlocks.remove(blockToDrop);
              blobSidecarPool.removeAllForBlock(blockToDrop.getRoot());
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

  public interface DataUnavailableSubscriber {
    void onDataUnavailable(SignedBeaconBlock block);
  }
}
