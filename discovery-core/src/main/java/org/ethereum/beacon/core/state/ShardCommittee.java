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

package org.ethereum.beacon.core.state;

import java.util.List;
import org.ethereum.beacon.core.types.ShardNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;

/** Validator committee assigned to a certain shard. */
public class ShardCommittee {

  /** Validator indices. */
  private final List<ValidatorIndex> committee;
  /** Shard number. */
  private final ShardNumber shard;

  public ShardCommittee(List<ValidatorIndex> committee, ShardNumber shard) {
    this.committee = committee;
    this.shard = shard;
  }

  public List<ValidatorIndex> getCommittee() {
    return committee;
  }

  public ShardNumber getShard() {
    return shard;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ShardCommittee committee1 = (ShardCommittee) o;

    if (!committee.equals(committee1.committee)) {
      return false;
    }
    return shard.equals(committee1.shard);
  }

  @Override
  public int hashCode() {
    int result = committee.hashCode();
    result = 31 * result + shard.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "ShardCommittee[" + shard + ": " + committee + "]";
  }
}
