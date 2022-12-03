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
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.datastructures.blocks.ImportedBlockListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.blobs.BlobsManager;
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
  private final BlobsManager blobsManager;
  private final PendingPool<SignedBeaconBlock> pendingBlocks;
  private final BlockValidator validator;
  private final TimeProvider timeProvider;
  private final EventLogger eventLogger;

  private final FutureItems<SignedBeaconBlock> futureBlocks;
  // in the invalidBlockRoots map we are going to store blocks whose import result is invalid
  // and will not require any further retry. Descendants of these blocks will be considered invalid
  // as well.
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots =
      LimitedMap.createSynchronized(500);
  private final Subscribers<ImportedBlockListener> receivedBlockSubscribers =
      Subscribers.create(true);
  private final Subscribers<FailedPayloadExecutionSubscriber> failedPayloadExecutionSubscribers =
      Subscribers.create(true);

  private final Optional<BlockImportMetrics> blockImportMetrics;

  public BlockManager(
      final RecentChainData recentChainData,
      final BlockImporter blockImporter,
      final BlobsManager blobsManager,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final FutureItems<SignedBeaconBlock> futureBlocks,
      final BlockValidator validator,
      final TimeProvider timeProvider,
      final EventLogger eventLogger,
      final Optional<BlockImportMetrics> blockImportMetrics) {
    this.recentChainData = recentChainData;
    this.blockImporter = blockImporter;
    this.blobsManager = blobsManager;
    this.pendingBlocks = pendingBlocks;
    this.futureBlocks = futureBlocks;
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
    return doImportBlockAndBlobsSidecar(block, Optional.empty());
  }

  public SafeFuture<InternalValidationResult> validateAndImportBlock(
      final SignedBeaconBlock block) {

    return validateAndImportBlockAndBlobsSidecar(block, Optional.empty());
  }

  public SafeFuture<InternalValidationResult> validateAndImportBlockAndBlobsSidecar(
      final SignedBeaconBlockAndBlobsSidecar signedBeaconBlockAndBlobsSidecar) {
    return validateAndImportBlockAndBlobsSidecar(
        signedBeaconBlockAndBlobsSidecar.getSignedBeaconBlock(),
        Optional.of(signedBeaconBlockAndBlobsSidecar.getBlobsSidecar()));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private SafeFuture<InternalValidationResult> validateAndImportBlockAndBlobsSidecar(
      final SignedBeaconBlock block, final Optional<BlobsSidecar> blobsSidecar) {

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

    final SafeFuture<InternalValidationResult> validationResult =
        validator.validate(block, blobsSidecar);
    validationResult.thenAccept(
        result -> {
          if (result.code().equals(ValidationResultCode.ACCEPT)
              || result.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
            final SafeFuture<Void> blobsImport;
            if (blobsSidecar.isPresent()) {
              blobsImport = blobsManager.importBlobs(blobsSidecar.get());
            } else {
              blobsImport = SafeFuture.COMPLETE;
            }
            // for now, we want to be sure that blobs are available in the manager before continue
            // block import
            blobsImport
                .thenCompose(__ -> doImportBlockAndBlobsSidecar(block, blockImportPerformance))
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

  @Override
  public void onBlockImported(final SignedBeaconBlock block) {
    // Check if any pending blocks can now be imported
    final Bytes32 blockRoot = block.getRoot();
    pendingBlocks.remove(block);
    final List<SignedBeaconBlock> children = pendingBlocks.getItemsDependingOn(blockRoot, false);
    children.forEach(pendingBlocks::remove);
    children.forEach(this::importBlockIgnoringResult);
  }

  private void importBlockIgnoringResult(final SignedBeaconBlock block) {
    doImportBlockAndBlobsSidecar(block, Optional.empty()).ifExceptionGetsHereRaiseABug();
  }

  private SafeFuture<BlockImportResult> doImportBlockAndBlobsSidecar(
      final SignedBeaconBlock block,
      final Optional<BlockImportPerformance> blockImportPerformance) {
    return handleInvalidBlock(block)
        .or(() -> handleKnownBlock(block))
        .orElseGet(
            () ->
                handleBlockAndBlobsSidecarImport(block, blockImportPerformance)
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

  private SafeFuture<BlockImportResult> handleBlockAndBlobsSidecarImport(
      final SignedBeaconBlock block,
      final Optional<BlockImportPerformance> blockImportPerformance) {
    return blockImporter
        .importBlockAndBlobsSidecar(block, blockImportPerformance)
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
                  case FAILED_BLOBS_AVAILABILITY_CHECK:
                    // TODO:
                    //  Trigger the fetcher in the case the coupled BeaconBlockAndBlobsSidecar
                    //  contains a valid block but the BlobsSidecar validation fails.
                    //  Should be similar to what we do with pendingBlocks.
                    blobsManager
                        .discardBlobsByBlockRoot(block.getRoot())
                        .ifExceptionGetsHereRaiseABug();
                    break;
                  default:
                    LOG.trace(
                        "Unable to import block for reason {}: {}",
                        result.getFailureReason(),
                        block);
                    blobsManager
                        .discardBlobsByBlockRoot(block.getRoot())
                        .ifExceptionGetsHereRaiseABug();
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

    pendingBlocks
        .getItemsDependingOn(blockRoot, true)
        .forEach(
            blockToDrop -> {
              invalidBlockRoots.put(
                  blockToDrop.getMessage().hashTreeRoot(),
                  BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);
              pendingBlocks.remove(blockToDrop);
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
}
