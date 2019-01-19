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

package tech.pegasys.artemis.datastructures.beaconchainstate;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import tech.pegasys.artemis.Constants;

public class ValidatorRecord {

  private Bytes48 pubkey;
  private double balance = 0.0d;

  public double getBalance() {
    return balance;
  }

  public void setBalance(double balance) {
    this.balance = balance;
  }

  private Bytes32 withdrawal_credentials;
  private Bytes32 randao_commitment;
  private UnsignedLong randao_layers;
  private UnsignedLong status;
  private UnsignedLong latest_status_change_slot;
  private UnsignedLong exit_count;
  private UnsignedLong last_poc_change_slot;
  private UnsignedLong second_last_poc_change_slot;

  public ValidatorRecord(
      Bytes48 pubkey,
      Bytes32 withdrawal_credentials,
      Bytes32 randao_commitment,
      UnsignedLong randao_layers,
      UnsignedLong status,
      UnsignedLong slot,
      UnsignedLong exit_count,
      UnsignedLong last_poc_change_slot,
      UnsignedLong second_last_poc_change_slot) {
    this.pubkey = pubkey;
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
    // checks validator status against the validator status constants for whether the validator is
    // active
    return (status.equals(UnsignedLong.valueOf(Constants.ACTIVE))
        || status.equals(UnsignedLong.valueOf(Constants.ACTIVE_PENDING_EXIT)));
  }

  /**
   * Returns the effective balance (also known as "balance at stake") for the ``validator``.
   *
   * @param
   * @return
   */
  public double get_effective_balance() {
    return Math.min(balance, Constants.MAX_DEPOSIT * Constants.GWEI_PER_ETH);
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getStatus() {
    return this.status;
  }

  public void setStatus(UnsignedLong status) {
    this.status = status;
  }

  public Bytes48 getPubkey() {
    return pubkey;
  }

  public void setPubkey(Bytes48 pubkey) {
    this.pubkey = pubkey;
  }

  public Bytes32 getRandao_commitment() {
    return randao_commitment;
  }

  public void setRandao_commitment(Bytes32 randao_commitment) {
    this.randao_commitment = randao_commitment;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials;
  }

  public void setWithdrawal_credentials(Bytes32 withdrawal_credentials) {
    this.withdrawal_credentials = withdrawal_credentials;
  }

  public UnsignedLong getRandao_layers() {
    return randao_layers;
  }

  public void setRandao_layers(UnsignedLong randao_layers) {
    this.randao_layers = randao_layers;
  }

  public UnsignedLong getLatest_status_change_slot() {
    return latest_status_change_slot;
  }

  public void setLatest_status_change_slot(UnsignedLong latest_status_change_slot) {
    this.latest_status_change_slot = latest_status_change_slot;
  }

  public UnsignedLong getExit_count() {
    return exit_count;
  }

  public void setExit_count(UnsignedLong exit_count) {
    this.exit_count = exit_count;
  }

  public UnsignedLong getLast_poc_change_slot() {
    return last_poc_change_slot;
  }

  public void setLast_poc_change_slot(UnsignedLong last_poc_change_slot) {
    this.last_poc_change_slot = last_poc_change_slot;
  }

  public UnsignedLong getSecond_last_poc_change_slot() {
    return second_last_poc_change_slot;
  }

  public void setSecond_last_poc_change_slot(UnsignedLong second_last_poc_change_slot) {
    this.second_last_poc_change_slot = second_last_poc_change_slot;
  }
}
