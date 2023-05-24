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
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlindedBlockContainer;

public class SignedBlindedBlockContents
    extends Container2<
        SignedBlindedBlockContents, SignedBeaconBlock, SszList<SignedBlindedBlobSidecar>>
    implements SignedBlindedBlockContainer {

  SignedBlindedBlockContents(
      final SignedBlindedBlockContentsSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBlindedBlockContents(
      final SignedBlindedBlockContentsSchema schema,
      final SignedBeaconBlock signedBeaconBlock,
      final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars) {
    super(
        schema,
        signedBeaconBlock,
        schema.getSignedBlindedBlobSidecarsSchema().createFromElements(signedBlindedBlobSidecars));
  }

  @Override
  public SignedBeaconBlock getSignedBlock() {
    return getField0();
  }

  @Override
  public Optional<List<SignedBlindedBlobSidecar>> getSignedBlindedBlobSidecars() {
    return Optional.of(getField1().asList());
  }

  @Override
  public Optional<SignedBlindedBlockContainer> toBlinded() {
    return Optional.of(this);
  }
}
