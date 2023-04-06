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

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class SignedBlindedBlockContents
    extends Container2<SignedBlindedBlockContents, SignedBeaconBlock, SignedBlindedBlobSidecars> {

  SignedBlindedBlockContents(
      final SignedBlindedBlockContentsSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBlindedBlockContents(
      final SignedBlindedBlockContentsSchema schema,
      final SignedBeaconBlock signedBeaconBlock,
      final SignedBlindedBlobSidecars signedBlindedBlobSidecars) {
    super(schema, signedBeaconBlock, signedBlindedBlobSidecars);
  }

  public SignedBeaconBlock getSignedBeaconBlock() {
    return getField0();
  }

  public SignedBlindedBlobSidecars getSignedBlindedBlobSidecars() {
    return getField1();
  }
}
