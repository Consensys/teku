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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;

public class BlindedBlockContentsSchema
    extends ContainerSchema2<BlindedBlockContents, BeaconBlock, BlindedBlobSidecars> {

  BlindedBlockContentsSchema(
      final BeaconBlockSchema beaconBlockSchema,
      final BlindedBlobSidecarsSchema blindedBlobSidecarsSchema) {
    super(
        "BlindedBlockContents",
        namedSchema("blinded_block", beaconBlockSchema),
        namedSchema("blinded_blob_sidecars", blindedBlobSidecarsSchema));
  }

  public static BlindedBlockContentsSchema create(
      final BeaconBlockSchema beaconBlockSchema,
      final BlindedBlobSidecarsSchema blindedBlobSidecarsSchema) {
    return new BlindedBlockContentsSchema(beaconBlockSchema, blindedBlobSidecarsSchema);
  }

  public BlindedBlockContents create(
      final BeaconBlock beaconBlock, final BlindedBlobSidecars blindedBlobSidecars) {
    return new BlindedBlockContents(this, beaconBlock, blindedBlobSidecars);
  }

  @Override
  public BlindedBlockContents createFromBackingNode(final TreeNode node) {
    return new BlindedBlockContents(this, node);
  }

  public BlindedBlobSidecarsSchema getBlindedBlobSidecarsSchema() {
    return (BlindedBlobSidecarsSchema) getFieldSchema1();
  }
}
