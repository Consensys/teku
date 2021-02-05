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

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ComplexViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.ByteView;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.UInt64View;
import tech.pegasys.teku.ssz.backing.view.SszUtils;
import tech.pegasys.teku.util.config.Constants;

public class IndexedAttestation
    extends Container3<
        IndexedAttestation, SszList<UInt64View>, AttestationData, SszVector<ByteView>> {

  public static class IndexedAttestationType
      extends ContainerType3<
          IndexedAttestation, SszList<UInt64View>, AttestationData, SszVector<ByteView>> {

    public IndexedAttestationType() {
      super(
          "IndexedAttestation",
          namedType(
              "attesting_indices",
              new ListViewType<>(
                  BasicViewTypes.UINT64_TYPE, Constants.MAX_VALIDATORS_PER_COMMITTEE)),
          namedType("data", AttestationData.TYPE),
          namedType("signature", ComplexViewTypes.BYTES_96_TYPE));
    }

    public ListViewType<UInt64View> getAttestingIndicesType() {
      return (ListViewType<UInt64View>) getFieldType0();
    }

    @Override
    public IndexedAttestation createFromBackingNode(TreeNode node) {
      return new IndexedAttestation(this, node);
    }
  }

  public static final IndexedAttestationType TYPE = new IndexedAttestationType();

  private BLSSignature signatureCache;

  private IndexedAttestation(IndexedAttestationType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public IndexedAttestation(
      SSZList<UInt64> attesting_indices, AttestationData data, BLSSignature signature) {
    super(
        TYPE,
        SszUtils.toListView(TYPE.getAttestingIndicesType(), attesting_indices, UInt64View::new),
        data,
        SszUtils.createVectorFromBytes(signature.toBytesCompressed()));
    this.signatureCache = signature;
  }

  public SSZList<UInt64> getAttesting_indices() {
    return new SSZBackingList<>(UInt64.class, getField0(), UInt64View::new, AbstractSszPrimitive::get);
  }

  public AttestationData getData() {
    return getField1();
  }

  public BLSSignature getSignature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(SszUtils.getAllBytes(getField2()));
    }
    return signatureCache;
  }
}
