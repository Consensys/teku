/*
 * Copyright Consensys Software Inc., 2026
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

import com.google.common.base.Suppliers;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class MilestoneBasedBlockFactory implements BlockFactory {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<SpecMilestone, BlockFactory> registeredFactories =
      new EnumMap<>(SpecMilestone.class);

  private final Spec spec;

  public MilestoneBasedBlockFactory(
      final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    this.spec = spec;
    final BlockFactoryPhase0 blockFactoryPhase0 = new BlockFactoryPhase0(spec, operationSelector);

    // Not needed for all milestones
    final Supplier<BlockFactoryDeneb> blockFactoryDenebSupplier =
        Suppliers.memoize(() -> new BlockFactoryDeneb(spec, operationSelector));
    final Supplier<BlockFactoryFulu> blockFactoryFuluSupplier =
        Suppliers.memoize(() -> new BlockFactoryFulu(spec, operationSelector));
    final Supplier<BlockFactoryGloas> blockFactoryGloasSupplier =
        Suppliers.memoize(() -> new BlockFactoryGloas(spec, operationSelector));

    // Populate forks factories
    spec.getEnabledMilestones()
        .forEach(
            forkAndSpecMilestone -> {
              final SpecMilestone milestone = forkAndSpecMilestone.getSpecMilestone();
              if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
                registeredFactories.put(milestone, blockFactoryGloasSupplier.get());
              } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
                registeredFactories.put(milestone, blockFactoryFuluSupplier.get());
              } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
                registeredFactories.put(milestone, blockFactoryDenebSupplier.get());
              } else {
                registeredFactories.put(milestone, blockFactoryPhase0);
              }
            });
  }

  @Override
  public SafeFuture<BlockContainerAndMetaData> createUnsignedBlock(
      final BeaconState blockSlotState,
      final UInt64 proposalSlot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {
    final SpecMilestone milestone = getMilestone(proposalSlot);
    return registeredFactories
        .get(milestone)
        .createUnsignedBlock(
            blockSlotState,
            proposalSlot,
            randaoReveal,
            optionalGraffiti,
            requestedBuilderBoostFactor,
            blockProductionPerformance)
        .whenException(
            error -> {
              LOG.debug("Error creating unsigned block for slot {}: {}", proposalSlot, error);
            });
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> unblindSignedBlockIfBlinded(
      final SignedBeaconBlock maybeBlindedBlock,
      final BlockPublishingPerformance blockPublishingPerformance) {
    final SpecMilestone milestone = getMilestone(maybeBlindedBlock.getSlot());
    return registeredFactories
        .get(milestone)
        .unblindSignedBlockIfBlinded(maybeBlindedBlock, blockPublishingPerformance);
  }

  @Override
  public List<BlobSidecar> createBlobSidecars(final SignedBlockContainer blockContainer) {
    final SpecMilestone milestone = getMilestone(blockContainer.getSlot());
    return registeredFactories.get(milestone).createBlobSidecars(blockContainer);
  }

  @Override
  public List<DataColumnSidecar> createDataColumnSidecars(
      final SignedBlockContainer blockContainer) {
    final SpecMilestone milestone = getMilestone(blockContainer.getSlot());
    return registeredFactories.get(milestone).createDataColumnSidecars(blockContainer);
  }

  private SpecMilestone getMilestone(final UInt64 slot) {
    return spec.atSlot(slot).getMilestone();
  }
}
