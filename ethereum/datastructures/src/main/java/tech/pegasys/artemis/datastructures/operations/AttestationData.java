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

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class AttestationData {

  // LMD GHOST vote
  private UnsignedLong slot;
  private Bytes32 beacon_block_root;

  // FFG vote
  private UnsignedLong source_epoch;
  private Bytes32 source_root;
  private Bytes32 target_root;

  // Crosslink vote
  private UnsignedLong shard;
  private Crosslink previous_crosslink;
  private Bytes32 crosslink_data_root;

  public AttestationData(
      UnsignedLong slot,
      Bytes32 beacon_block_root,
      UnsignedLong source_epoch,
      Bytes32 source_root,
      Bytes32 target_root,
      UnsignedLong shard,
      Crosslink previous_crosslink,
      Bytes32 crosslink_data_root) {
    this.slot = slot;
    this.beacon_block_root = beacon_block_root;
    this.source_epoch = source_epoch;
    this.source_root = source_root;
    this.target_root = target_root;
    this.shard = shard;
    this.previous_crosslink = previous_crosslink;
    this.crosslink_data_root = crosslink_data_root;
  }

  public AttestationData(AttestationData attestationData) {
    this.slot = attestationData.getSlot();
    this.beacon_block_root = attestationData.getBeacon_block_root();
    this.source_epoch = attestationData.getSource_epoch();
    this.source_root = attestationData.getSource_root();
    this.target_root = attestationData.getTarget_root();
    this.shard = attestationData.getShard();
    this.previous_crosslink = new Crosslink(attestationData.getPrevious_crosslink());
    this.crosslink_data_root = attestationData.getCrosslink_data_root();
  }

  public static AttestationData fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new AttestationData(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readFixedBytes(32)),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readFixedBytes(32)),
                Bytes32.wrap(reader.readFixedBytes(32)),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Crosslink.fromBytes(reader.readBytes()),
                Bytes32.wrap(reader.readFixedBytes(32))));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot.longValue());
          writer.writeFixedBytes(32, beacon_block_root);
          writer.writeUInt64(source_epoch.longValue());
          writer.writeFixedBytes(32, source_root);
          writer.writeFixedBytes(32, target_root);
          writer.writeUInt64(shard.longValue());
          writer.writeBytes(previous_crosslink.toBytes());
          writer.writeFixedBytes(32, crosslink_data_root);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        slot,
        beacon_block_root,
        source_epoch,
        source_root,
        target_root,
        shard,
        previous_crosslink,
        crosslink_data_root);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof AttestationData)) {
      return false;
    }

    AttestationData other = (AttestationData) obj;
    return Objects.equals(this.getSlot(), other.getSlot())
        && Objects.equals(this.getBeacon_block_root(), other.getBeacon_block_root())
        && Objects.equals(this.getSource_epoch(), other.getSource_epoch())
        && Objects.equals(this.getSource_root(), other.getSource_root())
        && Objects.equals(this.getTarget_root(), other.getTarget_root())
        && Objects.equals(this.getShard(), other.getShard())
        && Objects.equals(this.getPrevious_crosslink(), other.getPrevious_crosslink())
        && Objects.equals(this.getCrosslink_data_root(), other.getCrosslink_data_root());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public Bytes32 getBeacon_block_root() {
    return beacon_block_root;
  }

  public void setBeacon_block_root(Bytes32 beacon_block_root) {
    this.beacon_block_root = beacon_block_root;
  }

  public UnsignedLong getSource_epoch() {
    return source_epoch;
  }

  public void setSource_epoch(UnsignedLong source_epoch) {
    this.source_epoch = source_epoch;
  }

  public Bytes32 getSource_root() {
    return source_root;
  }

  public void setSource_root(Bytes32 source_root) {
    this.source_root = source_root;
  }

  public Bytes32 getTarget_root() {
    return target_root;
  }

  public void setTarget_root(Bytes32 target_root) {
    this.target_root = target_root;
  }

  public UnsignedLong getShard() {
    return shard;
  }

  public void setShard(UnsignedLong shard) {
    this.shard = shard;
  }

  public Crosslink getPrevious_crosslink() {
    return previous_crosslink;
  }

  public void setPrevious_crosslink(Crosslink previous_crosslink) {
    this.previous_crosslink = previous_crosslink;
  }

  public Bytes32 getCrosslink_data_root() {
    return crosslink_data_root;
  }

  public void setCrosslink_data_root(Bytes32 crosslink_data_root) {
    this.crosslink_data_root = crosslink_data_root;
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, beacon_block_root),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(source_epoch.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, source_root),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, target_root),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(shard.longValue())),
            previous_crosslink.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, crosslink_data_root)));
  }
}
