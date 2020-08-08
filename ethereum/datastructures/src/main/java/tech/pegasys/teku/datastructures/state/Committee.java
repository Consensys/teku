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

public class Committee {

  private final UInt64 index;
  private final List<Integer> committee;

  public Committee(UInt64 index, List<Integer> committee) {
    this.index = index;
    this.committee = committee;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UInt64 getIndex() {
    return index;
  }

  public List<Integer> getCommittee() {
    return committee;
  }

  public int getCommitteeSize() {
    return committee.size();
  }
}
