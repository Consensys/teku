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
import java.util.ArrayList;

public class ShardCommittee {

  private UnsignedLong shard;
  private ArrayList<Integer> committee;
  private UnsignedLong total_validator_count;

  public ShardCommittee(
      UnsignedLong shard, ArrayList<Integer> committee, UnsignedLong total_validator_count) {
    this.shard = shard;
    this.committee = committee;
    this.total_validator_count = total_validator_count;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getShard() {
    return shard;
  }

  public ArrayList<Integer> getCommittee() {
    return committee;
  }

  public void setCommittee(ArrayList<Integer> committee) {
    this.committee = committee;
  }

  public UnsignedLong getTotal_validator_count() {
    return total_validator_count;
  }

  public void setTotal_validator_count(UnsignedLong total_validator_count) {
    this.total_validator_count = total_validator_count;
  }
}
