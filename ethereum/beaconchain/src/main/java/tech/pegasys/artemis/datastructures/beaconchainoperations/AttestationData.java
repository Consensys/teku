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

package tech.pegasys.artemis.datastructures.beaconchainoperations;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.ethereum.core.Hash;

public class AttestationData {

  private long slot;
  private UnsignedLong shard;
  private Hash beacon_block_hash;
  private Hash epoch_boundary_hash;
  private Hash shard_block_hash;
  private Hash last_crosslink_hash;
  private UnsignedLong justified_slot;
  private Hash justified_block_hash;

  public AttestationData(
      long slot,
      UnsignedLong shard,
      Hash beacon_block_hash,
      Hash epoch_boundary_hash,
      Hash shard_block_hash,
      Hash last_crosslink_hash,
      UnsignedLong justified_slot,
      Hash justified_block_hash) {
    this.slot = slot;
    this.shard = shard;
    this.beacon_block_hash = beacon_block_hash;
    this.epoch_boundary_hash = epoch_boundary_hash;
    this.shard_block_hash = shard_block_hash;
    this.last_crosslink_hash = last_crosslink_hash;
    this.justified_slot = justified_slot;
    this.justified_block_hash = justified_block_hash;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public long getSlot() {
    return slot;
  }

  public void setSlot(long slot) {
    this.slot = slot;
  }

  public Hash getBeacon_block_hash() {
    return beacon_block_hash;
  }

  public void setBeacon_block_hash(Hash beacon_block_hash) {
    this.beacon_block_hash = beacon_block_hash;
  }

  public Hash getEpoch_boundary_hash() {
    return epoch_boundary_hash;
  }

  public void setEpoch_boundary_hash(Hash epoch_boundary_hash) {
    this.epoch_boundary_hash = epoch_boundary_hash;
  }

  public Hash getShard_block_hash() {
    return shard_block_hash;
  }

  public void setShard_block_hash(Hash shard_block_hash) {
    this.shard_block_hash = shard_block_hash;
  }

  public UnsignedLong getShard() {
    return shard;
  }

  public void setShard(UnsignedLong shard) {
    this.shard = shard;
  }

  public Hash getLast_crosslink_hash() {
    return last_crosslink_hash;
  }

  public void setLast_crosslink_hash(Hash last_crosslink_hash) {
    this.last_crosslink_hash = last_crosslink_hash;
  }

  public UnsignedLong getJustified_slot() {
    return justified_slot;
  }

  public void setJustified_slot(UnsignedLong justified_slot) {
    this.justified_slot = justified_slot;
  }

  public Hash getJustified_block_hash() {
    return justified_block_hash;
  }

  public void setJustified_block_hash(Hash justified_block_hash) {
    this.justified_block_hash = justified_block_hash;
  }
}
