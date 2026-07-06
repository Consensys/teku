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

import com.google.common.base.Preconditions;
import java.util.Optional;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContentsWithBlobsAndExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

import static tech.pegasys.teku.spec.constants.EthConstants.GWEI_TO_WEI;

// Gloas is more similar to BlockFactoryPhase0 than BlockFactoryFulu
public class BlockFactoryGloas extends BlockFactoryPhase0 {

  public BlockFactoryGloas(final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    super(spec, operationSelector);
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

  @Override
  public SafeFuture<BlockContainerAndMetaData> createUnsignedBlock(
      final BlockProductionContext blockProductionContext) {
    SafeFuture<BeaconBlockAndState> beaconBlockAndStateSafeFuture = spec.createNewUnsignedBlock(
            blockProductionContext.proposalSlot(),
            spec.getBeaconProposerIndex(blockProductionContext.blockSlotState(), blockProductionContext.proposalSlot()),
            blockProductionContext.blockSlotState(),
            blockProductionContext.parentRoot(),
            operationSelector.createSelector(blockProductionContext),
            blockProductionContext.blockProductionPerformance());

    return beaconBlockAndStateSafeFuture.thenApply((bbas) -> {
      //TODO the below commented code are a couple of my attempts to produce an ExecutionPayloadEnvelope.
      // Both require access to objects which I seemingly don't have easy access to here, making me think I'm barking up the wrong tree yet again.
//      SafeFuture<ExecutionPayloadEnvelope> executionPayloadEnvelopeSafeFuture = executionPayloadFactory.createUnsignedExecutionPayload(bbas.getBlock().getBody().getOptionalSignedExecutionPayloadBid().map(
//                signedExecutionPayloadBid ->
//                    signedExecutionPayloadBid.getMessage().getBuilderIndex()).get(), bbas);

//      spec.createNewUnsignedExecutionPayload(bbas.getSlot(),
//              bbas.getBlock().getBody().getOptionalSignedExecutionPayloadBid().map(
//                signedExecutionPayloadBid ->
//                    signedExecutionPayloadBid.getMessage().getBuilderIndex()).get(),
//              bbas,
//
//                    )
      SafeFuture<BlobsBundle> blobsBundleSafeFuture = operationSelector
        .createBlobsBundleSelector()
        .apply(bbas.getBlock());

      SchemaDefinitionsGloas schema =
        SchemaDefinitionsGloas.required(spec.atSlot(bbas.getBlock().getSlot()).getSchemaDefinitions());
      try {
        ExecutionPayloadEnvelope executionPayloadEnvelope = executionPayloadEnvelopeSafeFuture.get();
        BlobsBundle blobsBundle = blobsBundleSafeFuture.get();
        BlockContentsWithBlobsAndExecutionPayloadEnvelopeSchema<?> blockContainer = (BlockContentsWithBlobsAndExecutionPayloadEnvelopeSchema<?>) schema
                .getBlockContentsWithBlobsAndExecutionEnvelopeSchema()
                .create(bbas.getBlock(), executionPayloadEnvelope, blobsBundle.getProofs(), blobsBundle.getBlobs());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      final SlotCaches slotCaches = BeaconStateCache.getSlotCaches(bbas.getState());
      return new BlockContainerAndMetaData(
              bbas.getBlock(),
              spec.atSlot(bbas.getSlot()).getMilestone(),
              slotCaches.getBlockExecutionValue(),
              GWEI_TO_WEI.multiply(slotCaches.getBlockProposerRewards().longValue()));
    });
  }
}
