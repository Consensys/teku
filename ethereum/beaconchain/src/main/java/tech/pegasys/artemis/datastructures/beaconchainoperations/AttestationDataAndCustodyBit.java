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

public class AttestationDataAndCustodyBit {

  private AttestationData data;
  private boolean poc_bit;

  public AttestationDataAndCustodyBit() {}

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public boolean isPoc_bit() {
    return poc_bit;
  }

  public void setPoc_bit(boolean poc_bit) {
    this.poc_bit = poc_bit;
  }

  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }
}
