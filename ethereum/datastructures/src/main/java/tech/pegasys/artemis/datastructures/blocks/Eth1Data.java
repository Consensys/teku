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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
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

@JsonAutoDetect(getterVisibility = Visibility.NONE)
public class Eth1Data extends ContainerViewImpl<Eth1Data>
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  private static final int SSZ_FIELD_COUNT = 3;

  public static final ContainerViewType<Eth1Data> TYPE =
      new ContainerViewType<>(
          List.of(
              BasicViewTypes.BYTES32_TYPE, BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE),
          Eth1Data::new);

  @SuppressWarnings("unused")
  private final Bytes32 deposit_root = null;

  @SuppressWarnings("unused")
  private final UnsignedLong deposit_count = null;

  @SuppressWarnings("unused")
  private final Bytes32 block_hash = null;

  private Eth1Data(
      ContainerViewType<? extends ContainerViewWrite<ViewRead>> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Eth1Data(Bytes32 deposit_root, UnsignedLong deposit_count, Bytes32 block_hash) {
    super(
        TYPE,
        new Bytes32View(deposit_root),
        new UInt64View(deposit_count),
        new Bytes32View(block_hash));
  }

  public Eth1Data() {
    super(TYPE);
  }

  public Eth1Data(Eth1Data eth1Data) {
    super(TYPE, eth1Data.getBackingNode());
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(getDeposit_root())),
        SSZ.encodeUInt64(getDeposit_count().longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(getBlock_hash())));
  }

  public static Eth1Data fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Eth1Data(
                Bytes32.wrap(reader.readFixedBytes(32)),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readFixedBytes(32))));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeFixedBytes(getDeposit_root());
          writer.writeUInt64(getDeposit_count().longValue());
          writer.writeFixedBytes(getBlock_hash());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(getDeposit_root(), getDeposit_count(), getBlock_hash());
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Eth1Data)) {
      return false;
    }

    Eth1Data other = (Eth1Data) obj;
    return Objects.equals(this.getDeposit_root(), other.getDeposit_root())
        && Objects.equals(this.getDeposit_count(), other.getDeposit_count())
        && Objects.equals(this.getBlock_hash(), other.getBlock_hash());
  }

  /** @return the deposit_root */
  @JsonProperty
  public Bytes32 getDeposit_root() {
    return ((Bytes32View) get(0)).get();
  }

  @JsonProperty
  public UnsignedLong getDeposit_count() {
    return ((UInt64View) get(1)).get();
  }

  /** @return the block_hash */
  @JsonProperty
  public Bytes32 getBlock_hash() {
    return ((Bytes32View) get(2)).get();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("deposit_root", getDeposit_root())
        .add("deposit_count", getDeposit_count())
        .add("block_hash", getBlock_hash())
        .toString();
  }
}
