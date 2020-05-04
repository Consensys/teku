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

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.teku.util.config.Constants.MIN_ATTESTATION_INCLUSION_DELAY;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.hashtree.Merkleizable;

public class AttestationData extends AbstractImmutableContainer
    implements SimpleOffsetSerializable, Merkleizable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 3;

  public static final ContainerViewType<AttestationData> TYPE =
      new ContainerViewType<>(
          List.of(
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.BYTES32_TYPE,
              Checkpoint.TYPE,
              Checkpoint.TYPE),
          AttestationData::new);

  @SuppressWarnings("unused")
  private final UnsignedLong slot = null;

  @SuppressWarnings("unused")
  private final UnsignedLong index = null;

  // LMD GHOST vote
  @SuppressWarnings("unused")
  private final Bytes32 beacon_block_root = null;

  // FFG vote
  @SuppressWarnings("unused")
  private final Checkpoint source = null;

  @SuppressWarnings("unused")
  private final Checkpoint target = null;

  private AttestationData(ContainerViewType<AttestationData> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public AttestationData(
      UnsignedLong slot,
      UnsignedLong index,
      Bytes32 beacon_block_root,
      Checkpoint source,
      Checkpoint target) {
    super(
        TYPE,
        new UInt64View(slot),
        new UInt64View(index),
        new Bytes32View(beacon_block_root),
        source,
        target);
  }

  public AttestationData(UnsignedLong slot, AttestationData data) {
    this(slot, data.getIndex(), data.getBeacon_block_root(), data.getSource(), data.getTarget());
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + getSource().getSSZFieldCount() + getTarget().getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(
        List.of(
            SSZ.encodeUInt64(getSlot().longValue()),
            SSZ.encodeUInt64(getIndex().longValue()),
            SSZ.encode(writer -> writer.writeFixedBytes(getBeacon_block_root()))));
    fixedPartsList.addAll(getSource().get_fixed_parts());
    fixedPartsList.addAll(getTarget().get_fixed_parts());
    return fixedPartsList;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", getSlot())
        .add("index", getIndex())
        .add("beacon_block_root", getBeacon_block_root())
        .add("source", getSource())
        .add("target", getTarget())
        .toString();
  }

  public UnsignedLong getEarliestSlotForForkChoice() {
    // Attestations can't be processed by fork choice until their slot is in the past and until we
    // are in the same epoch as their target.
    return max(getSlot().plus(UnsignedLong.ONE), getTarget().getEpochStartSlot());
  }

  public boolean canIncludeInBlockAtSlot(final UnsignedLong blockSlot) {
    return isBlockWithinOneEpochOfAttestation(blockSlot)
        && isBlockAtLeastMinInclusionDelayAfterAttestation(blockSlot);
  }

  private boolean isBlockWithinOneEpochOfAttestation(final UnsignedLong blockSlot) {
    return blockSlot.compareTo(getSlot().plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH))) <= 0;
  }

  private boolean isBlockAtLeastMinInclusionDelayAfterAttestation(final UnsignedLong blockSlot) {
    return getSlot()
            .plus(UnsignedLong.valueOf(MIN_ATTESTATION_INCLUSION_DELAY))
            .compareTo(blockSlot)
        <= 0;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return ((UInt64View) get(0)).get();
  }

  public UnsignedLong getIndex() {
    return ((UInt64View) get(1)).get();
  }

  public Bytes32 getBeacon_block_root() {
    return ((Bytes32View) get(2)).get();
  }

  public Checkpoint getSource() {
    return ((Checkpoint) get(3));
  }

  public Checkpoint getTarget() {
    return ((Checkpoint) get(4));
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
