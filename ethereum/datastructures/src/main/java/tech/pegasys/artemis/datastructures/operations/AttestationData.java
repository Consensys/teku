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
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;

public class AttestationData {
//    # Slot number
//    'slot': 'uint64',
//        # Shard number
//    'shard': 'uint64',
//        # Hash of root of the signed beacon block
//    'beacon_block_root': 'bytes32',
//        # Hash of root of the ancestor at the epoch boundary
//    'epoch_boundary_root': 'bytes32',
//        # Shard block's hash of root
//      'shard_block_root': 'bytes32',
//      # Last crosslink
//    'latest_crosslink': Crosslink,
//      # Last justified epoch in the beacon state
//    'justified_epoch': 'uint64',
//        # Hash of the last justified beacon block
//    'justified_block_root': 'bytes32',

  private UnsignedLong slot;
  private UnsignedLong shard;
  private Bytes32 beacon_block_root;
  private Bytes32 epoch_boundary_root;
  private Bytes32 shard_block_root;
  private Bytes32 latest_crosslink_root;
  private UnsignedLong justified_epoch;
  private Bytes32 justified_block_root;

  public AttestationData(
      UnsignedLong slot,
      UnsignedLong shard,
      Bytes32 beacon_block_root,
      Bytes32 epoch_boundary_root,
      Bytes32 shard_block_root,
      Bytes32 latest_crosslink_root,
      UnsignedLong justified_epoch,
      Bytes32 justified_block_root) {
    this.slot = slot;
    this.shard = shard;
    this.beacon_block_root = beacon_block_root;
    this.epoch_boundary_root = epoch_boundary_root;
    this.shard_block_root = shard_block_root;
    this.latest_crosslink_root = latest_crosslink_root;
    this.justified_epoch = justified_epoch;
    this.justified_block_root = justified_block_root;
  }

  public static AttestationData fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new AttestationData(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot.longValue());
          writer.writeUInt64(shard.longValue());
          writer.writeBytes(beacon_block_root);
          writer.writeBytes(epoch_boundary_root);
          writer.writeBytes(shard_block_root);
          writer.writeBytes(latest_crosslink_root);
          writer.writeUInt64(justified_epoch.longValue());
          writer.writeBytes(justified_block_root);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        slot,
        shard,
        beacon_block_root,
        epoch_boundary_root,
        shard_block_root,
        latest_crosslink_root,
        justified_epoch,
        justified_block_root);
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
        && Objects.equals(this.getShard(), other.getShard())
        && Objects.equals(this.getBeacon_block_root(), other.getBeacon_block_root())
        && Objects.equals(this.getEpoch_boundary_root(), other.getEpoch_boundary_root())
        && Objects.equals(this.getShard_block_root(), other.getShard_block_root())
        && Objects.equals(this.getLatest_crosslink_root(), other.getLatest_crosslink_root())
        && Objects.equals(this.getJustified_epoch(), other.getJustified_epoch())
        && Objects.equals(this.getJustified_block_root(), other.getJustified_block_root());
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

  public Bytes32 getEpoch_boundary_root() {
    return epoch_boundary_root;
  }

  public void setEpoch_boundary_root(Bytes32 epoch_boundary_root) {
    this.epoch_boundary_root = epoch_boundary_root;
  }

  public Bytes32 getShard_block_root() {
    return shard_block_root;
  }

  public void setShard_block_root(Bytes32 shard_block_root) {
    this.shard_block_root = shard_block_root;
  }

  public UnsignedLong getShard() {
    return shard;
  }

  public void setShard(UnsignedLong shard) {
    this.shard = shard;
  }

  public Bytes32 getLatest_crosslink_root() {
    return latest_crosslink_root;
  }

  public void setLatest_crosslink_root(Bytes32 latest_crosslink_root) {
    this.latest_crosslink_root = latest_crosslink_root;
  }

  public UnsignedLong getJustified_epoch() {
    return justified_epoch;
  }

  public void setJustified_epoch(UnsignedLong justified_epoch) {
    this.justified_epoch = justified_epoch;
  }

  public Bytes32 getJustified_block_root() {
    return justified_block_root;
  }

  public void setJustified_block_root(Bytes32 justified_block_root) {
    this.justified_block_root = justified_block_root;
  }
}
