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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class AttestationData implements SimpleOffsetSerializable, Merkleizable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 3;

  private final UnsignedLong slot;
  private final UnsignedLong index;

  // LMD GHOST vote
  private final Bytes32 beacon_block_root;

  // FFG vote
  private final Checkpoint source;
  private final Checkpoint target;

  public AttestationData(
      UnsignedLong slot,
      UnsignedLong index,
      Bytes32 beacon_block_root,
      Checkpoint source,
      Checkpoint target) {
    this.slot = slot;
    this.index = index;
    this.beacon_block_root = beacon_block_root;
    this.source = source;
    this.target = target;
  }

  public AttestationData(UnsignedLong slot, AttestationData data) {
    this.slot = slot;
    this.index = data.getIndex();
    this.beacon_block_root = data.getBeacon_block_root();
    this.source = data.getSource();
    this.target = data.getTarget();
  }

  @Override
  @JsonIgnore
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + source.getSSZFieldCount() + target.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(
        List.of(
            SSZ.encodeUInt64(slot.longValue()),
            SSZ.encodeUInt64(index.longValue()),
            SSZ.encode(writer -> writer.writeFixedBytes(beacon_block_root))));
    fixedPartsList.addAll(source.get_fixed_parts());
    fixedPartsList.addAll(target.get_fixed_parts());
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
    return Objects.equals(slot, that.slot)
        && Objects.equals(index, that.index)
        && Objects.equals(beacon_block_root, that.beacon_block_root)
        && Objects.equals(source, that.source)
        && Objects.equals(target, that.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, index, beacon_block_root, source, target);
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public UnsignedLong getIndex() {
    return index;
  }

  public Bytes32 getBeacon_block_root() {
    return beacon_block_root;
  }

  public Checkpoint getSource() {
    return source;
  }

  public Checkpoint getTarget() {
    return target;
  }

  public AttestationData withIndex(final UnsignedLong index) {
    return new AttestationData(slot, index, beacon_block_root, source, target);
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(index.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, beacon_block_root),
            source.hash_tree_root(),
            target.hash_tree_root()));
  }
}
