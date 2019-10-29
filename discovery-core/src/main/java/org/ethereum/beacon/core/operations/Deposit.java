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

package org.ethereum.beacon.core.operations;

import com.google.common.base.Objects;
import java.util.List;
import org.ethereum.beacon.core.BeaconBlockBody;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.collections.ReadVector;

/**
 * Requests to add validator to the validator registry.
 *
 * @see BeaconBlockBody
 * @see DepositData
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#deposit>Deposit</a>
 *     in the spec.
 */
@SSZSerializable
public class Deposit {

  /** Merkle path to deposit data list root. */
  @SSZ(vectorLengthVar = "spec.DEPOSIT_CONTRACT_TREE_DEPTH_PLUS_ONE")
  private final ReadVector<Integer, Hash32> proof;
  /** Deposit data. */
  @SSZ private final DepositData data;

  public static Deposit create(List<Hash32> proof, DepositData data) {
    return new Deposit(ReadVector.wrap(proof, Integer::new), data);
  }

  public Deposit(ReadVector<Integer, Hash32> proof, DepositData data) {
    this.proof = proof;
    this.data = data;
  }

  public ReadVector<Integer, Hash32> getProof() {
    return proof;
  }

  public DepositData getData() {
    return data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Deposit deposit = (Deposit) o;
    return proof.equals(deposit.proof) && Objects.equal(data, deposit.data);
  }

  @Override
  public int hashCode() {
    int result = proof.hashCode();
    result = 31 * result + data.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "Deposit["
        + "pubkey="
        + data.getPubKey()
        + "withdrawalCredentials="
        + data.getWithdrawalCredentials()
        + "amount="
        + data.getAmount()
        + "]";
  }
}
