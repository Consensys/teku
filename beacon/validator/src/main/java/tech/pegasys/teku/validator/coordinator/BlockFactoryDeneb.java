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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class BlockFactoryDeneb extends BlockFactoryPhase0 {

  private final SchemaDefinitionsDeneb schemaDefinitionsDeneb;

  public BlockFactoryDeneb(final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    super(spec, operationSelector);
    this.schemaDefinitionsDeneb =
        SchemaDefinitionsDeneb.required(
            spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions());
  }

  @Override
  public SafeFuture<BlockContainer> createUnsignedBlock(
      final BeaconState blockSlotState,
      final UInt64 newSlot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti,
      final boolean blinded) {
    return super.createUnsignedBlock(
            blockSlotState, newSlot, randaoReveal, optionalGraffiti, blinded)
        .thenApply(BlockContainer::getBlock)
        .thenCompose(
            block -> {
              if (block.isBlinded()) {
                return operationSelector
                    .createBlindedBlobSidecarsSelector()
                    .apply(block)
                    .thenApply(
                        blindedBlobSidecars ->
                            createBlindedBlockContents(block, blindedBlobSidecars));
              }
              return operationSelector
                  .createBlobSidecarsSelector()
                  .apply(block)
                  .thenApply(blobSidecars -> createBlockContents(block, blobSidecars));
            });
  }

  private BlockContents createBlockContents(
      final BeaconBlock block, final List<BlobSidecar> blobSidecars) {
    return schemaDefinitionsDeneb.getBlockContentsSchema().create(block, blobSidecars);
  }

  private BlindedBlockContents createBlindedBlockContents(
      final BeaconBlock block, final List<BlindedBlobSidecar> blindedBlobSidecars) {
    return schemaDefinitionsDeneb
        .getBlindedBlockContentsSchema()
        .create(block, blindedBlobSidecars);
  }
}
