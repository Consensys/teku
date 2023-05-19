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
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;

public class BlockContents extends Container2<BlockContents, BeaconBlock, SszList<BlobSidecar>>
    implements BlockContainer {

  BlockContents(final BlockContentsSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlockContents(
      final BlockContentsSchema schema,
      final BeaconBlock beaconBlock,
      final List<BlobSidecar> blobSidecars) {
    super(schema, beaconBlock, schema.getBlobSidecarsSchema().createFromElements(blobSidecars));
  }

  @Override
  public BeaconBlock getBlock() {
    return getField0();
  }

  @Override
  public Optional<List<BlobSidecar>> getBlobSidecars() {
    return Optional.of(getField1().asList());
  }
}
