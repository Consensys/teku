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

package tech.pegasys.teku.validator.coordinator;

import static tech.pegasys.teku.spec.constants.EthConstants.GWEI_TO_WEI;

import com.google.common.base.Preconditions;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;

// Gloas is more similar to BlockFactoryPhase0 than BlockFactoryFulu
public class BlockFactoryGloas extends BlockFactoryPhase0 {

  public BlockFactoryGloas(final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    super(spec, operationSelector);
  }

  @Override
  protected BlockContainerAndMetaData beaconBlockAndStateToBlockContainerAndMetaData(
      final BeaconBlockAndState blockAndState) {
    final SlotCaches slotCaches = BeaconStateCache.getSlotCaches(blockAndState.getState());
    final BeaconBlock block = blockAndState.getBlock();
    final UInt256 blockExecutionValue = slotCaches.getBlockExecutionValue();
    return new BlockContainerAndMetaData(
        block,
        spec.atSlot(blockAndState.getSlot()).getMilestone(),
        blockExecutionValue.isZero()
            // use value from the bid for the execution_payload_value field if not set in the cache
            // (TBD)
            ? GWEI_TO_WEI.multiply(
                block
                    .getBody()
                    .getOptionalSignedExecutionPayloadBid()
                    .orElseThrow()
                    .getMessage()
                    .getValue()
                    .longValue())
            : blockExecutionValue,
        GWEI_TO_WEI.multiply(slotCaches.getBlockProposerRewards().longValue()));
  }

  // blocks in ePBS are all unblinded
  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> unblindSignedBlockIfBlinded(
      final SignedBeaconBlock maybeBlindedBlock,
      final BlockPublishingPerformance blockPublishingPerformance) {
    Preconditions.checkArgument(
        !maybeBlindedBlock.isBlinded(), "Blocks in ePBS should be all unblinded");
    return SafeFuture.completedFuture(Optional.of(maybeBlindedBlock));
  }
}
