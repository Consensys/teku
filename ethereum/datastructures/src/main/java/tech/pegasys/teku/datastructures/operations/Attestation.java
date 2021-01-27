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
import java.util.Collection;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.ComplexViewTypes;
import tech.pegasys.teku.ssz.backing.type.ComplexViewTypes.BitListType;
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

  private static final BitListType AGGREGATION_BITS_TYPE =
      new BitListType(Constants.MAX_VALIDATORS_PER_COMMITTEE);

  public static class AttestationType
      extends ContainerType3<
          Attestation, ListViewRead<BitView>, AttestationData, VectorViewRead<ByteView>> {

    public AttestationType() {
      super(AGGREGATION_BITS_TYPE, AttestationData.TYPE, ComplexViewTypes.BYTES_96_TYPE);
    }

    @Override
    public Attestation createFromBackingNode(TreeNode node) {
      return new Attestation(this, node);
    }
  }

  @SszTypeDescriptor public static final AttestationType TYPE = new AttestationType();

  private Bitlist aggregationBitsCache;
  private BLSSignature signatureCache;

  private Attestation(AttestationType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Attestation(Bitlist aggregation_bits, AttestationData data, BLSSignature signature) {
    super(
        TYPE,
        ViewUtils.createBitlistView(AGGREGATION_BITS_TYPE, aggregation_bits),
        data,
        ViewUtils.createVectorFromBytes(signature.toBytesCompressed()));
    aggregationBitsCache = aggregation_bits;
    signatureCache = signature;
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

  public Bitlist getAggregation_bits() {
    if (aggregationBitsCache == null) {
      aggregationBitsCache = ViewUtils.getBitlist(getField0());
    }
    return aggregationBitsCache;
  }

  public AttestationData getData() {
    return getField1();
  }

  public BLSSignature getAggregate_signature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField2()));
    }
    return signatureCache;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("aggregation_bits", getAggregation_bits())
        .add("data", getData())
        .add("signature", getAggregate_signature())
        .toString();
  }
}
