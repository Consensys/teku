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

package tech.pegasys.teku.datastructures.state;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.Copyable;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container4;
import tech.pegasys.teku.ssz.backing.containers.ContainerType4;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.BitView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;
import tech.pegasys.teku.util.config.Constants;

public class PendingAttestation
    extends Container4<
        PendingAttestation, ListViewRead<BitView>, AttestationData, UInt64View, UInt64View>
    implements Copyable<PendingAttestation>, Merkleizable, SimpleOffsetSerializable, SSZContainer {

  private static final ListViewType<BitView> AGGREGATION_BITS_TYPE = new ListViewType<>(
      BasicViewTypes.BIT_TYPE, Constants.MAX_VALIDATORS_PER_COMMITTEE);

  static class PendingAttestationType
      extends ContainerType4<
          PendingAttestation, ListViewRead<BitView>, AttestationData, UInt64View, UInt64View> {

    public PendingAttestationType() {
      super(
          AGGREGATION_BITS_TYPE,
          AttestationData.TYPE,
          BasicViewTypes.UINT64_TYPE,
          BasicViewTypes.UINT64_TYPE);
    }

    @Override
    public PendingAttestation createFromBackingNode(TreeNode node) {
      return new PendingAttestation(this, node);
    }
  }

  @SszTypeDescriptor public static final PendingAttestationType TYPE = new PendingAttestationType();

  private PendingAttestation(PendingAttestationType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public PendingAttestation(
      Bitlist aggregation_bitfield,
      AttestationData data,
      UInt64 inclusion_delay,
      UInt64 proposer_index) {
    super(
        TYPE,
        ViewUtils.createBitlistView(AGGREGATION_BITS_TYPE, aggregation_bitfield),
        data,
        new UInt64View(inclusion_delay),
        new UInt64View(proposer_index));
  }

  public PendingAttestation() {
    super(TYPE);
  }

  public PendingAttestation(PendingAttestation pendingAttestation) {
    super(TYPE, pendingAttestation.getBackingNode());
  }

  @Override
  public PendingAttestation copy() {
    return new PendingAttestation(this);
  }

  public Bitlist getAggregation_bits() {
    return ViewUtils.getBitlist(getField0());
  }

  public AttestationData getData() {
    return getField1();
  }

  public UInt64 getInclusion_delay() {
    return getField2().get();
  }

  public UInt64 getProposer_index() {
    return getField3().get();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return "PendingAttestation{"
        + "aggregation_bits="
        + getAggregation_bits()
        + ", data="
        + getData()
        + ", inclusion_delay="
        + getInclusion_delay()
        + ", proposer_index="
        + getProposer_index()
        + '}';
  }
}
