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

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
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
  protected final PerformanceTracker performanceTracker;
  protected final DutyMetrics dutyMetrics;

  public AbstractBlockPublisher(
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final PerformanceTracker performanceTracker,
      final DutyMetrics dutyMetrics) {
    this.blockFactory = blockFactory;
    this.blockImportChannel = blockImportChannel;
    this.performanceTracker = performanceTracker;
    this.dutyMetrics = dutyMetrics;
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel) {
    return blockFactory
        .unblindSignedBlockIfBlinded(blockContainer.getSignedBlock())
        .thenPeek(performanceTracker::saveProducedBlock)
        .thenCompose(
            signedBlock ->
                // TODO: produce blob sidecars for Deneb (using BlockFactory)
                gossipAndImportUnblindedSignedBlockAndBlobSidecars(
                    signedBlock, List.of(), broadcastValidationLevel))
        .thenCompose(result -> calculateResult(blockContainer, result));
  }

  private SafeFuture<BlockImportAndBroadcastValidationResults>
      gossipAndImportUnblindedSignedBlockAndBlobSidecars(
          final SignedBeaconBlock block,
          final List<BlobSidecar> blobSidecars,
          final BroadcastValidationLevel broadcastValidationLevel) {

    if (broadcastValidationLevel == BroadcastValidationLevel.NOT_REQUIRED) {
      // when broadcast validation is disabled, we can publish the block (and blob sidecars)
      // immediately and then import
      publishBlockAndBlobSidecars(block, blobSidecars);
      return importBlockAndBlobSidecars(block, blobSidecars, broadcastValidationLevel);
    }

    // when broadcast validation is enabled, we need to wait for the validation to complete before
    // publishing the block (and blob sidecars)

    final SafeFuture<BlockImportAndBroadcastValidationResults>
        blockImportAndBroadcastValidationResults =
            importBlockAndBlobSidecars(block, blobSidecars, broadcastValidationLevel);

    blockImportAndBroadcastValidationResults
        .thenCompose(BlockImportAndBroadcastValidationResults::broadcastValidationResult)
        .thenAccept(
            broadcastValidationResult -> {
              if (broadcastValidationResult == BroadcastValidationResult.SUCCESS) {
                publishBlockAndBlobSidecars(block, blobSidecars);
                LOG.debug("Block (and blob sidecars) publishing initiated");
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

  abstract SafeFuture<BlockImportAndBroadcastValidationResults> importBlockAndBlobSidecars(
      SignedBeaconBlock block,
      List<BlobSidecar> blobSidecars,
      BroadcastValidationLevel broadcastValidationLevel);

  abstract void publishBlockAndBlobSidecars(
      SignedBeaconBlock block, List<BlobSidecar> blobSidecars);

  private SafeFuture<SendSignedBlockResult> calculateResult(
      final SignedBlockContainer maybeBlindedBlockContainer,
      final BlockImportAndBroadcastValidationResults blockImportAndBroadcastValidationResults) {

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
                        "Broadcast validation failed: " + broadcastValidationResult.name()));
              }

              return blockImportAndBroadcastValidationResults
                  .blockImportResult()
                  .thenApply(
                      importResult -> {
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
