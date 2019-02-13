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

package tech.pegasys.artemis.datastructures.operations;

public class AttestationDataAndCustodyBit {

  private AttestationData data;
  private boolean custody_bit;

  public AttestationDataAndCustodyBit() {}

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public boolean getCustody_bit() {
    return custody_bit;
  }

  public void setCustody_bit(boolean custody_bit) {
    this.custody_bit = custody_bit;
  }

  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }
}
