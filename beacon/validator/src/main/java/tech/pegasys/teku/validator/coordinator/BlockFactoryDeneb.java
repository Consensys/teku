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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class BlockFactoryDeneb extends BlockFactoryPhase0 {

  public BlockFactoryDeneb(final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    super(spec, operationSelector);
  }

  @Override
  public SafeFuture<BlockContainerAndMetaData> createUnsignedBlock(
      final BeaconState blockSlotState,
      final UInt64 proposalSlot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {
    return super.createUnsignedBlock(
            blockSlotState,
            proposalSlot,
            randaoReveal,
            optionalGraffiti,
            requestedBuilderBoostFactor,
            blockProductionPerformance)
        .thenCompose(
            blockContainerAndMetaData -> {
              final BeaconBlock block = blockContainerAndMetaData.blockContainer().getBlock();
              if (block.isBlinded()) {
                return SafeFuture.completedFuture(blockContainerAndMetaData);
              }
              return createBlockContents(block)
                  .thenApply(blockContainerAndMetaData::withBlockContents);
            });
  }

  @Override
  public List<BlobSidecar> createBlobSidecars(final SignedBlockContainer blockContainer) {
    return operationSelector.createBlobSidecarsSelector().apply(blockContainer);
  }

  private SafeFuture<BlockContainer> createBlockContents(final BeaconBlock block) {
    // The execution BlobsBundle has been cached as part of the block creation
    return operationSelector
        .createBlobsBundleSelector()
        .apply(block)
        .thenApply(blobsBundle -> createBlockContents(block, blobsBundle));
  }

  private BlockContainer createBlockContents(
      final BeaconBlock block, final BlobsBundle blobsBundle) {
    return SchemaDefinitionsDeneb.required(spec.atSlot(block.getSlot()).getSchemaDefinitions())
        .getBlockContentsSchema()
        .create(block, blobsBundle.getProofs(), blobsBundle.getBlobs());
  }
}
