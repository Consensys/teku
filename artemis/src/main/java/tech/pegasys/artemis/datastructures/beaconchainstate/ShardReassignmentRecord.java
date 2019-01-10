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

package tech.pegasys.artemis.datastructures.beaconchainstate;

import tech.pegasys.artemis.util.uint.UInt64;

public class ShardReassignmentRecord {

    private int validator_index;
    private UInt64 shard;
    private UInt64 slot;

    public ShardReassignmentRecord(int validator_index, UInt64 shard, UInt64 slot) {
        this.validator_index = validator_index;
        this.shard = shard;
        this.slot = slot;
    }

    public int getValidator_index() {
        return validator_index;
    }

    public void setValidator_index(int validator_index) {
        this.validator_index = validator_index;
    }

    public UInt64 getShard() {
        return shard;
    }

    public void setShard(UInt64 shard) {
        this.shard = shard;
    }

    public UInt64 getSlot() {
        return slot;
    }

    public void setSlot(UInt64 slot) {
        this.slot = slot;
    }
}
