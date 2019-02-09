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

  private long slot;
  private UnsignedLong shard;
  private Bytes32 beacon_block_hash;
  private Bytes32 epoch_boundary_hash;
  private Bytes32 shard_block_hash;
  private Bytes32 last_crosslink_hash;
  private UnsignedLong justified_slot;
  private Bytes32 justified_block_hash;

  public AttestationData(
      long slot,
      UnsignedLong shard,
      Bytes32 beacon_block_hash,
      Bytes32 epoch_boundary_hash,
      Bytes32 shard_block_hash,
      Bytes32 last_crosslink_hash,
      UnsignedLong justified_slot,
      Bytes32 justified_block_hash) {
    this.slot = slot;
    this.shard = shard;
    this.beacon_block_hash = beacon_block_hash;
    this.epoch_boundary_hash = epoch_boundary_hash;
    this.shard_block_hash = shard_block_hash;
    this.last_crosslink_hash = last_crosslink_hash;
    this.justified_slot = justified_slot;
    this.justified_block_hash = justified_block_hash;
  }

  public static AttestationData fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new AttestationData(
                reader.readUInt64(),
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
          writer.writeUInt64(slot);
          writer.writeUInt64(shard.longValue());
          writer.writeBytes(beacon_block_hash);
          writer.writeBytes(epoch_boundary_hash);
          writer.writeBytes(shard_block_hash);
          writer.writeBytes(last_crosslink_hash);
          writer.writeUInt64(justified_slot.longValue());
          writer.writeBytes(justified_block_hash);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        slot,
        shard,
        beacon_block_hash,
        epoch_boundary_hash,
        shard_block_hash,
        last_crosslink_hash,
        justified_slot,
        justified_block_hash);
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
        && Objects.equals(this.getBeacon_block_hash(), other.getBeacon_block_hash())
        && Objects.equals(this.getEpoch_boundary_hash(), other.getEpoch_boundary_hash())
        && Objects.equals(this.getShard_block_hash(), other.getShard_block_hash())
        && Objects.equals(this.getLast_crosslink_hash(), other.getLast_crosslink_hash())
        && Objects.equals(this.getJustified_slot(), other.getJustified_slot())
        && Objects.equals(this.getJustified_block_hash(), other.getJustified_block_hash());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public long getSlot() {
    return slot;
  }

  public void setSlot(long slot) {
    this.slot = slot;
  }

  public Bytes32 getBeacon_block_hash() {
    return beacon_block_hash;
  }

  public void setBeacon_block_hash(Bytes32 beacon_block_hash) {
    this.beacon_block_hash = beacon_block_hash;
  }

  public Bytes32 getEpoch_boundary_hash() {
    return epoch_boundary_hash;
  }

  public void setEpoch_boundary_hash(Bytes32 epoch_boundary_hash) {
    this.epoch_boundary_hash = epoch_boundary_hash;
  }

  public Bytes32 getShard_block_hash() {
    return shard_block_hash;
  }

  public void setShard_block_hash(Bytes32 shard_block_hash) {
    this.shard_block_hash = shard_block_hash;
  }

  public UnsignedLong getShard() {
    return shard;
  }

  public void setShard(UnsignedLong shard) {
    this.shard = shard;
  }

  public Bytes32 getLast_crosslink_hash() {
    return last_crosslink_hash;
  }

  public void setLast_crosslink_hash(Bytes32 last_crosslink_hash) {
    this.last_crosslink_hash = last_crosslink_hash;
  }

  public UnsignedLong getJustified_slot() {
    return justified_slot;
  }

  public void setJustified_slot(UnsignedLong justified_slot) {
    this.justified_slot = justified_slot;
  }

  public Bytes32 getJustified_block_hash() {
    return justified_block_hash;
  }

  public void setJustified_block_hash(Bytes32 justified_block_hash) {
    this.justified_block_hash = justified_block_hash;
  }
}
