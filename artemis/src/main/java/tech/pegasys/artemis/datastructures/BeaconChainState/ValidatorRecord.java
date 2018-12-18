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

package tech.pegasys.artemis.datastructures.BeaconChainState;

import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt384;
import tech.pegasys.artemis.util.uint.UInt64;

public class ValidatorRecord {

  private UInt384 pubkey;
  private Hash withdrawal_credentials;
  private Hash randao_commitment;
  private UInt64 randao_layers;
  public UInt64 balance;
  public UInt64 status;
  private UInt64 latest_status_change_slot;
  private UInt64 exit_count;

  public ValidatorRecord(){}

  public ValidatorRecord(int pubkey, Hash withdrawal_credentials, Hash randao_commitment,
                         UInt64 randao_layers, UInt64 deposit, UInt64 status, UInt64 slot, UInt64 exit_count) {
    this.pubkey = new UInt384(pubkey);
    this.withdrawal_credentials = withdrawal_credentials;
    this.randao_commitment = randao_commitment;
    this.randao_layers = randao_layers;
    this.balance = deposit;
    this.status = status;
    this.latest_status_change_slot = slot;
    this.exit_count = exit_count;
  }

  public UInt64 getBalance() {
    return this.balance;
  }

  public void setBalance(UInt64 balance) {
    this.balance = balance;
  }

  public UInt64 getStatus() {
    return this.status;
  }

  public void setStatus(UInt64 status) {
    this.status = status;
  }

}
