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

package tech.pegasys.artemis.datastructures.beaconchainoperations;

import java.util.ArrayList;
import net.consensys.cava.bytes.Bytes48;

public class SlashableVoteData {

  private ArrayList<Integer> custody_bit_0_indices;
  private ArrayList<Integer> custody_bit_1_indices;
  private AttestationData data;
  private Bytes48[] aggregate_signature;

  public SlashableVoteData(
      ArrayList<Integer> custody_bit_0_indices,
      ArrayList<Integer> custody_bit_1_indices,
      AttestationData data,
      Bytes48[] aggregate_signature) {
    this.custody_bit_0_indices = custody_bit_0_indices;
    this.custody_bit_1_indices = custody_bit_1_indices;
    this.data = data;
    this.aggregate_signature = aggregate_signature;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public ArrayList<Integer> getCustody_bit_0_indices() {
    return custody_bit_0_indices;
  }

  public void setCustody_bit_0_indices(ArrayList<Integer> custody_bit_0_indices) {
    this.custody_bit_0_indices = custody_bit_0_indices;
  }

  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }

  public Bytes48[] getAggregate_signature() {
    return aggregate_signature;
  }

  public void setAggregate_signature(Bytes48[] aggregate_signature) {
    this.aggregate_signature = aggregate_signature;
  }

  public ArrayList<Integer> getCustody_bit_1_indices() {
    return custody_bit_1_indices;
  }

  public void setCustody_bit_1_indices(ArrayList<Integer> custody_bit_1_indices) {
    this.custody_bit_1_indices = custody_bit_1_indices;
  }
}
