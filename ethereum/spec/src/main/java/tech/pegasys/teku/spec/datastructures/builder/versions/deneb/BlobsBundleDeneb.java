/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.builder.versions.deneb;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class BlobsBundleDeneb
    extends Container3<
        BlobsBundleDeneb, SszList<SszKZGCommitment>, SszList<SszKZGProof>, SszList<Blob>> {

  BlobsBundleDeneb(final BlobsBundleSchemaDeneb type, final TreeNode backingTreeNode) {
    super(type, backingTreeNode);
  }

  public BlobsBundleDeneb(
      final BlobsBundleSchemaDeneb schema,
      final SszList<SszKZGCommitment> commitments,
      final SszList<SszKZGProof> proofs,
      final SszList<Blob> blobs) {
    super(schema, commitments, proofs, blobs);
    checkArgument(
        commitments.size() == blobs.size(),
        "Expected %s commitments but got %s",
        blobs.size(),
        commitments.size());
    checkArgument(
        proofs.size() == blobs.size(),
        "Expected %s proofs but got %s",
        blobs.size(),
        proofs.size());
  }

  public SszList<SszKZGCommitment> getCommitments() {
    return getField0();
  }

  public SszList<SszKZGProof> getProofs() {
    return getField1();
  }

  public SszList<Blob> getBlobs() {
    return getField2();
  }

  public int getNumberOfBlobs() {
    return getBlobs().size();
  }
}
