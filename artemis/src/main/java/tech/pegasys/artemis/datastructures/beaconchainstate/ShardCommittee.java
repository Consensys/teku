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

import java.util.ArrayList;
import tech.pegasys.artemis.util.uint.UInt64;

public class ShardCommittee {

  private UInt64 shard;
  private ArrayList<Integer> committee;
  private UInt64 total_validator_count;

  public ShardCommittee(UInt64 shard, ArrayList<Integer> committee, UInt64 total_validator_count) {
    this.shard = shard;
    this.committee = committee;
    this.total_validator_count = total_validator_count;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UInt64 getShard() {
    return shard;
  }

  public ArrayList<Integer> getCommittee() {
    return committee;
  }

  public void setCommittee(ArrayList<Integer> committee) {
    this.committee = committee;
  }

  public UInt64 getTotal_validator_count() {
    return total_validator_count;
  }

  public void setTotal_validator_count(UInt64 total_validator_count) {
    this.total_validator_count = total_validator_count;
  }
}
