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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES48;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;

public class DepositData {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48)
  public final BLSPubKey pubkey;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 withdrawal_credentials;

  @Schema(type = "string", format = "uint64")
  public final UnsignedLong amount;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public final BLSSignature signature;

  public DepositData(tech.pegasys.teku.datastructures.operations.DepositData depositData) {
    this.pubkey = new BLSPubKey(depositData.getPubkey().toBytes());
    this.withdrawal_credentials = depositData.getWithdrawal_credentials();
    this.amount = depositData.getAmount();
    this.signature = new BLSSignature(depositData.getSignature());
  }

  public DepositData(
      @JsonProperty("pubkey") final BLSPubKey pubkey,
      @JsonProperty("withdrawal_credentials") final Bytes32 withdrawal_credentials,
      @JsonProperty("amount") final UnsignedLong amount,
      @JsonProperty("signature") final BLSSignature signature) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.amount = amount;
    this.signature = signature;
  }

  public tech.pegasys.teku.datastructures.operations.DepositData asInternalDepositData() {
    return new tech.pegasys.teku.datastructures.operations.DepositData(
        BLSPublicKey.fromBytes(pubkey.toBytes()),
        withdrawal_credentials,
        amount,
        signature.asInternalBLSSignature());
  }
}
