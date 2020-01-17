/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.validator.coordinator;

public class Eth1Vote implements Comparable<Eth1Vote> {

  private int vote = 0;
  private int index = -1;

  public void incrementVotes() {
    vote++;
  }

  public void setIndex(int i) {
    index = i;
  }

  @Override
  // Greater vote number, or in case of a tie,
  // smallest index number wins
  public int compareTo(Eth1Vote eth1Vote) {
    if (this.vote > eth1Vote.vote) {
      return 1;
    } else if (this.vote < eth1Vote.vote) {
      return -1;
    } else if (this.index < eth1Vote.index) {
      return 1;
    } else if (this.index > eth1Vote.index) {
      return -1;
    } else {
      return 0;
    }
  }
}
}
