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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlockAndBlobsSidecarGossipChannel;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

public class BlockPublisherEip4844 extends AbstractBlockPublisher {
  private final BlockAndBlobsSidecarGossipChannel blockAndBlobsSidecarGossipChannel;

  public BlockPublisherEip4844(
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockAndBlobsSidecarGossipChannel blockAndBlobsSidecarGossipChannel,
      final PerformanceTracker performanceTracker,
      final DutyMetrics dutyMetrics) {
    super(blockFactory, blockImportChannel, performanceTracker, dutyMetrics);
    this.blockAndBlobsSidecarGossipChannel = blockAndBlobsSidecarGossipChannel;
  }

  @Override
  protected SafeFuture<BlockImportResult> gossipAndImportUnblindedSignedBlock(
      final SignedBeaconBlock block) {
    final SafeFuture<SignedBeaconBlockAndBlobsSidecar> blockAndBlobsSidecarSafeFuture =
        blockFactory.supplementBlockWithSidecar(block);
    return blockAndBlobsSidecarSafeFuture
        .thenPeek(blockAndBlobsSidecarGossipChannel::publishBlockAndBlobsSidecar)
        .thenCompose(
            blockAndBlobsSidecar ->
                blockImportChannel.importBlock(
                    blockAndBlobsSidecar.getSignedBeaconBlock(),
                    Optional.of(blockAndBlobsSidecar.getBlobsSidecar())));
  }
}
