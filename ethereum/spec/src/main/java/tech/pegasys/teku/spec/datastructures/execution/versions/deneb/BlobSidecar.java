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

package tech.pegasys.teku.spec.datastructures.execution.versions.deneb;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container8;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class BlobSidecar
    extends Container8<
        BlobSidecar,
        SszBytes32,
        SszUInt64,
        SszUInt64,
        SszBytes32,
        SszUInt64,
        Blob,
        SszKZGCommitment,
        SszKZGProof> {

  BlobSidecar(final BlobSidecarSchema blobSidecarSchema, final TreeNode backingTreeNode) {
    super(blobSidecarSchema, backingTreeNode);
  }

  public BlobSidecar(
      BlobSidecarSchema schema,
      Bytes32 blockRoot,
      UInt64 index,
      UInt64 slot,
      Bytes32 blockParentRoot,
      UInt64 proposerIndex,
      Blob blob,
      KZGCommitment kzgCommitment,
      KZGProof kzgProof) {
    super(
        schema,
        SszBytes32.of(blockRoot),
        SszUInt64.of(index),
        SszUInt64.of(slot),
        SszBytes32.of(blockParentRoot),
        SszUInt64.of(proposerIndex),
        schema.getBlobSchema().create(blob.getBytes()),
        new SszKZGCommitment(kzgCommitment),
        new SszKZGProof(kzgProof));
  }

  public Bytes32 getBlockRoot() {
    return getField0().get();
  }

  public UInt64 getIndex() {
    return getField1().get();
  }

  public UInt64 getSlot() {
    return getField2().get();
  }

  public Bytes32 getBlockParentRoot() {
    return getField3().get();
  }

  public UInt64 getProposerIndex() {
    return getField4().get();
  }

  public Blob getBlob() {
    return getField5();
  }

  public KZGCommitment getKZGCommitment() {
    return getField6().getKZGCommitment();
  }

  public KZGProof getKZGProof() {
    return getField7().getKZGProof();
  }
}
