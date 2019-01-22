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

public class ShardReassignmentRecord {

  private int validator_index;
  private UnsignedLong shard;
  private UnsignedLong slot;

  public ShardReassignmentRecord(int validator_index, UnsignedLong shard, UnsignedLong slot) {
    this.validator_index = validator_index;
    this.shard = shard;
    this.slot = slot;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public int getValidator_index() {
    return validator_index;
  }

  public void setValidator_index(int validator_index) {
    this.validator_index = validator_index;
  }

  public UnsignedLong getShard() {
    return shard;
  }

  public void setShard(UnsignedLong shard) {
    this.shard = shard;
  }

  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }
}
