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

package tech.pegasys.teku.validator.coordinator;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlockAndBlobsSidecarGossipChannel;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

public class BlockImporterEip4844 implements BlockImporter {
  private static final Logger LOG = LogManager.getLogger();

  private final BlockFactory blockFactory;
  private final BlockImportChannel blockImportChannel;
  private final BlockAndBlobsSidecarGossipChannel blockAndBlobsSidecarGossipChannel;
  private final PerformanceTracker performanceTracker;
  private final DutyMetrics dutyMetrics;

  public BlockImporterEip4844(
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockAndBlobsSidecarGossipChannel blockAndBlobsSidecarGossipChannel,
      final PerformanceTracker performanceTracker,
      final DutyMetrics dutyMetrics) {
    this.blockFactory = blockFactory;
    this.blockImportChannel = blockImportChannel;
    this.blockAndBlobsSidecarGossipChannel = blockAndBlobsSidecarGossipChannel;
    this.performanceTracker = performanceTracker;
    this.dutyMetrics = dutyMetrics;
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(SignedBeaconBlock maybeBlindedBlock) {
    return blockFactory
        .unblindSignedBeaconBlockIfBlinded(maybeBlindedBlock)
        .thenCompose(this::sendUnblindedSignedBlock);
  }

  private SafeFuture<SendSignedBlockResult> sendUnblindedSignedBlock(
      final SignedBeaconBlock block) {
    performanceTracker.saveProducedBlock(block);
    final SafeFuture<SignedBeaconBlockAndBlobsSidecar> blockAndBlobsSidecarSafeFuture =
        blockFactory.supplementBlockWithSidecar(block);
    return blockAndBlobsSidecarSafeFuture
        .thenPeek(blockAndBlobsSidecarGossipChannel::publishBlockAndBlobsSidecar)
        .thenCompose(
            blockAndBlobsSidecar ->
                blockImportChannel.importBlock(
                    blockAndBlobsSidecar.getSignedBeaconBlock(),
                    Optional.of(blockAndBlobsSidecar.getBlobsSidecar())))
        .thenApply(
            result -> {
              if (result.isSuccessful()) {
                LOG.trace("Successfully imported proposed block: {}", block::toLogString);
                dutyMetrics.onBlockPublished(block.getMessage().getSlot());
                return SendSignedBlockResult.success(block.getRoot());
              } else if (result.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE) {
                LOG.debug(
                    "Delayed processing proposed block {} because it is from the future",
                    block::toLogString);
                dutyMetrics.onBlockPublished(block.getMessage().getSlot());
                return SendSignedBlockResult.notImported(result.getFailureReason().name());
              } else {
                VALIDATOR_LOGGER.proposedBlockImportFailed(
                    result.getFailureReason().toString(),
                    block.getSlot(),
                    block.getRoot(),
                    result.getFailureCause());

                return SendSignedBlockResult.notImported(result.getFailureReason().name());
              }
            });
  }
}
