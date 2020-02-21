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

package tech.pegasys.artemis.datastructures.operations;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ContainerViewImpl;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class AttestationData extends ContainerViewImpl<AttestationData>
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

  public AttestationData(
      ContainerViewType<? extends ContainerViewWrite<ViewRead>> type, TreeNode backingNode) {
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
  @JsonIgnore
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
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AttestationData that = (AttestationData) o;
    return Objects.equals(getSlot(), that.getSlot())
        && Objects.equals(getIndex(), that.getIndex())
        && Objects.equals(getBeacon_block_root(), that.getBeacon_block_root())
        && Objects.equals(getSource(), that.getSource())
        && Objects.equals(getTarget(), that.getTarget());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getSlot(), getIndex(), getBeacon_block_root(), getSource(), getTarget());
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

  public AttestationData withIndex(final UnsignedLong index) {
    return new AttestationData(getSlot(), index, getBeacon_block_root(), getSource(), getTarget());
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return "AttestationData{"
        + "slot="
        + getSlot()
        + ", index="
        + getIndex()
        + ", beacon_block_root="
        + getBeacon_block_root()
        + ", source="
        + getSource()
        + ", target="
        + getTarget()
        + '}';
  }
}
