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

package tech.pegasys.artemis.beaconrestapi.schema;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public class Validator {
  public String pubkey;
  public Bytes32 withdrawal_credentials;
  public UnsignedLong effective_balance;
  public boolean slashed;
  public UnsignedLong activation_eligibility_epoch;
  public UnsignedLong activation_epoch;
  public UnsignedLong exit_epoch;
  public UnsignedLong withdrawable_epoch;

  public Validator(tech.pegasys.artemis.datastructures.state.Validator validator) {
    this.pubkey = validator.getPubkey().toString();
    this.withdrawal_credentials = validator.getWithdrawal_credentials();
    this.effective_balance = validator.getEffective_balance();
    this.slashed = validator.isSlashed();
    this.activation_eligibility_epoch = validator.getActivation_eligibility_epoch();
    this.activation_epoch = validator.getActivation_epoch();
    this.exit_epoch = validator.getExit_epoch();
    this.withdrawable_epoch = validator.getWithdrawable_epoch();
  }
}
