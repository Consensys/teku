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

package tech.pegasys.artemis.datastructures.blocks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class BeaconBlockHeader implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 4;

  private final UnsignedLong slot;
  private final Bytes32 parent_root;
  private Bytes32 state_root;
  private Bytes32 body_root;

  @JsonCreator
  public BeaconBlockHeader(
      UnsignedLong slot, Bytes32 parent_root, Bytes32 state_root, Bytes32 body_root) {
    this.slot = slot;
    this.parent_root = parent_root;
    this.state_root = state_root;
    this.body_root = body_root;
  }

  public BeaconBlockHeader(BeaconBlockHeader header) {
    this.slot = header.getSlot();
    this.parent_root = header.getParent_root();
    this.state_root = header.getState_root();
    this.body_root = header.getBody_root();
  }

  public BeaconBlockHeader() {
    this.slot = UnsignedLong.ZERO;
    this.parent_root = Bytes32.ZERO;
    this.state_root = Bytes32.ZERO;
    this.body_root = Bytes32.ZERO;
  }

  @Override
  @JsonIgnore
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  @JsonIgnore
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(slot.longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(parent_root)),
        SSZ.encode(writer -> writer.writeFixedBytes(state_root)),
        SSZ.encode(writer -> writer.writeFixedBytes(body_root)));
  }

  public static BeaconBlockHeader fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BeaconBlockHeader(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readFixedBytes(32)),
                Bytes32.wrap(reader.readFixedBytes(32)),
                Bytes32.wrap(reader.readFixedBytes(32))));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot.longValue());
          writer.writeFixedBytes(parent_root);
          writer.writeFixedBytes(state_root);
          writer.writeFixedBytes(body_root);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, parent_root, state_root, body_root);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconBlockHeader)) {
      return false;
    }

    BeaconBlockHeader other = (BeaconBlockHeader) obj;
    return Objects.equals(this.getSlot(), other.getSlot())
        && Objects.equals(this.getParent_root(), other.getParent_root())
        && Objects.equals(this.getState_root(), other.getState_root())
        && Objects.equals(this.getBody_root(), other.getBody_root());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public Bytes32 getParent_root() {
    return parent_root;
  }

  public Bytes32 getState_root() {
    return state_root;
  }

  public void setState_root(Bytes32 state_root) {
    this.state_root = state_root;
  }

  public Bytes32 getBody_root() {
    return body_root;
  }

  public void setBody_root(Bytes32 body_root) {
    this.body_root = body_root;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, parent_root),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, state_root),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, body_root)));
  }
}
