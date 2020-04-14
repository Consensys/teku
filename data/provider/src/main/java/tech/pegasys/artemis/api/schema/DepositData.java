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

package tech.pegasys.artemis.api.schema;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.bls.BLSPublicKey;

public class DepositData {
  public final BLSPubKey pubkey;
  public final Bytes32 withdrawal_credentials;
  public final UnsignedLong amount;
  public final BLSSignature signature;

  public DepositData(tech.pegasys.artemis.datastructures.operations.DepositData depositData) {
    this.pubkey = new BLSPubKey(depositData.getPubkey().toBytes());
    this.withdrawal_credentials = depositData.getWithdrawal_credentials();
    this.amount = depositData.getAmount();
    this.signature = new BLSSignature(depositData.getSignature());
  }

  public tech.pegasys.artemis.datastructures.operations.DepositData asInternalDepositData() {
    return new tech.pegasys.artemis.datastructures.operations.DepositData(
        BLSPublicKey.fromBytes(pubkey.toBytes()),
        withdrawal_credentials,
        amount,
        signature.asInternalBLSSignature());
  }
}
