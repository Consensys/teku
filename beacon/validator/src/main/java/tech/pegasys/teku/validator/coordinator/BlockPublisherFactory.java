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

import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlockAndBlobsSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

public class BlockPublisherFactory {

  private final Spec spec;
  private final Map<SpecMilestone, BlockPublisher> registeredImporters = new HashMap<>();

  public BlockPublisherFactory(
      final Spec spec,
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockGossipChannel blockGossipChannel,
      final BlockAndBlobsSidecarGossipChannel blockAndBlobsSidecarGossipChannel,
      final PerformanceTracker performanceTracker,
      final DutyMetrics dutyMetrics) {
    this.spec = spec;
    final BlockPublisherPhase0 blockImporterImpl =
        new BlockPublisherPhase0(
            blockFactory, blockGossipChannel, blockImportChannel, performanceTracker, dutyMetrics);
    for (final SpecMilestone specMilestone :
        SpecMilestone.getAllPriorMilestones(SpecMilestone.EIP4844)) {
      registeredImporters.put(specMilestone, blockImporterImpl);
    }
    if (spec.isMilestoneSupported(SpecMilestone.EIP4844)) {
      final BlockPublisherEip4844 blockAndBlobsSidecarImporter =
          new BlockPublisherEip4844(
              blockFactory,
              blockImportChannel,
              blockAndBlobsSidecarGossipChannel,
              performanceTracker,
              dutyMetrics);
      registeredImporters.put(SpecMilestone.EIP4844, blockAndBlobsSidecarImporter);
    }
  }

  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBeaconBlock maybeBlindedBlock) {
    SpecMilestone blockMilestone = spec.atSlot(maybeBlindedBlock.getSlot()).getMilestone();
    return registeredImporters.get(blockMilestone).sendSignedBlock(maybeBlindedBlock);
  }
}
