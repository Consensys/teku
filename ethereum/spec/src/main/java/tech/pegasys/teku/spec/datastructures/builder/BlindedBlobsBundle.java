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

package tech.pegasys.teku.spec.datastructures.builder;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class BlindedBlobsBundle
    extends Container3<
        BlindedBlobsBundle, SszList<SszKZGCommitment>, SszList<SszKZGProof>, SszList<SszBytes32>> {

  BlindedBlobsBundle(final BlindedBlobsBundleSchema type, final TreeNode backingTreeNode) {
    super(type, backingTreeNode);
  }

  public BlindedBlobsBundle(
      final BlindedBlobsBundleSchema schema,
      final SszList<SszKZGCommitment> commitments,
      final SszList<SszKZGProof> proofs,
      final SszList<SszBytes32> blobRoots) {
    super(schema, commitments, proofs, blobRoots);
  }

  public SszList<SszKZGCommitment> getCommitments() {
    return getField0();
  }

  public SszList<SszKZGProof> getProofs() {
    return getField1();
  }

  public SszList<SszBytes32> getBlobRoots() {
    return getField2();
  }

  public int getNumberOfBlobs() {
    return getBlobRoots().size();
  }
}
