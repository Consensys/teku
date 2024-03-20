/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.api.schema.electra;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceiptSchema;

public class DepositReceipt {

  @JsonProperty("pubkey")
  private final BLSPubKey pubkey;

  @JsonProperty("withdrawal_credentials")
  private final Bytes32 withdrawalCredentials;

  @JsonProperty("amount")
  private final UInt64 amount;

  @JsonProperty("signature")
  private final BLSSignature signature;

  @JsonProperty("index")
  private final UInt64 index;

  public DepositReceipt(
      @JsonProperty("pubkey") final BLSPubKey pubkey,
      @JsonProperty("withdrawal_credentials") final Bytes32 withdrawalCredentials,
      @JsonProperty("amount") final UInt64 amount,
      @JsonProperty("signature") final BLSSignature signature,
      @JsonProperty("index") final UInt64 index) {
    this.pubkey = pubkey;
    this.withdrawalCredentials = withdrawalCredentials;
    this.amount = amount;
    this.signature = signature;
    this.index = index;
  }

  public DepositReceipt(
      final tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt
          depositReceipt) {
    this.pubkey = new BLSPubKey(depositReceipt.getPubkey());
    this.withdrawalCredentials = depositReceipt.getWithdrawalCredentials();
    this.amount = depositReceipt.getAmount();
    this.signature = new BLSSignature(depositReceipt.getSignature());
    this.index = depositReceipt.getIndex();
  }

  public tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt
      asInternalDepositReceipt(final DepositReceiptSchema schema) {
    return schema.create(
        pubkey.asBLSPublicKey(),
        withdrawalCredentials,
        amount,
        signature.asInternalBLSSignature(),
        index);
  }
}
