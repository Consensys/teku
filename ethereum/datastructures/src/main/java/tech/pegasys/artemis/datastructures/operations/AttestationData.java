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
import tech.pegasys.artemis.datastructures.state.Crosslink;

public class AttestationData {

  private UnsignedLong slot;
  private UnsignedLong shard;
  private Bytes32 beacon_block_root;
  private Bytes32 epoch_boundary_root;
  private Bytes32 crosslink_data_root;
  private Crosslink latest_crosslink;
  private UnsignedLong justified_epoch;
  private Bytes32 justified_block_root;

  public AttestationData(
      UnsignedLong slot,
      UnsignedLong shard,
      Bytes32 beacon_block_root,
      Bytes32 epoch_boundary_root,
      Bytes32 crosslink_data_root,
      Crosslink latest_crosslink,
      UnsignedLong justified_epoch,
      Bytes32 justified_block_root) {
    this.slot = slot;
    this.shard = shard;
    this.beacon_block_root = beacon_block_root;
    this.epoch_boundary_root = epoch_boundary_root;
    this.crosslink_data_root = crosslink_data_root;
    this.latest_crosslink = latest_crosslink;
    this.justified_epoch = justified_epoch;
    this.justified_block_root = justified_block_root;
  }

  public AttestationData(AttestationData attestationData) {
    this.slot = attestationData.getSlot();
    this.shard = attestationData.getShard();
    this.beacon_block_root = attestationData.getBeacon_block_root();
    this.epoch_boundary_root = attestationData.getEpoch_boundary_root();
    this.crosslink_data_root = attestationData.getCrosslink_data_root();
    this.latest_crosslink = new Crosslink(attestationData.getLatest_crosslink());
    this.justified_epoch = attestationData.getJustified_epoch();
    this.justified_block_root = attestationData.getJustified_block_root();
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
                Crosslink.fromBytes(reader.readBytes()),
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
          writer.writeBytes(crosslink_data_root);
          writer.writeBytes(latest_crosslink.toBytes());
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
        crosslink_data_root,
        latest_crosslink,
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
        && Objects.equals(this.getCrosslink_data_root(), other.getCrosslink_data_root())
        && Objects.equals(this.getLatest_crosslink(), other.getLatest_crosslink())
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

  public Bytes32 getCrosslink_data_root() {
    return crosslink_data_root;
  }

  public void setCrosslink_data_root(Bytes32 crosslink_data_root) {
    this.crosslink_data_root = crosslink_data_root;
  }

  public UnsignedLong getShard() {
    return shard;
  }

  public void setShard(UnsignedLong shard) {
    this.shard = shard;
  }

  public Crosslink getLatest_crosslink() {
    return latest_crosslink;
  }

  public void setLatest_crosslink(Crosslink latest_crosslink) {
    this.latest_crosslink = latest_crosslink;
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
