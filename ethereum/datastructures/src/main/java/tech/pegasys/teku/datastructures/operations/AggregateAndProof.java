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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class AggregateAndProof
    extends Container3<AggregateAndProof, UInt64View, Attestation, VectorViewRead<ByteView>>
    implements SimpleOffsetSerializable, SSZContainer, Merkleizable {

  public static class AggregateAndProofType
      extends ContainerType3<AggregateAndProof, UInt64View, Attestation, VectorViewRead<ByteView>> {

    public AggregateAndProofType() {
      super(
          BasicViewTypes.UINT64_TYPE,
          Attestation.TYPE,
          new VectorViewType<>(BasicViewTypes.BYTE_TYPE, 96));
    }

    @Override
    public AggregateAndProof createFromBackingNode(TreeNode node) {
      return new AggregateAndProof(this, node);
    }
  }

  @SszTypeDescriptor public static final AggregateAndProofType TYPE = new AggregateAndProofType();

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;

  private UInt64 index;
  private Attestation aggregate;
  private BLSSignature selection_proof;

  public AggregateAndProof(
      ContainerType3<AggregateAndProof, UInt64View, Attestation, VectorViewRead<ByteView>> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public AggregateAndProof(UInt64 index, Attestation aggregate, BLSSignature selection_proof) {
    super(
        TYPE,
        new UInt64View(index),
        aggregate,
        ViewUtils.createVectorFromBytes(selection_proof.toBytesCompressed()));
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + selection_proof.getSSZFieldCount() + aggregate.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.add(SSZ.encodeUInt64(index.longValue()));
    fixedPartsList.add(Bytes.EMPTY);
    fixedPartsList.addAll(selection_proof.get_fixed_parts());
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    variablePartsList.add(Bytes.EMPTY);
    variablePartsList.add(SimpleOffsetSerializer.serialize(aggregate));
    variablePartsList.addAll(Collections.nCopies(selection_proof.getSSZFieldCount(), Bytes.EMPTY));
    return variablePartsList;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("index", index)
        .add("selection_proof", selection_proof)
        .add("aggregate", aggregate)
        .toString();
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UInt64 getIndex() {
    return getField0().get();
  }

  public Attestation getAggregate() {
    return getField1();
  }

  public BLSSignature getSelection_proof() {
    return BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField2()));
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
