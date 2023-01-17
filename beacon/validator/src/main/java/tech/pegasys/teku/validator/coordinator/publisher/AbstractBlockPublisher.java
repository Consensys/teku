/*
 * Copyright ConsenSys Software Inc., 2023
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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
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
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(SignedBeaconBlock maybeBlindedBlock) {
    return blockFactory
        .unblindSignedBeaconBlockIfBlinded(maybeBlindedBlock)
        .thenPeek(performanceTracker::saveProducedBlock)
        .thenCompose(this::gossipAndImportUnblindedSignedBlock)
        .thenApply(
            result -> {
              if (result.isSuccessful()) {
                LOG.trace(
                    "Successfully imported proposed block: {}", maybeBlindedBlock::toLogString);
                dutyMetrics.onBlockPublished(maybeBlindedBlock.getMessage().getSlot());
                return SendSignedBlockResult.success(maybeBlindedBlock.getRoot());
              } else if (result.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE) {
                LOG.debug(
                    "Delayed processing proposed block {} because it is from the future",
                    maybeBlindedBlock::toLogString);
                dutyMetrics.onBlockPublished(maybeBlindedBlock.getMessage().getSlot());
                return SendSignedBlockResult.notImported(result.getFailureReason().name());
              } else {
                VALIDATOR_LOGGER.proposedBlockImportFailed(
                    result.getFailureReason().toString(),
                    maybeBlindedBlock.getSlot(),
                    maybeBlindedBlock.getRoot(),
                    result.getFailureCause());

                return SendSignedBlockResult.notImported(result.getFailureReason().name());
              }
            });
  }

  abstract SafeFuture<BlockImportResult> gossipAndImportUnblindedSignedBlock(
      final SignedBeaconBlock block);
}
