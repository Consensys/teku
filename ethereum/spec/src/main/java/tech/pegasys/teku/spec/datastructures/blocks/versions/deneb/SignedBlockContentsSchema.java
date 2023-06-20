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
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;

public class SignedBlockContentsSchema
    extends ContainerSchema2<SignedBlockContents, SignedBeaconBlock, SszList<SignedBlobSidecar>>
    implements SignedBlockContainerSchema<SignedBlockContents> {

  static final SszFieldName FIELD_SIGNED_BLOB_SIDECARS = () -> "signed_blob_sidecars";

  SignedBlockContentsSchema(
      final String containerName,
      final SpecConfigDeneb specConfig,
      final SignedBlobSidecarSchema signedBlobSidecarSchema,
      final SignedBeaconBlockSchema signedBeaconBlockSchema) {
    super(
        containerName,
        namedSchema("signed_block", signedBeaconBlockSchema),
        namedSchema(
            FIELD_SIGNED_BLOB_SIDECARS,
            SszListSchema.create(signedBlobSidecarSchema, specConfig.getMaxBlobsPerBlock())));
  }

  public static SignedBlockContentsSchema create(
      final SpecConfigDeneb specConfig,
      final SignedBlobSidecarSchema signedBlobSidecarSchema,
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final String containerName) {
    return new SignedBlockContentsSchema(
        containerName, specConfig, signedBlobSidecarSchema, signedBeaconBlockSchema);
  }

  public SignedBlockContents create(
      final SignedBeaconBlock signedBeaconBlock, final List<SignedBlobSidecar> signedBlobSidecars) {
    return new SignedBlockContents(this, signedBeaconBlock, signedBlobSidecars);
  }

  @Override
  public SignedBlockContents createFromBackingNode(final TreeNode node) {
    return new SignedBlockContents(this, node);
  }

  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return (SignedBeaconBlockSchema) getFieldSchema0();
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SignedBlobSidecar, ?> getSignedBlobSidecarsSchema() {
    return (SszListSchema<SignedBlobSidecar, ?>)
        getChildSchema(getFieldIndex(FIELD_SIGNED_BLOB_SIDECARS));
  }
}
