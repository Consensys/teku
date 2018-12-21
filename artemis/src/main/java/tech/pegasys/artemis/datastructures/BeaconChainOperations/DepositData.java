/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.BeaconChainOperations;

import tech.pegasys.artemis.util.uint.UInt64;

public class DepositData {

  private DepositInput deposit_input;
  private UInt64 value;
  private UInt64 timestamp;

  public DepositData(DepositInput deposit_input, UInt64 value, UInt64 timestamp) {
    this.deposit_input = deposit_input;
    this.value = value;
    this.timestamp = timestamp;
  }

  public DepositInput getDeposit_input() {
    return deposit_input;
  }

  public void setDeposit_input(DepositInput deposit_input) {
    this.deposit_input = deposit_input;
  }

  public UInt64 getValue() {
    return value;
  }

  public void setValue(UInt64 value) {
    this.value = value;
  }

  public UInt64 getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(UInt64 timestamp) {
    this.timestamp = timestamp;
  }
}
