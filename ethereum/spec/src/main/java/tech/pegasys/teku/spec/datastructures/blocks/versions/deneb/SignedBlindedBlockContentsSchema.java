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
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;

public class SignedBlindedBlockContentsSchema
    extends ContainerSchema2<
        SignedBlindedBlockContents, SignedBeaconBlock, SszList<SignedBlindedBlobSidecar>>
    implements SignedBlockContainerSchema<SignedBlindedBlockContents> {

  static final SszFieldName FIELD_BLOB_SIDECARS = () -> "signed_blinded_blob_sidecars";

  SignedBlindedBlockContentsSchema(
      final String containerName,
      final SpecConfigDeneb specConfig,
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final SignedBlindedBlobSidecarSchema signedBlindedBlobSidecarSchema) {
    super(
        containerName,
        namedSchema("signed_blinded_block", signedBeaconBlockSchema),
        namedSchema(
            FIELD_BLOB_SIDECARS,
            SszListSchema.create(
                signedBlindedBlobSidecarSchema, specConfig.getMaxBlobsPerBlock())));
  }

  public static SignedBlindedBlockContentsSchema create(
      final SpecConfigDeneb specConfig,
      final SignedBlindedBlobSidecarSchema signedBlindedBlobSidecarSchema,
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final String containerName) {
    return new SignedBlindedBlockContentsSchema(
        containerName, specConfig, signedBeaconBlockSchema, signedBlindedBlobSidecarSchema);
  }

  public SignedBlindedBlockContents create(
      final SignedBeaconBlock signedBeaconBlock,
      final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars) {
    return new SignedBlindedBlockContents(this, signedBeaconBlock, signedBlindedBlobSidecars);
  }

  @Override
  public SignedBlindedBlockContents createFromBackingNode(final TreeNode node) {
    return new SignedBlindedBlockContents(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SignedBlindedBlobSidecar, ?> getSignedBlindedBlobSidecarsSchema() {
    return (SszListSchema<SignedBlindedBlobSidecar, ?>) getFieldSchema1();
  }
}
