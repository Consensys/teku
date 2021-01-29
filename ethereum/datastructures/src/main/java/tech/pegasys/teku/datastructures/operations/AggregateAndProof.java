/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.operations;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ComplexViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class AggregateAndProof
    extends Container3<AggregateAndProof, UInt64View, Attestation, VectorViewRead<ByteView>> {

  public static class AggregateAndProofType
      extends ContainerType3<AggregateAndProof, UInt64View, Attestation, VectorViewRead<ByteView>> {

    public AggregateAndProofType() {
      super(BasicViewTypes.UINT64_TYPE, Attestation.TYPE, ComplexViewTypes.BYTES_96_TYPE);
    }

    @Override
    public AggregateAndProof createFromBackingNode(TreeNode node) {
      return new AggregateAndProof(this, node);
    }
  }

  @SszTypeDescriptor public static final AggregateAndProofType TYPE = new AggregateAndProofType();

  private BLSSignature selectionProofCache;

  private AggregateAndProof(AggregateAndProofType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public AggregateAndProof(UInt64 index, Attestation aggregate, BLSSignature selection_proof) {
    super(
        TYPE,
        new UInt64View(index),
        aggregate,
        ViewUtils.createVectorFromBytes(selection_proof.toBytesCompressed()));
    selectionProofCache = selection_proof;
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public Attestation getAggregate() {
    return getField1();
  }

  public BLSSignature getSelection_proof() {
    if (selectionProofCache == null) {
      selectionProofCache = BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField2()));
    }
    return selectionProofCache;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("index", getIndex())
        .add("selection_proof", getSelection_proof())
        .add("aggregate", getAggregate())
        .toString();
  }
}
