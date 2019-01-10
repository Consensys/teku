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

package tech.pegasys.artemis.datastructures.beaconchainoperations;

import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt384;

public class DepositInput {

    private UInt384 pubkey;
    private Hash withdrawal_credentials;
    private Hash randao_commitment;
    private UInt384[] proof_of_possession;

    public DepositInput(
            UInt384 pubkey,
            Hash withdrawal_credentials,
            Hash randao_commitment,
            UInt384[] proof_of_possession) {
        this.pubkey = pubkey;
        this.withdrawal_credentials = withdrawal_credentials;
        this.randao_commitment = randao_commitment;
        this.proof_of_possession = proof_of_possession;
    }

    public UInt384 getPubkey() {
        return pubkey;
    }

    public void setPubkey(UInt384 pubkey) {
        this.pubkey = pubkey;
    }

    public Hash getWithdrawal_credentials() {
        return withdrawal_credentials;
    }

    public void setWithdrawal_credentials(Hash withdrawal_credentials) {
        this.withdrawal_credentials = withdrawal_credentials;
    }

    public Hash getRandao_commitment() {
        return randao_commitment;
    }

    public void setRandao_commitment(Hash randao_commitment) {
        this.randao_commitment = randao_commitment;
    }

    public UInt384[] getProof_of_possession() {
        return proof_of_possession;
    }

    public void setProof_of_possession(UInt384[] proof_of_possession) {
        this.proof_of_possession = proof_of_possession;
    }
}
