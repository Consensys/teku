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
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;

public class SignedBlockContents
    extends Container2<SignedBlockContents, SignedBeaconBlock, SszList<SignedBlobSidecar>>
    implements SignedBlockContainer {

  SignedBlockContents(final SignedBlockContentsSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBlockContents(
      final SignedBlockContentsSchema schema,
      final SignedBeaconBlock signedBeaconBlock,
      final List<SignedBlobSidecar> signedBlobSidecars) {
    super(
        schema,
        signedBeaconBlock,
        schema.getSignedBlobSidecarsSchema().createFromElements(signedBlobSidecars));
  }

  @Override
  public SignedBeaconBlock getSignedBlock() {
    return getField0();
  }

  @Override
  public Optional<List<SignedBlobSidecar>> getSignedBlobSidecars() {
    return Optional.of(getField1().asList());
  }
}
