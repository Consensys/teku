/*
 * Copyright Consensys Software Inc., 2022
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

import java.util.List;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

public class BlockPublisherEip7594 extends AbstractBlockPublisher {

  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final BlockGossipChannel blockGossipChannel;
  private final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel;

  public BlockPublisherEip7594(
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockGossipChannel blockGossipChannel,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel,
      final PerformanceTracker performanceTracker,
      final DutyMetrics dutyMetrics) {
    super(blockFactory, blockImportChannel, performanceTracker, dutyMetrics);
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.blockGossipChannel = blockGossipChannel;
    this.dataColumnSidecarGossipChannel = dataColumnSidecarGossipChannel;
  }

  @Override
  SafeFuture<BlockImportAndBroadcastValidationResults> importBlockAndBlobSidecars(
      final SignedBeaconBlock block,
      final List<BlobSidecar> blobSidecars,
      final BroadcastValidationLevel broadcastValidationLevel,
      final BlockPublishingPerformance blockPublishingPerformance) {
    // TODO: DataColumnSidecars pool fill up
    // provide blobs for the block before importing it
    blockBlobSidecarsTrackersPool.onCompletedBlockAndBlobSidecars(block, blobSidecars);
    return blockImportChannel
        .importBlock(block, broadcastValidationLevel)
        .thenPeek(__ -> blockPublishingPerformance.blockImportCompleted());
  }

  @Override
  void publishBlockAndBlobSidecars(
      final SignedBeaconBlock block,
      final List<BlobSidecar> blobSidecars,
      BlockPublishingPerformance blockPublishingPerformance) {
    blockGossipChannel.publishBlock(block);
    final List<DataColumnSidecar> dataColumnSidecars = blockFactory.createDataColumnSidecars(block);
    dataColumnSidecarGossipChannel.publishDataColumnSidecars(dataColumnSidecars);
    blockPublishingPerformance.blockAndBlobSidecarsPublishingInitiated();
  }
}
