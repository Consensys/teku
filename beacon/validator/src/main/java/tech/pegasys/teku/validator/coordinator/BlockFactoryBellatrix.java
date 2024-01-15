/*
 * Copyright Consensys Software Inc., 2024
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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;

public class BlockFactoryBellatrix extends BlockFactoryPhase0 {
  private final ForkChoiceNotifier forkChoiceNotifier;

  public BlockFactoryBellatrix(
      final Spec spec,
      final ForkChoiceNotifier forkChoiceNotifier,
      final BlockOperationSelectorFactory operationSelector) {
    super(spec, operationSelector);
    this.forkChoiceNotifier = forkChoiceNotifier;
  }

  @Override
  public SafeFuture<BlockContainer> createUnsignedBlock(
      final BeaconState blockSlotState,
      final UInt64 newSlot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti,
      final Optional<Boolean> requestedBlinded,
      final BlockProductionPerformance blockProductionPerformance) {
    final Bytes32 parentRoot = getParentRoot(blockSlotState, newSlot);

    return forkChoiceNotifier
        .getPayloadId(parentRoot, newSlot)
        .thenCompose(
            executionPayloadContext ->
                internalCreateUnsignedBlock(
                    executionPayloadContext,
                    parentRoot,
                    blockSlotState,
                    newSlot,
                    randaoReveal,
                    optionalGraffiti,
                    requestedBlinded,
                    blockProductionPerformance));
  }
}
