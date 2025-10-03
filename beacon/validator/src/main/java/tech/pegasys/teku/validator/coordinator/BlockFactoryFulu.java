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

import java.util.Collections;
import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.fulu.BlockContentsFulu;
import tech.pegasys.teku.spec.datastructures.blocks.versions.fulu.BlockContentsSchemaFulu;
import tech.pegasys.teku.spec.datastructures.execution.BlobsCellBundle;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class BlockFactoryFulu extends BlockFactoryDeneb {

  private final KZG kzg;

  public BlockFactoryFulu(
      final Spec spec, final BlockOperationSelectorFactory operationSelector, final KZG kzg) {
    super(spec, operationSelector);
    this.kzg = kzg;
  }

  // No blob sidecars in Fulu
  @Override
  public List<BlobSidecar> createBlobSidecars(final SignedBlockContainer blockContainer) {
    return Collections.emptyList();
  }

  @Override
  public List<DataColumnSidecar> createDataColumnSidecars(
      final SignedBlockContainer blockContainer) {
    return operationSelector.createDataColumnSidecarsSelector(kzg).apply(blockContainer);
  }

  @Override
  protected SafeFuture<BlockContainer> createBlockContents(final BeaconBlock block) {
    // The execution BlobsCellBundle has been cached as part of the block creation
    return operationSelector
        .createBlobsCellBundleSelector()
        .apply(block)
        .thenApply(blobsCellBundle -> createBlockContents(block, blobsCellBundle));
  }

  private BlockContentsFulu createBlockContents(
      final BeaconBlock block, final BlobsCellBundle blobsCellBundle) {
    return getBlockContentsSchema(block.getSlot())
        .create(block, blobsCellBundle.getProofs(), blobsCellBundle.getBlobs());
  }

  private BlockContentsSchemaFulu getBlockContentsSchema(final UInt64 slot) {
    return (BlockContentsSchemaFulu)
        SchemaDefinitionsFulu.required(spec.atSlot(slot).getSchemaDefinitions())
            .getBlockContentsSchema();
  }
}
