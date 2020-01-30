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

package tech.pegasys.artemis.pow.event;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class Deposit {
  private final BLSPublicKey pubkey;
  private final Bytes32 withdrawal_credentials;
  private final BLSSignature signature;
  private final UnsignedLong amount;
  private final UnsignedLong merkle_tree_index;

  public Deposit(DepositContract.DepositEventEventResponse response) {
    this.merkle_tree_index = UnsignedLong.valueOf(Bytes.wrap(response.index).reverse().toLong());
    this.pubkey = BLSPublicKey.fromBytesCompressed(Bytes.wrap(response.pubkey));
    this.withdrawal_credentials = Bytes32.wrap(response.withdrawal_credentials);
    this.signature = BLSSignature.fromBytes(Bytes.wrap(response.signature));
    this.amount = UnsignedLong.valueOf(Bytes.wrap(response.amount).reverse().toLong());
  }

  public UnsignedLong getMerkle_tree_index() {
    return merkle_tree_index;
  }

  public BLSPublicKey getPubkey() {
    return pubkey;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials;
  }

  public UnsignedLong getAmount() {
    return amount;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Deposit deposit = (Deposit) o;
    return Objects.equals(pubkey, deposit.pubkey)
        && Objects.equals(withdrawal_credentials, deposit.withdrawal_credentials)
        && Objects.equals(signature, deposit.signature)
        && Objects.equals(amount, deposit.amount)
        && Objects.equals(merkle_tree_index, deposit.merkle_tree_index);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubkey, withdrawal_credentials, signature, amount, merkle_tree_index);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pubkey", pubkey)
        .add("withdrawal_credentials", withdrawal_credentials)
        .add("signature", signature)
        .add("amount", amount)
        .add("merkle_tree_index", merkle_tree_index)
        .toString();
  }
}
