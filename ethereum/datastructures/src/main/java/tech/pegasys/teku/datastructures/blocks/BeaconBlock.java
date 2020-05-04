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

package tech.pegasys.teku.datastructures.blocks;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.hashtree.HashTreeUtil;
import tech.pegasys.teku.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.util.hashtree.Merkleizable;

public final class BeaconBlock implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 4;

  // Header
  private UnsignedLong slot;
  private UnsignedLong proposer_index;
  private Bytes32 parent_root;
  private Bytes32 state_root;

  // Body
  private BeaconBlockBody body;

  public BeaconBlock(
      UnsignedLong slot,
      UnsignedLong proposer_index,
      Bytes32 parent_root,
      Bytes32 state_root,
      BeaconBlockBody body) {
    this.slot = slot;
    this.proposer_index = proposer_index;
    this.parent_root = parent_root;
    this.state_root = state_root;
    this.body = body;
  }

  public BeaconBlock() {
    this.slot = UnsignedLong.ZERO;
    this.proposer_index = UnsignedLong.ZERO;
    this.parent_root = Bytes32.ZERO;
    this.state_root = Bytes32.ZERO;
    this.body = new BeaconBlockBody();
  }

  public BeaconBlock(Bytes32 state_root) {
    this.slot = UnsignedLong.ZERO;
    this.proposer_index = UnsignedLong.ZERO;
    this.parent_root = Bytes32.ZERO;
    this.state_root = state_root;
    this.body = new BeaconBlockBody();
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + body.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(slot.longValue()),
        SSZ.encodeUInt64(proposer_index.longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(parent_root)),
        SSZ.encode(writer -> writer.writeFixedBytes(state_root)),
        Bytes.EMPTY);
  }

  @Override
  public List<Bytes> get_variable_parts() {
    return List.of(
        Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, SimpleOffsetSerializer.serialize(body));
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, proposer_index, parent_root, state_root, body);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconBlock)) {
      return false;
    }

    BeaconBlock other = (BeaconBlock) obj;
    return Objects.equals(this.getSlot(), other.getSlot())
        && Objects.equals(this.getProposer_index(), other.getProposer_index())
        && Objects.equals(this.getParent_root(), other.getParent_root())
        && Objects.equals(this.getState_root(), other.getState_root())
        && Objects.equals(this.getBody(), other.getBody());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public BeaconBlockBody getBody() {
    return body;
  }

  public Bytes32 getState_root() {
    return state_root;
  }

  public void setState_root(Bytes32 state_root) {
    this.state_root = state_root;
  }

  public Bytes32 getParent_root() {
    return parent_root;
  }

  public UnsignedLong getSlot() {
    return slot;
  }

  public UnsignedLong getProposer_index() {
    return proposer_index;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(proposer_index.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, parent_root),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, state_root),
            body.hash_tree_root()));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("root", hash_tree_root())
        .add("slot", slot)
        .add("proposer_index", proposer_index)
        .add("parent_root", parent_root)
        .add("state_root", state_root)
        .add("body", body.hash_tree_root())
        .toString();
  }
}
