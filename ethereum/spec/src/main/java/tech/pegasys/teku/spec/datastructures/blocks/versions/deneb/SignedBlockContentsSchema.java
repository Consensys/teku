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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecars;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecarsSchema;

public class SignedBlockContentsSchema
    extends ContainerSchema2<SignedBlockContents, SignedBeaconBlock, SignedBlobSidecars> {

  SignedBlockContentsSchema(
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final SignedBlobSidecarsSchema signedBlobSidecarsSchema) {
    super(
        "SignedBlockContents",
        namedSchema("signed_block_contents", signedBeaconBlockSchema),
        namedSchema("signed_blob_sidecars", signedBlobSidecarsSchema));
  }

  public static SignedBlockContentsSchema create(
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final SignedBlobSidecarsSchema signedBlobSidecarsSchema) {
    return new SignedBlockContentsSchema(signedBeaconBlockSchema, signedBlobSidecarsSchema);
  }

  public SignedBlockContents create(
      final SignedBeaconBlock signedBeaconBlock, final SignedBlobSidecars signedBlobSidecars) {
    return new SignedBlockContents(this, signedBeaconBlock, signedBlobSidecars);
  }

  @Override
  public SignedBlockContents createFromBackingNode(final TreeNode node) {
    return new SignedBlockContents(this, node);
  }

  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return (SignedBeaconBlockSchema) getFieldSchema0();
  }

  public SignedBlobSidecarsSchema getSignedBlobSidecarsSchema() {
    return (SignedBlobSidecarsSchema) getFieldSchema1();
  }
}
