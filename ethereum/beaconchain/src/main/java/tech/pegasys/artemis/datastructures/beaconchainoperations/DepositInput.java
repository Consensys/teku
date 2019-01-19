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

import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;

public class DepositInput {

  private Bytes48 pubkey;
  private Bytes32 withdrawal_credentials;
  private Bytes32 randao_commitment;
  private Bytes32 poc_commitment;
  private Bytes48[] proof_of_possession;

  public DepositInput(
      Bytes48 pubkey,
      Bytes32 withdrawal_credentials,
      Bytes32 randao_commitment,
      Bytes32 poc_commitment,
      Bytes48[] proof_of_possession) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.randao_commitment = randao_commitment;
    this.poc_commitment = poc_commitment;
    this.proof_of_possession = proof_of_possession;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
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

  public Bytes32 getRandao_commitment() {
    return randao_commitment;
  }

  public void setRandao_commitment(Bytes32 randao_commitment) {
    this.randao_commitment = randao_commitment;
  }

  public Bytes48[] getProof_of_possession() {
    return proof_of_possession;
  }

  public void setProof_of_possession(Bytes48[] proof_of_possession) {
    this.proof_of_possession = proof_of_possession;
  }

  public Bytes32 getPoc_commitment() {
    return poc_commitment;
  }

  public void setPoc_commitment(Bytes32 poc_commitment) {
    this.poc_commitment = poc_commitment;
  }
}
