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

package tech.pegasys.artemis.datastructures.beaconchainstate;

import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt384;
import tech.pegasys.artemis.util.uint.UInt64;

public class ValidatorRecord {

  private UInt384 pubkey;
  private double balance = 0.0d;

  public double getBalance() {
    return balance;
  }

  public void setBalance(double balance) {
    this.balance = balance;
  }

  private Hash withdrawal_credentials;
  private Hash randao_commitment;
  private UInt64 randao_layers;
  private UInt64 status;
  private UInt64 latest_status_change_slot;
  private UInt64 exit_count;
  private UInt64 last_poc_change_slot;
  private UInt64 second_last_poc_change_slot;

  public ValidatorRecord(int pubkey, Hash withdrawal_credentials, Hash randao_commitment, UInt64 randao_layers,
                          UInt64 status, UInt64 slot, UInt64 exit_count, UInt64 last_poc_change_slot,
                          UInt64 second_last_poc_change_slot) {
    this.pubkey = UInt384.valueOf(pubkey);
    this.withdrawal_credentials = withdrawal_credentials;
    this.randao_commitment = randao_commitment;
    this.randao_layers = randao_layers;
    this.status = status;
    this.latest_status_change_slot = slot;
    this.exit_count = exit_count;
    this.last_poc_change_slot = last_poc_change_slot;
    this.second_last_poc_change_slot = second_last_poc_change_slot;
  }

  public boolean is_active_validator() {
    //checks validator status against the validator status constants for whether the validator is active
    return (status.equals(UInt64.valueOf(Constants.ACTIVE)) || status.equals(UInt64.valueOf(Constants.ACTIVE_PENDING_EXIT)));
  }

  /**
   * Returns the effective balance (also known as "balance at stake") for the ``validator``.
   * @param
   * @return
   */
  public double get_effective_balance() {
    return Math.min(balance, Constants.MAX_DEPOSIT * Constants.GWEI_PER_ETH);
  }

  /*********************
   *                   *
   * GETTERS & SETTERS *
   *                   *
   *********************/

  public UInt64 getStatus() {
    return this.status;
  }

  public void setStatus(UInt64 status) {
    this.status = status;
  }

  public UInt384 getPubkey() {
    return pubkey;
  }

  public void setPubkey(UInt384 pubkey) {
    this.pubkey = pubkey;
  }

  public Hash getRandao_commitment() {
    return randao_commitment;
  }

  public void setRandao_commitment(Hash randao_commitment) {
    this.randao_commitment = randao_commitment;
  }

  public Hash getWithdrawal_credentials() {
    return withdrawal_credentials;
  }

  public void setWithdrawal_credentials(Hash withdrawal_credentials) {
    this.withdrawal_credentials = withdrawal_credentials;
  }

  public UInt64 getRandao_layers() {
    return randao_layers;
  }

  public void setRandao_layers(UInt64 randao_layers) {
    this.randao_layers = randao_layers;
  }

  public UInt64 getLatest_status_change_slot() {
    return latest_status_change_slot;
  }

  public void setLatest_status_change_slot(UInt64 latest_status_change_slot) {
    this.latest_status_change_slot = latest_status_change_slot;
  }

  public UInt64 getExit_count() {
    return exit_count;
  }

  public void setExit_count(UInt64 exit_count) {
    this.exit_count = exit_count;
  }

  public UInt64 getLast_poc_change_slot() {
    return last_poc_change_slot;
  }

  public void setLast_poc_change_slot(UInt64 last_poc_change_slot) {
    this.last_poc_change_slot = last_poc_change_slot;
  }

  public UInt64 getSecond_last_poc_change_slot() {
    return second_last_poc_change_slot;
  }

  public void setSecond_last_poc_change_slot(UInt64 second_last_poc_change_slot) {
    this.second_last_poc_change_slot = second_last_poc_change_slot;
  }
}
