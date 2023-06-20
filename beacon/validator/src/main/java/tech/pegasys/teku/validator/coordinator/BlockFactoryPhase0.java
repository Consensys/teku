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

package tech.pegasys.teku.validator.coordinator;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

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
      final UInt64 newSlot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti,
      final boolean blinded) {
    checkArgument(
        blockSlotState.getSlot().equals(newSlot),
        "Block slot state for slot %s but should be for slot %s",
        blockSlotState.getSlot(),
        newSlot);

    // Process empty slots up to the one before the new block slot
    final UInt64 slotBeforeBlock = newSlot.minus(UInt64.ONE);

    final Bytes32 parentRoot = spec.getBlockRootAtSlot(blockSlotState, slotBeforeBlock);

    return spec.createNewUnsignedBlock(
            newSlot,
            spec.getBeaconProposerIndex(blockSlotState, newSlot),
            blockSlotState,
            parentRoot,
            operationSelector.createSelector(
                parentRoot, blockSlotState, randaoReveal, optionalGraffiti),
            blinded)
        .thenApply(BeaconBlockAndState::getBlock);
  }

  @Override
  public SafeFuture<SignedBlockContainer> unblindSignedBlockIfBlinded(
      final SignedBlockContainer maybeBlindedBlockContainer) {
    return maybeBlindedBlockContainer
        .toBlinded()
        .map(
            blindedBlockContainer ->
                spec.unblindSignedBeaconBlock(
                        blindedBlockContainer, operationSelector.createBlockUnblinderSelector())
                    .thenApply(signedBeaconBlock -> (SignedBlockContainer) signedBeaconBlock))
        .orElseGet(() -> SafeFuture.completedFuture(maybeBlindedBlockContainer));
  }
}
