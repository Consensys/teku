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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.statetransition.validation.BlockValidator.BroadcastValidationResult;
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
      final SignedBlockContainer maybeBlindedBlockContainer,
      final BroadcastValidationLevel broadcastValidationLevel) {
    return blockFactory
        .unblindSignedBlockIfBlinded(maybeBlindedBlockContainer)
        .thenPeek(performanceTracker::saveProducedBlock)
        .thenCompose(
            signedBlockContainer ->
                gossipAndImportUnblindedSignedBlock(signedBlockContainer, broadcastValidationLevel))
        .thenApply(
            result -> {
              if (result.isSuccessful()) {
                LOG.trace(
                    "Successfully imported proposed block: {}",
                    maybeBlindedBlockContainer.getSignedBlock().toLogString());
                dutyMetrics.onBlockPublished(maybeBlindedBlockContainer.getSlot());
                return SendSignedBlockResult.success(maybeBlindedBlockContainer.getRoot());
              } else if (result.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE) {
                LOG.debug(
                    "Delayed processing proposed block {} because it is from the future",
                    maybeBlindedBlockContainer.getSignedBlock().toLogString());
                dutyMetrics.onBlockPublished(maybeBlindedBlockContainer.getSlot());
                return SendSignedBlockResult.notImported(result.getFailureReason().name());
              } else {
                VALIDATOR_LOGGER.proposedBlockImportFailed(
                    result.getFailureReason().toString(),
                    maybeBlindedBlockContainer.getSlot(),
                    maybeBlindedBlockContainer.getRoot(),
                    result.getFailureCause());

                return SendSignedBlockResult.notImported(result.getFailureReason().name());
              }
            });
  }

  private SafeFuture<BlockImportResult> gossipAndImportUnblindedSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel) {

    if (broadcastValidationLevel == BroadcastValidationLevel.NOT_REQUIRED) {
      // when broadcast validation is disabled, we can publish the block immediately and then import
      publishBlock(blockContainer);
      return importBlock(blockContainer, broadcastValidationLevel)
          .thenCompose(BlockImportAndBroadcastValidationResults::blockImportResult);
    }

    // when broadcast validation is enabled, we need to wait for the validation to complete before
    // publishing the block

    final SafeFuture<BlockImportAndBroadcastValidationResults>
        importResultWithBroadcastValidationResult =
            importBlock(blockContainer, broadcastValidationLevel);

    importResultWithBroadcastValidationResult
        .thenCompose(
            blockImportAndBroadcastValidationResults ->
                blockImportAndBroadcastValidationResults
                    .broadcastValidationResult()
                    .orElseThrow()
                    .thenAccept(
                        broadcastValidationResult -> {
                          if (broadcastValidationResult == BroadcastValidationResult.SUCCESS) {
                            publishBlock(blockContainer);
                            LOG.debug("Block (and blob sidecars) publishing initiated");
                          } else {
                            LOG.warn(
                                "Block (and blob sidecars) publishing skipped due to broadcast validation result {} for slot {}",
                                broadcastValidationResult,
                                blockContainer.getSlot());
                          }
                        }))
        .finish(
            err ->
                LOG.error(
                    "Block (and blob sidecars) publishing failed for slot {}",
                    blockContainer.getSlot(),
                    err));

    return importResultWithBroadcastValidationResult.thenCompose(
        BlockImportAndBroadcastValidationResults::blockImportResult);
  }

  abstract SafeFuture<BlockImportAndBroadcastValidationResults> importBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel);

  abstract void publishBlock(final SignedBlockContainer blockContainer);
}
