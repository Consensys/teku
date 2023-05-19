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

package tech.pegasys.teku.validator.coordinator.publisher;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

public class BlockPublisherDeneb extends AbstractBlockPublisher {

  private final BlobSidecarPool blobSidecarPool;
  private final BlockGossipChannel blockGossipChannel;
  private final BlobSidecarGossipChannel blobSidecarGossipChannel;

  public BlockPublisherDeneb(
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockGossipChannel blockGossipChannel,
      final BlobSidecarPool blobSidecarPool,
      final BlobSidecarGossipChannel blobSidecarGossipChannel,
      final PerformanceTracker performanceTracker,
      final DutyMetrics dutyMetrics) {
    super(blockFactory, blockImportChannel, performanceTracker, dutyMetrics);
    this.blobSidecarPool = blobSidecarPool;
    this.blockGossipChannel = blockGossipChannel;
    this.blobSidecarGossipChannel = blobSidecarGossipChannel;
  }

  @Override
  protected SafeFuture<BlockImportResult> gossipAndImportUnblindedSignedBlock(
      final SignedBlockContainer blockContainer) {
    gossipAndImportBlobSidecars(blockContainer);
    final SignedBeaconBlock block = blockContainer.getSignedBlock();
    blockGossipChannel.publishBlock(block);
    return blockImportChannel.importBlock(block);
  }

  private void gossipAndImportBlobSidecars(final SignedBlockContainer blockContainer) {
    blockContainer
        .getSignedBlobSidecars()
        .ifPresent(
            signedBlobSidecars -> {
              blobSidecarGossipChannel.publishBlobSidecars(signedBlobSidecars);
              blobSidecarPool.onCompletedBlockAndSignedBlobSidecars(
                  blockContainer.getSignedBlock(), signedBlobSidecars);
            });
  }
}
