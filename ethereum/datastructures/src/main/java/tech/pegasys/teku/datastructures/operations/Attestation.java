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
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.BitView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;
import tech.pegasys.teku.util.config.Constants;

public class Attestation
    extends Container3<
        Attestation, ListViewRead<BitView>, AttestationData, VectorViewRead<ByteView>>
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  public BLSSignature getSignature() {
    return signature;
  }

  public static class AttestationType
      extends ContainerType3<
          Attestation, ListViewRead<BitView>, AttestationData, VectorViewRead<ByteView>> {

    public AttestationType() {
      super(
          new ListViewType<>(BasicViewTypes.BIT_TYPE, Constants.MAX_VALIDATORS_PER_COMMITTEE),
          AttestationData.TYPE,
          new VectorViewType<>(BasicViewTypes.BYTE_TYPE, 96));
    }

    @Override
    public Attestation createFromBackingNode(TreeNode node) {
      return new Attestation(this, node);
    }
  }

  @SszTypeDescriptor public static final AttestationType TYPE = new AttestationType();

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;

  private Bitlist aggregation_bits; // Bitlist bounded by MAX_VALIDATORS_PER_COMMITTEE
  private AttestationData data;
  private BLSSignature signature;

  public Attestation(
      ContainerType3<Attestation, ListViewRead<BitView>, AttestationData, VectorViewRead<ByteView>>
          type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public Attestation(Bitlist aggregation_bits, AttestationData data, BLSSignature signature) {
    super(
        TYPE,
        ViewUtils.createBitlistView(aggregation_bits),
        data,
        ViewUtils.createVectorFromBytes(signature.toBytesCompressed()));
  }

  public Attestation() {
    super(TYPE);
  }

  public static Bitlist createEmptyAggregationBits() {
    return new Bitlist(
        Constants.MAX_VALIDATORS_PER_COMMITTEE, Constants.MAX_VALIDATORS_PER_COMMITTEE);
  }

  public UInt64 getEarliestSlotForForkChoiceProcessing() {
    return getData().getEarliestSlotForForkChoice();
  }

  public Collection<Bytes32> getDependentBlockRoots() {
    return Sets.newHashSet(getData().getTarget().getRoot(), getData().getBeacon_block_root());
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + getData().getSSZFieldCount() + getSignature().getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(List.of(Bytes.EMPTY));
    fixedPartsList.addAll(getData().get_fixed_parts());
    fixedPartsList.addAll(getSignature().get_fixed_parts());
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    variablePartsList.addAll(List.of(getAggregation_bits().serialize()));
    variablePartsList.addAll(Collections.nCopies(getData().getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(Collections.nCopies(getSignature().getSSZFieldCount(), Bytes.EMPTY));
    return variablePartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getAggregation_bits(), getData(), getSignature());
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Attestation)) {
      return false;
    }

    Attestation other = (Attestation) obj;
    return Objects.equals(this.getAggregation_bits(), other.getAggregation_bits())
        && Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getAggregate_signature(), other.getAggregate_signature());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("aggregation_bits", getAggregation_bits())
        .add("data", getData())
        .add("signature", getSignature())
        .toString();
  }

  public Bitlist getAggregation_bits() {
    return ViewUtils.getBitlist(getField0());
  }

  public AttestationData getData() {
    return data;
  }

  public BLSSignature getAggregate_signature() {
    return getSignature();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root_bitlist(getAggregation_bits()),
            getData().hash_tree_root(),
            HashTreeUtil.hash_tree_root(
                SSZTypes.VECTOR_OF_BASIC, SimpleOffsetSerializer.serialize(getSignature()))));
  }
}
