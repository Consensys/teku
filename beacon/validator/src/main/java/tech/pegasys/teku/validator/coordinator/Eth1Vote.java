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

package tech.pegasys.teku.validator.coordinator;

public class Eth1Vote implements Comparable<Eth1Vote> {

  private int vote = 0;
  private final int index;

  public Eth1Vote(int index) {
    this.index = index;
  }

  public void incrementVotes() {
    vote++;
  }

  public int getVoteCount() {
    return vote;
  }

  // Greater vote number, or in case of a tie,
  // smallest index number wins
  @Override
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
