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
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecars;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecarsSchema;

public class BlockContentsSchema
    extends ContainerSchema2<BlockContents, BeaconBlock, BlobSidecars> {

  BlockContentsSchema(
      final BeaconBlockSchema beaconBlockSchema, final BlobSidecarsSchema blobSidecarsSchema) {
    super(
        "BlockContents",
        namedSchema("beacon_block", beaconBlockSchema),
        namedSchema("blob_sidecars", blobSidecarsSchema));
  }

  public static BlockContentsSchema create(
      final BeaconBlockSchema beaconBlockSchema, final BlobSidecarsSchema blobSidecarsSchema) {
    return new BlockContentsSchema(beaconBlockSchema, blobSidecarsSchema);
  }

  public BlockContents create(final BeaconBlock beaconBlock, final BlobSidecars blobSidecars) {
    return new BlockContents(this, beaconBlock, blobSidecars);
  }

  @Override
  public BlockContents createFromBackingNode(final TreeNode node) {
    return new BlockContents(this, node);
  }

  public BlobSidecarsSchema getBlobSidecarsSchema() {
    return (BlobSidecarsSchema) getFieldSchema1();
  }
}
