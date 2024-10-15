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

package tech.pegasys.teku.validator.coordinator.publisher;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason.FAILED_BROADCAST_VALIDATION;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

public abstract class AbstractBlockPublisher implements BlockPublisher {
  private static final Logger LOG = LogManager.getLogger();

  protected final BlockFactory blockFactory;
  protected final BlockImportChannel blockImportChannel;
  protected final BlockGossipChannel blockGossipChannel;
  protected final PerformanceTracker performanceTracker;
  protected final DutyMetrics dutyMetrics;

  public AbstractBlockPublisher(
      final BlockFactory blockFactory,
      final BlockGossipChannel blockGossipChannel,
      final BlockImportChannel blockImportChannel,
      final PerformanceTracker performanceTracker,
      final DutyMetrics dutyMetrics) {
    this.blockFactory = blockFactory;
    this.blockImportChannel = blockImportChannel;
    this.blockGossipChannel = blockGossipChannel;
    this.performanceTracker = performanceTracker;
    this.dutyMetrics = dutyMetrics;
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel,
      final BlockPublishingPerformance blockPublishingPerformance) {
    return blockFactory
        .unblindSignedBlockIfBlinded(blockContainer.getSignedBlock(), blockPublishingPerformance)
        .thenPeek(performanceTracker::saveProducedBlock)
        .thenCompose(
            // creating blob sidecars after unblinding the block to ensure in the blinded flow we
            // will have the cached builder payload
            signedBlock ->
                gossipAndImportUnblindedSignedBlockAndBlobSidecars(
                    signedBlock,
                    Suppliers.memoize(
                        () ->
                            blockFactory.createBlobSidecars(
                                blockContainer, blockPublishingPerformance)),
                    broadcastValidationLevel,
                    blockPublishingPerformance))
        .thenCompose(result -> calculateResult(blockContainer, result, blockPublishingPerformance));
  }

  private SafeFuture<BlockImportAndBroadcastValidationResults>
      gossipAndImportUnblindedSignedBlockAndBlobSidecars(
          final SignedBeaconBlock block,
          final Supplier<List<BlobSidecar>> blobSidecars,
          final BroadcastValidationLevel broadcastValidationLevel,
          final BlockPublishingPerformance blockPublishingPerformance) {

    if (broadcastValidationLevel == BroadcastValidationLevel.NOT_REQUIRED) {
      // when broadcast validation is disabled, we can publish the block (and blob sidecars)
      // immediately and then import
      publishBlock(block, blockPublishingPerformance)
          .always(() -> publishBlobSidecars(blobSidecars.get(), blockPublishingPerformance));

      importBlobSidecars(blobSidecars.get(), blockPublishingPerformance);
      return importBlock(block, broadcastValidationLevel, blockPublishingPerformance);
    }

    // when broadcast validation is enabled, we need to wait for the validation to complete before
    // publishing the block (and blob sidecars)

    final SafeFuture<BlockImportAndBroadcastValidationResults>
        blockImportAndBroadcastValidationResults =
            importBlock(block, broadcastValidationLevel, blockPublishingPerformance);

    blockImportAndBroadcastValidationResults
        .thenCompose(BlockImportAndBroadcastValidationResults::broadcastValidationResult)
        .thenAccept(
            broadcastValidationResult -> {
              if (broadcastValidationResult == BroadcastValidationResult.SUCCESS) {
                publishBlock(block, blockPublishingPerformance)
                    .always(
                        () -> publishBlobSidecars(blobSidecars.get(), blockPublishingPerformance));
                LOG.debug("Block (and blob sidecars) publishing initiated");
                importBlobSidecars(blobSidecars.get(), blockPublishingPerformance);
              } else {
                LOG.warn(
                    "Block (and blob sidecars) publishing skipped due to broadcast validation result {} for slot {}",
                    broadcastValidationResult,
                    block.getSlot());
              }
            })
        .finish(
            err ->
                LOG.error(
                    "Block (and blob sidecars) publishing failed for slot {}",
                    block.getSlot(),
                    err));

    return blockImportAndBroadcastValidationResults;
  }

  abstract SafeFuture<BlockImportAndBroadcastValidationResults> importBlock(
      SignedBeaconBlock block,
      BroadcastValidationLevel broadcastValidationLevel,
      BlockPublishingPerformance blockPublishingPerformance);

  abstract void importBlobSidecars(
      List<BlobSidecar> blobSidecars, BlockPublishingPerformance blockPublishingPerformance);

  abstract SafeFuture<Void> publishBlock(
      SignedBeaconBlock block, BlockPublishingPerformance blockPublishingPerformance);

  abstract void publishBlobSidecars(
      List<BlobSidecar> blobSidecars, BlockPublishingPerformance blockPublishingPerformance);

  //  abstract SafeFuture<BlockImportAndBroadcastValidationResults> importBlockAndBlobSidecars(
  //      SignedBeaconBlock block,
  //      List<BlobSidecar> blobSidecars,
  //      BroadcastValidationLevel broadcastValidationLevel,
  //      BlockPublishingPerformance blockPublishingPerformance);

  //  abstract void publishBlockAndBlobSidecars(
  //      SignedBeaconBlock block,
  //      List<BlobSidecar> blobSidecars,
  //      BlockPublishingPerformance blockPublishingPerformance);

  private SafeFuture<SendSignedBlockResult> calculateResult(
      final SignedBlockContainer maybeBlindedBlockContainer,
      final BlockImportAndBroadcastValidationResults blockImportAndBroadcastValidationResults,
      final BlockPublishingPerformance blockPublishingPerformance) {

    // broadcast validation can fail earlier than block import.
    // The assumption is that in that block import will fail but not as fast
    // (there might be the state transition in progress)
    // Thus, to let the API return as soon as possible, let's check broadcast validation first.
    return blockImportAndBroadcastValidationResults
        .broadcastValidationResult()
        .thenCompose(
            broadcastValidationResult -> {
              if (broadcastValidationResult.isFailure()) {
                return SafeFuture.completedFuture(
                    SendSignedBlockResult.rejected(
                        FAILED_BROADCAST_VALIDATION.name()
                            + ": "
                            + broadcastValidationResult.name()));
              }

              return blockImportAndBroadcastValidationResults
                  .blockImportResult()
                  .thenApply(
                      importResult -> {
                        blockPublishingPerformance.blockImportCompleted();
                        if (importResult.isSuccessful()) {
                          LOG.trace(
                              "Successfully imported proposed block: {}",
                              maybeBlindedBlockContainer.getSignedBlock().toLogString());
                          dutyMetrics.onBlockPublished(maybeBlindedBlockContainer.getSlot());
                          return SendSignedBlockResult.success(
                              maybeBlindedBlockContainer.getRoot());
                        }
                        if (importResult.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE) {
                          LOG.debug(
                              "Delayed processing proposed block {} because it is from the future",
                              maybeBlindedBlockContainer.getSignedBlock().toLogString());
                          dutyMetrics.onBlockPublished(maybeBlindedBlockContainer.getSlot());
                          return SendSignedBlockResult.notImported(
                              importResult.getFailureReason().name());
                        }
                        VALIDATOR_LOGGER.proposedBlockImportFailed(
                            importResult.getFailureReason().toString(),
                            maybeBlindedBlockContainer.getSlot(),
                            maybeBlindedBlockContainer.getRoot(),
                            importResult.getFailureCause());

                        return SendSignedBlockResult.notImported(
                            importResult.getFailureReason().name());
                      });
            });
  }
}
