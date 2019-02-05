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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import tech.pegasys.artemis.datastructures.Constants;

public class Validator {

  // BLS public key
  private Bytes48 pubkey;
  // Withdrawal credentials
  private Bytes32 withdrawal_credentials;
  // Epoch when validator activated
  private UnsignedLong activation_epoch;
  // Epoch when validator exited
  private UnsignedLong exit_epoch;
  // Epoch when validator withdrew
  private UnsignedLong withdrawal_epoch;
  // Epoch when validator was penalized
  private UnsignedLong penalized_epoch;
  // Status flags
  private UnsignedLong status_flags;
  // Validator balace
  private double balance = 0.0d;

  public Validator() {}

  public Bytes48 getPubkey() {
    return pubkey;
  }

  public void setPubkey(Bytes48 pubkey) {
    this.pubkey = pubkey;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials;
  }

  public void setWithdrawal_credentials(Bytes32 withdrawal_credentials) {
    this.withdrawal_credentials = withdrawal_credentials;
  }

  public UnsignedLong getActivation_epoch() {
    return activation_epoch;
  }

  public void setActivation_epoch(UnsignedLong activation_epoch) {
    this.activation_epoch = activation_epoch;
  }

  public UnsignedLong getExit_epoch() {
    return exit_epoch;
  }

  public void setExit_epoch(UnsignedLong exit_epoch) {
    this.exit_epoch = exit_epoch;
  }

  public UnsignedLong getWithdrawal_epoch() {
    return withdrawal_epoch;
  }

  public void setWithdrawal_epoch(UnsignedLong withdrawal_epoch) {
    this.withdrawal_epoch = withdrawal_epoch;
  }

  public UnsignedLong getPenalized_epoch() {
    return penalized_epoch;
  }

  public void setPenalized_epoch(UnsignedLong penalized_epoch) {
    this.penalized_epoch = penalized_epoch;
  }

  public UnsignedLong getStatus_flags() {
    return status_flags;
  }

  public void setStatus_flags(UnsignedLong status_flags) {
    this.status_flags = status_flags;
  }

  public double getBalance() {
    return balance;
  }

  public void setBalance(double balance) {
    this.balance = balance;
  }

  public boolean is_active_validator(long epoch) {
    // checks validator status against the validator status constants for whether the validator is
    // active
    return activation_epoch.compareTo(UnsignedLong.valueOf(epoch)) <= 0
        && activation_epoch.compareTo(exit_epoch) < 0;
  }

  /**
   * Returns the effective balance (also known as "balance at stake") for the ``validator``.
   *
   * @param
   * @return
   */
  public double get_effective_balance() {
    return Math.min(balance, Constants.MAX_DEPOSIT_AMOUNT);
  }
}
