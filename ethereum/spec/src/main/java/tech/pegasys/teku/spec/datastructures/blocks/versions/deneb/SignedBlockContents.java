/*
 * Copyright Consensys Software Inc., 2023
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
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class SignedBlockContents
    extends Container3<SignedBlockContents, SignedBeaconBlock, SszList<SszKZGProof>, SszList<Blob>>
    implements SignedBlockContainer {

  SignedBlockContents(final SignedBlockContentsSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBlockContents(
      final SignedBlockContentsSchema schema,
      final SignedBeaconBlock signedBeaconBlock,
      final List<KZGProof> kzgProofs,
      final List<Blob> blobs) {
    this(
        schema,
        signedBeaconBlock,
        schema
            .getKzgProofsSchema()
            .createFromElements(kzgProofs.stream().map(SszKZGProof::new).toList()),
        schema.getBlobsSchema().createFromElements(blobs));
  }

  public SignedBlockContents(
      final SignedBlockContentsSchema schema,
      final SignedBeaconBlock signedBeaconBlock,
      final SszList<SszKZGProof> kzgProofs,
      final SszList<Blob> blobs) {
    super(schema, signedBeaconBlock, kzgProofs, blobs);
  }

  @Override
  public SignedBeaconBlock getSignedBlock() {
    return getField0();
  }

  @Override
  public Optional<SszList<SszKZGProof>> getKzgProofs() {
    return Optional.of(getField1());
  }

  @Override
  public Optional<SszList<Blob>> getBlobs() {
    return Optional.of(getField2());
  }
}
