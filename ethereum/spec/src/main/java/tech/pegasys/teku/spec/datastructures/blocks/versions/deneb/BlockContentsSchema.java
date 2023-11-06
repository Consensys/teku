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

package tech.pegasys.teku.spec.datastructures.blocks.versions.deneb;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarOld;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchemaOld;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;

public class BlockContentsSchema
    extends ContainerSchema2<BlockContents, BeaconBlock, SszList<BlobSidecarOld>>
    implements BlockContainerSchema<BlockContents> {

  static final SszFieldName FIELD_BLOB_SIDECARS = () -> "blob_sidecars";

  BlockContentsSchema(
      final String containerName,
      final SpecConfigDeneb specConfig,
      final BeaconBlockSchema beaconBlockSchema,
      final BlobSidecarSchemaOld blobSidecarSchema) {
    super(
        containerName,
        namedSchema("block", beaconBlockSchema),
        namedSchema(
            FIELD_BLOB_SIDECARS,
            SszListSchema.create(blobSidecarSchema, specConfig.getMaxBlobsPerBlock())));
  }

  public static BlockContentsSchema create(
      final SpecConfigDeneb specConfig,
      final BeaconBlockSchema beaconBlockSchema,
      final BlobSidecarSchemaOld blobSidecarSchema,
      final String containerName) {
    return new BlockContentsSchema(containerName, specConfig, beaconBlockSchema, blobSidecarSchema);
  }

  public BlockContents create(final BeaconBlock beaconBlock, final List<BlobSidecarOld> blobSidecars) {
    return new BlockContents(this, beaconBlock, blobSidecars);
  }

  @Override
  public BlockContents createFromBackingNode(final TreeNode node) {
    return new BlockContents(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<BlobSidecarOld, ?> getBlobSidecarsSchema() {
    return (SszListSchema<BlobSidecarOld, ?>) getChildSchema(getFieldIndex(FIELD_BLOB_SIDECARS));
  }
}
