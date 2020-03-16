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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.view.AbstractImmutableContainer;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

@JsonAutoDetect(getterVisibility = Visibility.NONE)
public class BeaconBlockHeader extends AbstractImmutableContainer<BeaconBlockHeader>
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 4;

  public static final ContainerViewType<BeaconBlockHeader> TYPE =
      new ContainerViewType<>(
          List.of(
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.BYTES32_TYPE,
              BasicViewTypes.BYTES32_TYPE,
              BasicViewTypes.BYTES32_TYPE),
          BeaconBlockHeader::new);

  @SuppressWarnings("unused")
  private final UnsignedLong slot = null;

  @SuppressWarnings("unused")
  private final Bytes32 parent_root = null;

  @SuppressWarnings("unused")
  private final Bytes32 state_root = null;

  @SuppressWarnings("unused")
  private final Bytes32 body_root = null;

  private BeaconBlockHeader(ContainerViewType<BeaconBlockHeader> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @JsonCreator
  public BeaconBlockHeader(
      UnsignedLong slot, Bytes32 parent_root, Bytes32 state_root, Bytes32 body_root) {
    super(
        TYPE,
        new UInt64View(slot),
        new Bytes32View(parent_root),
        new Bytes32View(state_root),
        new Bytes32View(body_root));
  }

  public BeaconBlockHeader(BeaconBlockHeader header) {
    super(TYPE, header.getBackingNode());
  }

  public BeaconBlockHeader() {
    super(TYPE);
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
        SSZ.encodeUInt64(getSlot().longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(getParent_root())),
        SSZ.encode(writer -> writer.writeFixedBytes(getState_root())),
        SSZ.encode(writer -> writer.writeFixedBytes(getBody_root())));
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
          writer.writeUInt64(getSlot().longValue());
          writer.writeFixedBytes(getParent_root());
          writer.writeFixedBytes(getState_root());
          writer.writeFixedBytes(getBody_root());
        });
  }

  @Override
  public int hashCode() {
    return hashTreeRoot().slice(0, 4).toInt();
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
    return hashTreeRoot().equals(other.hashTreeRoot());
  }

  /** *************** * GETTERS & SETTERS * * ******************* */
  @JsonProperty
  public UnsignedLong getSlot() {
    return ((UInt64View) get(0)).get();
  }

  @JsonProperty
  public Bytes32 getParent_root() {
    return ((Bytes32View) get(1)).get();
  }

  @JsonProperty
  public Bytes32 getState_root() {
    return ((Bytes32View) get(2)).get();
  }

  @JsonProperty
  public Bytes32 getBody_root() {
    return ((Bytes32View) get(3)).get();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("parent_root", parent_root)
        .add("state_root", state_root)
        .add("body_root", body_root)
        .toString();
  }
}
