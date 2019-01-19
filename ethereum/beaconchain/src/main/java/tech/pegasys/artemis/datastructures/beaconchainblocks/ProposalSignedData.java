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

package tech.pegasys.artemis.datastructures.beaconchainblocks;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;

public class ProposalSignedData {

  private UnsignedLong slot;
  private UnsignedLong shard;
  private Bytes32 block_hash;

  public ProposalSignedData(UnsignedLong slot, UnsignedLong shard, Bytes32 block_hash) {
    this.slot = slot;
    this.shard = shard;
    this.block_hash = block_hash;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public UnsignedLong getShard() {
    return shard;
  }

  public void setShard(UnsignedLong shard) {
    this.shard = shard;
  }

  public Bytes32 getBlock_hash() {
    return block_hash;
  }

  public void setBlock_hash(Bytes32 block_hash) {
    this.block_hash = block_hash;
  }
}
