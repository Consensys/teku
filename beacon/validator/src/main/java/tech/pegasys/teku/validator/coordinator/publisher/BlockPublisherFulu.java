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
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

public class BlockPublisherFulu extends BlockPublisherPhase0 {

  private final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel;

  public BlockPublisherFulu(
      final AsyncRunner asyncRunner,
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockGossipChannel blockGossipChannel,
      final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel,
      final DutyMetrics dutyMetrics,
      final boolean gossipBlobsAfterBlock) {
    super(
        asyncRunner,
        blockFactory,
        blockGossipChannel,
        blockImportChannel,
        dutyMetrics,
        gossipBlobsAfterBlock);
    this.dataColumnSidecarGossipChannel = dataColumnSidecarGossipChannel;
  }

  @Override
  void publishBlobSidecars(
      final List<BlobSidecar> blobSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    throw new RuntimeException("Unexpected call to publishBlobSidecars in Fulu");
  }

  @Override
  void publishDataColumnSidecars(
      final List<DataColumnSidecar> dataColumnSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    blockPublishingPerformance.dataColumnSidecarsPublishingInitiated();
    dataColumnSidecarGossipChannel.publishDataColumnSidecars(
        dataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);
  }
}
