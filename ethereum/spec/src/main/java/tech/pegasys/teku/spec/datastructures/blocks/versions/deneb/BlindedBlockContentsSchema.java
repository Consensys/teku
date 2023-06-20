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

package tech.pegasys.teku.spec.datastructures.blocks.versions.deneb;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;

public class BlindedBlockContentsSchema
    extends ContainerSchema2<BlindedBlockContents, BeaconBlock, SszList<BlindedBlobSidecar>>
    implements BlockContainerSchema<BlindedBlockContents> {

  static final SszFieldName FIELD_BLINDED_BLOB_SIDECARS = () -> "blinded_blob_sidecars";

  BlindedBlockContentsSchema(
      final String containerName,
      final SpecConfigDeneb specConfig,
      final BeaconBlockSchema beaconBlockSchema,
      final BlindedBlobSidecarSchema blindedBlobSidecarSchema) {
    super(
        containerName,
        namedSchema("blinded_block", beaconBlockSchema),
        namedSchema(
            FIELD_BLINDED_BLOB_SIDECARS,
            SszListSchema.create(blindedBlobSidecarSchema, specConfig.getMaxBlobsPerBlock())));
  }

  public static BlindedBlockContentsSchema create(
      final SpecConfigDeneb specConfig,
      final BlindedBlobSidecarSchema blindedBlobSidecarSchema,
      final BeaconBlockSchema beaconBlockSchema,
      final String containerName) {
    return new BlindedBlockContentsSchema(
        containerName, specConfig, beaconBlockSchema, blindedBlobSidecarSchema);
  }

  public BlindedBlockContents create(
      final BeaconBlock beaconBlock, final List<BlindedBlobSidecar> blindedBlobSidecars) {
    return new BlindedBlockContents(this, beaconBlock, blindedBlobSidecars);
  }

  @Override
  public BlindedBlockContents createFromBackingNode(final TreeNode node) {
    return new BlindedBlockContents(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<BlindedBlobSidecar, ?> getBlindedBlobSidecarsSchema() {
    return (SszListSchema<BlindedBlobSidecar, ?>)
        getChildSchema(getFieldIndex(FIELD_BLINDED_BLOB_SIDECARS));
  }
}
