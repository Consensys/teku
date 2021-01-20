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

import java.util.ArrayList;
import java.util.Arrays;
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractBasicView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;
import tech.pegasys.teku.util.config.Constants;

public class IndexedAttestation
    extends Container3<
        IndexedAttestation, ListViewRead<UInt64View>, AttestationData, VectorViewRead<ByteView>>
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  public static class IndexedAttestationType
      extends ContainerType3<
          IndexedAttestation, ListViewRead<UInt64View>, AttestationData, VectorViewRead<ByteView>> {

    static final ListViewType<UInt64View> AttestingIndicesType =
        new ListViewType<>(BasicViewTypes.UINT64_TYPE, Constants.MAX_VALIDATORS_PER_COMMITTEE);

    public IndexedAttestationType() {
      super(
          AttestingIndicesType,
          AttestationData.TYPE,
          new VectorViewType<>(BasicViewTypes.BYTE_TYPE, 96));
    }

    @Override
    public IndexedAttestation createFromBackingNode(TreeNode node) {
      return new IndexedAttestation(this, node);
    }
  }

  @SszTypeDescriptor public static final IndexedAttestationType TYPE = new IndexedAttestationType();

  private SSZList<UInt64> attesting_indices; // List bounded by MAX_VALIDATORS_PER_COMMITTEE
  private AttestationData data;
  private BLSSignature signature;

  public IndexedAttestation(IndexedAttestationType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public IndexedAttestation(
      SSZList<UInt64> attesting_indices, AttestationData data, BLSSignature signature) {
    super(
        TYPE,
        ViewUtils.toListView(
            IndexedAttestationType.AttestingIndicesType, attesting_indices, UInt64View::new),
        data,
        ViewUtils.createVectorFromBytes(signature.toBytesCompressed()));
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + data.getSSZFieldCount() + signature.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(List.of(Bytes.EMPTY));
    fixedPartsList.addAll(data.get_fixed_parts());
    fixedPartsList.addAll(signature.get_fixed_parts());
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    variablePartsList.add(
        SSZ.encode(
            writer ->
                attesting_indices.stream()
                    .forEach(value -> writer.writeUInt64(value.longValue()))));
    variablePartsList.addAll(Collections.nCopies(data.getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(Collections.nCopies(signature.getSSZFieldCount(), Bytes.EMPTY));
    return variablePartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(attesting_indices, data, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof IndexedAttestation)) {
      return false;
    }

    IndexedAttestation other = (IndexedAttestation) obj;
    return Objects.equals(this.getAttesting_indices(), other.getAttesting_indices())
        && Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public SSZList<UInt64> getAttesting_indices() {
    return new SSZBackingList<>(UInt64.class, getField0(), UInt64View::new, AbstractBasicView::get);
  }

  public AttestationData getData() {
    return getField1();
  }

  public BLSSignature getSignature() {
    return BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField2()));
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root_list_ul(
                attesting_indices.map(Bytes.class, item -> SSZ.encodeUInt64(item.longValue()))),
            data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, signature.toSSZBytes())));
  }
}
