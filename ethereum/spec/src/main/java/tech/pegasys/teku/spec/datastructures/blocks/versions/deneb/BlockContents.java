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

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecars;

public class BlockContents extends Container2<BlockContents, BeaconBlock, BlobSidecars> {

  BlockContents(final BlockContentsSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlockContents(
      final BlockContentsSchema schema,
      final BeaconBlock beaconBlock,
      final BlobSidecars blobSidecars) {
    super(schema, beaconBlock, blobSidecars);
  }

  public BeaconBlock getBeaconBlock() {
    return getField0();
  }

  public BlobSidecars getBlobSidecars() {
    return getField1();
  }
}
