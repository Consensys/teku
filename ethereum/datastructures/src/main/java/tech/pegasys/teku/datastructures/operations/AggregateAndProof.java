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
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class AggregateAndProof implements SimpleOffsetSerializable, SSZContainer, Merkleizable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;

  private final UInt64 index;
  private final Attestation aggregate;
  private final BLSSignature selection_proof;

  public AggregateAndProof(UInt64 index, Attestation aggregate, BLSSignature selection_proof) {
    this.index = index;
    this.selection_proof = selection_proof;
    this.aggregate = aggregate;
  }

  public AggregateAndProof() {
    this.index = UInt64.ZERO;
    this.selection_proof = BLSSignature.empty();
    this.aggregate = new Attestation();
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
  public int hashCode() {
    return Objects.hash(index, selection_proof, aggregate);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof AggregateAndProof)) {
      return false;
    }

    AggregateAndProof other = (AggregateAndProof) obj;
    return Objects.equals(this.index, other.index)
        && Objects.equals(this.selection_proof, other.selection_proof)
        && Objects.equals(this.aggregate, other.aggregate);
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
    return index;
  }

  public BLSSignature getSelection_proof() {
    return selection_proof;
  }

  public Attestation getAggregate() {
    return aggregate;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        List.of(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(index.longValue())),
            aggregate.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, selection_proof.toSSZBytes())));
  }
}
