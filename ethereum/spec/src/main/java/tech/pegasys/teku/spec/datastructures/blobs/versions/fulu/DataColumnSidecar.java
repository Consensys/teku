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

package tech.pegasys.teku.spec.datastructures.blobs.versions.fulu;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container6;
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

public class DataColumnSidecar
    extends Container6<
        DataColumnSidecar,
        SszUInt64,
        DataColumn,
        SszList<SszKZGCommitment>,
        SszList<SszKZGProof>,
        SignedBeaconBlockHeader,
        SszBytes32Vector> {

  DataColumnSidecar(
      final DataColumnSidecarSchema dataColumnSidecarSchema, final TreeNode backingTreeNode) {
    super(dataColumnSidecarSchema, backingTreeNode);
  }

  public DataColumnSidecar(
      final DataColumnSidecarSchema schema,
      final UInt64 index,
      final DataColumn dataColumn,
      final List<KZGCommitment> kzgCommitments,
      final List<KZGProof> kzgProofs,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<Bytes32> kzgCommitmentsInclusionProof) {
    super(
        schema,
        SszUInt64.of(index),
        dataColumn,
        schema
            .getKzgCommitmentsSchema()
            .createFromElements(kzgCommitments.stream().map(SszKZGCommitment::new).toList()),
        schema
            .getKzgProofsSchema()
            .createFromElements(kzgProofs.stream().map(SszKZGProof::new).toList()),
        signedBeaconBlockHeader,
        schema
            .getKzgCommitmentsInclusionProofSchema()
            .createFromElements(
                kzgCommitmentsInclusionProof.stream().map(SszBytes32::of).toList()));
  }

  public DataColumnSidecar(
      final DataColumnSidecarSchema schema,
      final UInt64 index,
      final DataColumn dataColumn,
      final SszList<SszKZGCommitment> sszKzgCommitments,
      final SszList<SszKZGProof> sszKzgProofs,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<Bytes32> kzgCommitmentsInclusionProof) {
    super(
        schema,
        SszUInt64.of(index),
        dataColumn,
        sszKzgCommitments,
        sszKzgProofs,
        signedBeaconBlockHeader,
        schema
            .getKzgCommitmentsInclusionProofSchema()
            .createFromElements(
                kzgCommitmentsInclusionProof.stream().map(SszBytes32::of).toList()));
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public DataColumn getDataColumn() {
    return getField1();
  }

  public SszList<SszKZGCommitment> getSszKZGCommitments() {
    return getField2();
  }

  public SszList<SszKZGProof> getSszKZGProofs() {
    return getField3();
  }

  public SignedBeaconBlockHeader getSignedBeaconBlockHeader() {
    return getField4();
  }

  public SszBytes32Vector getKzgCommitmentsInclusionProof() {
    return getField5();
  }

  public UInt64 getSlot() {
    return getSignedBeaconBlockHeader().getMessage().getSlot();
  }

  public Bytes32 getBlockBodyRoot() {
    return getSignedBeaconBlockHeader().getMessage().getBodyRoot();
  }

  public Bytes32 getBlockRoot() {
    return getSignedBeaconBlockHeader().getMessage().getRoot();
  }

  public SlotAndBlockRoot getSlotAndBlockRoot() {
    return new SlotAndBlockRoot(getSlot(), getBlockRoot());
  }

  public String toLogString() {
    return LogFormatter.formatDataColumnSidecar(
        getSlot(),
        getBlockRoot(),
        getIndex(),
        getDataColumn().toBriefString(),
        getSszKZGCommitments().size(),
        getSszKZGProofs().size());
  }
}
