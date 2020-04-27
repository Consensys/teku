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

package tech.pegasys.teku.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public class Validator {
  public final BLSPubKey pubkey;
  public final Bytes32 withdrawal_credentials;
  public final UnsignedLong effective_balance;
  public final boolean slashed;
  public final UnsignedLong activation_eligibility_epoch;
  public final UnsignedLong activation_epoch;
  public final UnsignedLong exit_epoch;
  public final UnsignedLong withdrawable_epoch;

  @JsonCreator
  public Validator(
      @JsonProperty("pubkey") final BLSPubKey pubkey,
      @JsonProperty("withdrawal_credentials") final Bytes32 withdrawal_credentials,
      @JsonProperty("effective_balance") final UnsignedLong effective_balance,
      @JsonProperty("slashed") final boolean slashed,
      @JsonProperty("activation_eligibility_epoch") final UnsignedLong activation_eligibility_epoch,
      @JsonProperty("activation_epoch") final UnsignedLong activation_epoch,
      @JsonProperty("exit_epoch") final UnsignedLong exit_epoch,
      @JsonProperty("withdrawable_epoch") final UnsignedLong withdrawable_epoch) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.effective_balance = effective_balance;
    this.slashed = slashed;
    this.activation_eligibility_epoch = activation_eligibility_epoch;
    this.activation_epoch = activation_epoch;
    this.exit_epoch = exit_epoch;
    this.withdrawable_epoch = withdrawable_epoch;
  }

  public Validator(final tech.pegasys.teku.datastructures.state.Validator validator) {
    this.pubkey = new BLSPubKey(validator.getPubkey().toBytes());
    this.withdrawal_credentials = validator.getWithdrawal_credentials();
    this.effective_balance = validator.getEffective_balance();
    this.slashed = validator.isSlashed();
    this.activation_eligibility_epoch = validator.getActivation_eligibility_epoch();
    this.activation_epoch = validator.getActivation_epoch();
    this.exit_epoch = validator.getExit_epoch();
    this.withdrawable_epoch = validator.getWithdrawable_epoch();
  }
}
