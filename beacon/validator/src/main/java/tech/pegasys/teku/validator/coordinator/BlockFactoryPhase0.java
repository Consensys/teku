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

package tech.pegasys.teku.validator.coordinator;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.EthConstants;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;

public class BlockFactoryPhase0 implements BlockFactory {

  protected final Spec spec;
  protected final BlockOperationSelectorFactory operationSelector;

  public BlockFactoryPhase0(
      final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    this.spec = spec;
    this.operationSelector = operationSelector;
  }

  @Override
  public SafeFuture<BlockContainer> createUnsignedBlock(
      final BeaconState blockSlotState,
      final UInt64 proposalSlot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti,
      final Optional<Boolean> requestedBlinded,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {
    checkArgument(
        blockSlotState.getSlot().equals(proposalSlot),
        "Block slot state for slot %s but should be for slot %s",
        blockSlotState.getSlot(),
        proposalSlot);

    // Process empty slots up to the one before the new block slot
    final UInt64 slotBeforeBlock = proposalSlot.minus(UInt64.ONE);

    final Bytes32 parentRoot = spec.getBlockRootAtSlot(blockSlotState, slotBeforeBlock);

    return spec.createNewUnsignedBlock(
            proposalSlot,
            spec.getBeaconProposerIndex(blockSlotState, proposalSlot),
            blockSlotState,
            parentRoot,
            operationSelector.createSelector(
                parentRoot,
                blockSlotState,
                randaoReveal,
                optionalGraffiti,
                requestedBlinded,
                requestedBuilderBoostFactor,
                blockProductionPerformance),
            blockProductionPerformance)
        .thenPeek(
            beaconBlockAndState ->
                System.out.println(
                    "block rewards: "
                        + EthConstants.GWEI_TO_WEI.multiply(
                            BeaconStateCache.getSlotCaches(beaconBlockAndState.getState())
                                .getBlockProposerRewards()
                                .longValue())))
        .thenApply(BeaconBlockAndState::getBlock);
  }

  @Override
  public SafeFuture<SignedBeaconBlock> unblindSignedBlockIfBlinded(
      final SignedBeaconBlock maybeBlindedBlock) {
    if (maybeBlindedBlock.isBlinded()) {
      return spec.unblindSignedBeaconBlock(
          maybeBlindedBlock.getSignedBlock(), operationSelector.createBlockUnblinderSelector());
    }
    return SafeFuture.completedFuture(maybeBlindedBlock);
  }

  @Override
  public List<BlobSidecar> createBlobSidecars(final SignedBlockContainer blockContainer) {
    return Collections.emptyList();
  }
}
