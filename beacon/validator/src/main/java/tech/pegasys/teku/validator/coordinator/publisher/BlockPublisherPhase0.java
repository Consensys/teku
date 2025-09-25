/*
 * Copyright Consensys Software Inc., 2025
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
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

public class BlockPublisherPhase0 extends AbstractBlockPublisher {

  public BlockPublisherPhase0(
      final AsyncRunner asyncRunner,
      final BlockFactory blockFactory,
      final BlockGossipChannel blockGossipChannel,
      final BlockImportChannel blockImportChannel,
      final DutyMetrics dutyMetrics,
      final boolean gossipBlobsAfterBlock) {
    super(
        asyncRunner,
        blockFactory,
        blockGossipChannel,
        blockImportChannel,
        dutyMetrics,
        gossipBlobsAfterBlock);
  }

  @Override
  SafeFuture<BlockImportAndBroadcastValidationResults> importBlock(
      final SignedBeaconBlock block,
      final BroadcastValidationLevel broadcastValidationLevel,
      final BlockPublishingPerformance blockPublishingPerformance) {
    return blockImportChannel.importBlock(block, broadcastValidationLevel);
  }

  @Override
  void importBlobSidecars(
      final List<BlobSidecar> blobSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    // No-op for phase 0
  }

  @Override
  SafeFuture<Void> publishBlock(
      final SignedBeaconBlock block, final BlockPublishingPerformance blockPublishingPerformance) {
    blockPublishingPerformance.blockPublishingInitiated();
    return blockGossipChannel.publishBlock(block);
  }

  @Override
  void publishBlobSidecars(
      final List<BlobSidecar> blobSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    // No-op for phase 0
  }

  @Override
  void publishDataColumnSidecars(
      final List<DataColumnSidecar> dataColumnSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    // No-op for phase 0
  }
}
