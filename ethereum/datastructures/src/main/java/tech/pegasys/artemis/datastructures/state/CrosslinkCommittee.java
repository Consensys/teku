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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;

public class CrosslinkCommittee {

  private UnsignedLong shard;
  private List<Integer> committee;

  public CrosslinkCommittee(UnsignedLong shard, List<Integer> committee) {
    this.shard = shard;
    this.committee = committee;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getShard() {
    return shard;
  }

  public List<Integer> getCommittee() {
    return committee;
  }

  public void setCommittee(ArrayList<Integer> committee) {
    this.committee = committee;
  }

  public int getCommitteeSize() {
    return committee.size();
  }
}
