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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarOld;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class BlockFactoryDeneb extends BlockFactoryPhase0 {

  private final SchemaDefinitionsDeneb schemaDefinitionsDeneb;

  public BlockFactoryDeneb(final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    super(spec, operationSelector);
    this.schemaDefinitionsDeneb =
        SchemaDefinitionsDeneb.required(
            spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions());
  }

  @Deprecated
  @Override
  public SafeFuture<BlockContainer> createUnsignedBlock(
      final BeaconState blockSlotState,
      final UInt64 newSlot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti,
      final boolean blinded) {
    return super.createUnsignedBlock(
            blockSlotState, newSlot, randaoReveal, optionalGraffiti, blinded)
        .thenCompose(
            blockContainer -> {
              if (blockContainer.isBlinded()) {
                return SafeFuture.completedFuture(blockContainer);
              }
              // TODO: add blobs and proofs
              final BeaconBlock block = blockContainer.getBlock();
              return operationSelector
                  .createBlobSidecarsSelector()
                  .apply(block)
                  .thenApply(blobSidecars -> createBlockContents(block, blobSidecars));
            });
  }

  @Override
  public SafeFuture<BlockContainer> createUnsignedBlock(
      final BeaconState blockSlotState,
      final UInt64 newSlot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti) {
    return super.createUnsignedBlock(blockSlotState, newSlot, randaoReveal, optionalGraffiti)
        .thenCompose(
            blockContainer -> {
              if (blockContainer.isBlinded()) {
                return SafeFuture.completedFuture(blockContainer);
              }
              // TODO: add blobs and proofs
              final BeaconBlock block = blockContainer.getBlock();
              return operationSelector
                  .createBlobSidecarsSelector()
                  .apply(block)
                  .thenApply(blobSidecars -> createBlockContents(block, blobSidecars));
            });
  }

  /**
   * Adding blobs and proofs after the block in order to use the cached value from the {@link
   * ExecutionLayerChannel#builderGetPayload( SignedBlockContainer, Function)} call
   */
  @Override
  public SafeFuture<SignedBlockContainer> unblindSignedBlockIfBlinded(
      final SignedBlockContainer maybeBlindedBlockContainer) {
    if (maybeBlindedBlockContainer.isBlinded()) {
      return unblindBlock(maybeBlindedBlockContainer)
          .thenApply(this::createUnblindedSignedBlockContents);
    }
    return SafeFuture.completedFuture(maybeBlindedBlockContainer);
  }

  private BlockContents createBlockContents(
      final BeaconBlock block, final List<BlobSidecarOld> blobSidecars) {
    return schemaDefinitionsDeneb.getBlockContentsSchema().create(block, blobSidecars);
  }

  /** use {@link BlockFactoryPhase0} unblinding of the {@link SignedBeaconBlock} */
  private SafeFuture<SignedBeaconBlock> unblindBlock(
      final SignedBlockContainer blindedBlockContainer) {
    return super.unblindSignedBlockIfBlinded(blindedBlockContainer)
        .thenApply(SignedBlockContainer::getSignedBlock);
  }

  @Deprecated
  // TODO: add blobs and proofs
  private SignedBlockContents createUnblindedSignedBlockContents(
      final SignedBeaconBlock signedBlock) {
    return schemaDefinitionsDeneb.getSignedBlockContentsSchema().create(signedBlock, List.of());
  }
}
