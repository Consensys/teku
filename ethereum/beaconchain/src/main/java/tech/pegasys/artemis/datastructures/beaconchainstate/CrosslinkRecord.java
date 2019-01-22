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

package tech.pegasys.artemis.datastructures.beaconchainstate;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;

public class CrosslinkRecord {

  private Bytes32 shard_block_hash;
  private UnsignedLong slot;

  public CrosslinkRecord(Bytes32 shard_block_hash, UnsignedLong slot) {
    this.shard_block_hash = shard_block_hash;
    this.slot = slot;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes32 getShard_block_hash() {
    return shard_block_hash;
  }

  public void setShard_block_hash(Bytes32 shard_block_hash) {
    this.shard_block_hash = shard_block_hash;
  }

  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }
}
