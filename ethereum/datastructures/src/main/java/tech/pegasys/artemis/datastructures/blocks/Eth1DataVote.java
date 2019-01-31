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

package tech.pegasys.artemis.datastructures.blocks;

import com.google.common.primitives.UnsignedLong;

public final class Eth1DataVote {

  private Eth1Data eth1_data;
  private UnsignedLong vote_count;

  public Eth1DataVote(){
    
  }

  /**
   * @return the eth1_data
   */
  public Eth1Data getEth1_data() {
    return eth1_data;
  }

  /**
   * @param eth1_data the eth1_data to set
   */
  public void setEth1_data(Eth1Data eth1_data) {
    this.eth1_data = eth1_data;
  }

  /**
   * @return the vote_count
   */
  public UnsignedLong getVote_count() {
    return vote_count;
  }

  /**
   * @param vote_count the vote_count to set
   */
  public void setVote_count(UnsignedLong vote_count) {
    this.vote_count = vote_count;
  }

}