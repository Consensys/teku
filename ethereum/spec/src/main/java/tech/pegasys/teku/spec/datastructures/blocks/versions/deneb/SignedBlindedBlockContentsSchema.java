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

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecars;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecarsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;

public class SignedBlindedBlockContentsSchema
    extends ContainerSchema2<
        SignedBlindedBlockContents, SignedBeaconBlock, SignedBlindedBlobSidecars> {

  SignedBlindedBlockContentsSchema(
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final SignedBlindedBlobSidecarsSchema signedBlindedBlobSidecarsSchema) {
    super(
        "SignedBlindedBlockContents",
        namedSchema("signed_blinded_block", signedBeaconBlockSchema),
        namedSchema("signed_blinded_blob_sidecars", signedBlindedBlobSidecarsSchema));
  }

  public static SignedBlindedBlockContentsSchema create(
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final SignedBlindedBlobSidecarsSchema signedBlindedBlobSidecarsSchema) {
    return new SignedBlindedBlockContentsSchema(
        signedBeaconBlockSchema, signedBlindedBlobSidecarsSchema);
  }

  public SignedBlindedBlockContents create(
      final SignedBeaconBlock signedBeaconBlock,
      final SignedBlindedBlobSidecars signedBlindedBlobSidecars) {
    return new SignedBlindedBlockContents(this, signedBeaconBlock, signedBlindedBlobSidecars);
  }

  @Override
  public SignedBlindedBlockContents createFromBackingNode(final TreeNode node) {
    return new SignedBlindedBlockContents(this, node);
  }

  public SignedBlindedBlobSidecarsSchema getSignedBlindedBlobSidecarsSchema() {
    return (SignedBlindedBlobSidecarsSchema) getFieldSchema1();
  }
}
