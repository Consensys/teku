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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import java.nio.ByteOrder;
import tech.pegasys.artemis.pow.api.DepositEvent;
import tech.pegasys.artemis.pow.contract.DepositContract.DepositEventResponse;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class Deposit extends AbstractEvent<DepositEventResponse> implements DepositEvent {
  // processed fields
  private BLSPublicKey pubkey;
  private Bytes32 withdrawal_credentials;
  private Bytes proof_of_possession;
  private long amount;

  // raw fields
  private Bytes data;
  private Bytes merkel_tree_index;

  public Deposit(DepositEventResponse response) {
    super(response);
    // raw fields
    this.data = Bytes.wrap(response.data);
    this.merkel_tree_index = Bytes.wrap(response.merkle_tree_index);

    // process fields
    this.pubkey = BLSPublicKey.fromBytesCompressed(data.slice(0, 48).reverse());
    this.withdrawal_credentials = Bytes32.wrap(data.slice(48, 32).reverse());
    this.proof_of_possession = data.slice(88, 96).reverse();
    this.amount = data.slice(80, 8).toLong(ByteOrder.LITTLE_ENDIAN);
  }

  public Bytes getData() {
    return data;
  }

  public Bytes getMerkel_tree_index() {
    return merkel_tree_index;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(data.toString()).append("\n").append(merkel_tree_index.toString());
    return sb.toString();
  }

  public BLSPublicKey getPubkey() {
    return pubkey;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials;
  }

  public Bytes getProof_of_possession() {
    return proof_of_possession;
  }

  public long getAmount() {
    return amount;
  }
}
