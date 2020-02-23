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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
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

public class Eth1Data implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  private static final int SSZ_FIELD_COUNT = 3;

  private Bytes32 deposit_root;
  private UnsignedLong deposit_count;
  private Bytes32 block_hash;

  public Eth1Data(Bytes32 deposit_root, UnsignedLong deposit_count, Bytes32 block_hash) {
    this.deposit_root = deposit_root;
    this.deposit_count = deposit_count;
    this.block_hash = block_hash;
  }

  public Eth1Data() {
    this.deposit_root = Bytes32.ZERO;
    this.deposit_count = UnsignedLong.ZERO;
    this.block_hash = Bytes32.ZERO;
  }

  public Eth1Data(Eth1Data eth1Data) {
    this.deposit_root = eth1Data.getDeposit_root();
    this.deposit_count = eth1Data.getDeposit_count();
    this.block_hash = eth1Data.getBlock_hash();
  }

  @Override
  @JsonIgnore
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(deposit_root)),
        SSZ.encodeUInt64(deposit_count.longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(block_hash)));
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
          writer.writeFixedBytes(deposit_root);
          writer.writeUInt64(deposit_count.longValue());
          writer.writeFixedBytes(block_hash);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(deposit_root, deposit_count, block_hash);
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
  public Bytes32 getDeposit_root() {
    return deposit_root;
  }

  /** @param deposit_root the deposit_root to set */
  public void setDeposit_root(Bytes32 deposit_root) {
    this.deposit_root = deposit_root;
  }

  public UnsignedLong getDeposit_count() {
    return deposit_count;
  }

  public void setDeposit_count(UnsignedLong deposit_count) {
    this.deposit_count = deposit_count;
  }

  /** @return the block_hash */
  public Bytes32 getBlock_hash() {
    return block_hash;
  }

  /** @param block_hash the block_hash to set */
  public void setBlock_hash(Bytes32 block_hash) {
    this.block_hash = block_hash;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, deposit_root),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(deposit_count.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, block_hash)));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("deposit_root", deposit_root)
        .add("deposit_count", deposit_count)
        .add("block_hash", block_hash)
        .toString();
  }
}
