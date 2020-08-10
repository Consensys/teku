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

package tech.pegasys.teku.datastructures.state;

import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class CommitteeAssignment {

  private List<Integer> committee;
  private UInt64 committeeIndex;
  private UInt64 slot;

  public CommitteeAssignment(List<Integer> committee, UInt64 committeeIndex, UInt64 slot) {
    this.committee = committee;
    this.committeeIndex = committeeIndex;
    this.slot = slot;
  }

  public List<Integer> getCommittee() {
    return committee;
  }

  public UInt64 getCommitteeIndex() {
    return committeeIndex;
  }

  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public String toString() {
    return "CommitteeAssignment{"
        + "committee="
        + committee
        + ", committeeIndex="
        + committeeIndex
        + ", slot="
        + slot
        + '}';
  }
}
