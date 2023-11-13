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

package tech.pegasys.teku.spec.datastructures.blobs.versions.deneb;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container6;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class BlobSidecar
    extends Container6<
        BlobSidecar,
        SszUInt64,
        Blob,
        SszKZGCommitment,
        SszKZGProof,
        SignedBeaconBlockHeader,
        SszBytes32Vector> {

  BlobSidecar(final BlobSidecarSchema blobSidecarSchema, final TreeNode backingTreeNode) {
    super(blobSidecarSchema, backingTreeNode);
  }

  public BlobSidecar(
      final BlobSidecarSchema schema,
      final UInt64 index,
      final Blob blob,
      final KZGCommitment kzgCommitment,
      final KZGProof kzgProof,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<Bytes32> kzgCommitmentInclusionProof) {
    super(
        schema,
        SszUInt64.of(index),
        schema.getBlobSchema().create(blob.getBytes()),
        new SszKZGCommitment(kzgCommitment),
        new SszKZGProof(kzgProof),
        signedBeaconBlockHeader,
        schema
            .getKzgCommitmentInclusionProofSchema()
            .createFromElements(kzgCommitmentInclusionProof.stream().map(SszBytes32::of).toList()));
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public Blob getBlob() {
    return getField1();
  }

  public KZGCommitment getKZGCommitment() {
    return getField2().getKZGCommitment();
  }

  public KZGProof getKZGProof() {
    return getField3().getKZGProof();
  }

  public SignedBeaconBlockHeader getSignedBeaconBlockHeader() {
    return getField4();
  }

  public List<Bytes32> getKzgCommitmentInclusionProof() {
    return getField5().stream().map(AbstractSszPrimitive::get).toList();
  }

  public UInt64 getSlot() {
    return getSignedBeaconBlockHeader().getMessage().getSlot();
  }

  public Bytes32 getBlockRoot() {
    return getSignedBeaconBlockHeader().getMessage().getRoot();
  }

  public SlotAndBlockRoot getSlotAndBlockRoot() {
    return new SlotAndBlockRoot(getSlot(), getBlockRoot());
  }

  public String toLogString() {
    return LogFormatter.formatBlobSidecar(
        getSlot(),
        getBlockRoot(),
        getIndex(),
        getBlob().toBriefString(),
        getKZGCommitment().toHexString(),
        getKZGProof().toAbbreviatedString());
  }
}
