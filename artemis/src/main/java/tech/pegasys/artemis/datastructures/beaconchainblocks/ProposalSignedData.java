/*
 * Copyright 2018 ConsenSys AG.
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

import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt64;

public class ProposalSignedData {

  private UInt64 slot;
  private UInt64 shard;
  private Hash block_hash;

  public ProposalSignedData(UInt64 slot, UInt64 shard, Hash block_hash) {
    this.slot = slot;
    this.shard = shard;
    this.block_hash = block_hash;
  }

  public UInt64 getSlot() {
    return slot;
  }

  public void setSlot(UInt64 slot) {
    this.slot = slot;
  }

  public UInt64 getShard() {
    return shard;
  }

  public void setShard(UInt64 shard) {
    this.shard = shard;
  }

  public Hash getBlock_hash() {
    return block_hash;
  }

  public void setBlock_hash(Hash block_hash) {
    this.block_hash = block_hash;
  }
}
