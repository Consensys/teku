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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSPubKeyDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSPubKeySerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSSignatureDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSSignatureSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositDataV1 {
  @JsonSerialize(using = BLSPubKeySerializer.class)
  @JsonDeserialize(using = BLSPubKeyDeserializer.class)
  public final BLSPubKey pubkey;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 withdrawalCredentials;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 amount;

  @JsonSerialize(using = BLSSignatureSerializer.class)
  @JsonDeserialize(using = BLSSignatureDeserializer.class)
  public final BLSSignature signature;

  public DepositDataV1(tech.pegasys.teku.spec.datastructures.operations.DepositData depositData) {
    this.pubkey = new BLSPubKey(depositData.getPubkey().toSSZBytes());
    this.withdrawalCredentials = depositData.getWithdrawalCredentials();
    this.amount = depositData.getAmount();
    this.signature = new BLSSignature(depositData.getSignature());
  }

  @JsonCreator
  public DepositDataV1(
      @JsonProperty("pubkey") final BLSPubKey pubkey,
      @JsonProperty("withdrawalCredentials") final Bytes32 withdrawalCredentials,
      @JsonProperty("amount") final UInt64 amount,
      @JsonProperty("signature") final BLSSignature signature) {
    this.pubkey = pubkey;
    this.withdrawalCredentials = withdrawalCredentials;
    this.amount = amount;
    this.signature = signature;
  }

  public tech.pegasys.teku.spec.datastructures.operations.DepositData asInternalDepositData() {
    return new tech.pegasys.teku.spec.datastructures.operations.DepositData(
        BLSPublicKey.fromSSZBytes(pubkey.toBytes()),
        withdrawalCredentials,
        amount,
        signature.asInternalBLSSignature());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DepositDataV1)) {
      return false;
    }
    DepositDataV1 that = (DepositDataV1) o;
    return Objects.equals(pubkey, that.pubkey)
        && Objects.equals(withdrawalCredentials, that.withdrawalCredentials)
        && Objects.equals(amount, that.amount)
        && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubkey, withdrawalCredentials, amount, signature);
  }
}
