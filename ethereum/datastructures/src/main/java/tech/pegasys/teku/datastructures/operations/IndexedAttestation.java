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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
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
    return hashTreeRoot();
  }
}
