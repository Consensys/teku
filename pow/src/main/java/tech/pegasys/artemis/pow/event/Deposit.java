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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.interfaces.IRecordAdapter;
import tech.pegasys.artemis.pow.api.DepositEvent;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class Deposit extends AbstractEvent<DepositContract.DepositEventEventResponse>
    implements DepositEvent, IRecordAdapter {
  // processed fields
  private final BLSPublicKey pubkey;
  private final Bytes32 withdrawal_credentials;
  private final BLSSignature signature;
  private final UnsignedLong amount;
  private final UnsignedLong merkle_tree_index;

  private static final ObjectMapper mapper = new ObjectMapper();

  private Map<String, Object> outputFieldMap = new HashMap<>();

  public Deposit(DepositContract.DepositEventEventResponse response) {
    super(response);
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
  public void filterOutputFields(List<String> outputFields) {
    this.outputFieldMap.put("eventType", "Deposit");
    for (String field : outputFields) {
      switch (field) {
        case "pubkey":
          this.outputFieldMap.put(
              "pubkey", pubkey.getPublicKey().toBytesCompressed().toHexString());
          break;

        case "withdrawal_credentials":
          this.outputFieldMap.put("withdrawal_credentials", withdrawal_credentials.toHexString());
          break;

        case "signature":
          this.outputFieldMap.put(
              "signature", signature.getSignature().toBytesCompressed().toHexString());
          break;

        case "amount":
          this.outputFieldMap.put("amount", amount);
          break;

        case "merkle_tree_index":
          this.outputFieldMap.put("merkle_tree_index", merkle_tree_index.toString());
          break;
      }
    }
  }

  @Override
  public String toJSON() throws JsonProcessingException {
    return mapper.writerFor(Map.class).writeValueAsString(this.outputFieldMap);
  }

  @Override
  public String toCSV() {
    String csvOutputString = "";
    for (Object obj : this.outputFieldMap.values()) {
      csvOutputString += "'" + obj.toString() + "',";
    }
    if (csvOutputString.length() > 0) {
      csvOutputString = csvOutputString.substring(0, csvOutputString.length() - 1);
    }
    return csvOutputString;
  }

  @Override
  public String[] toLabels() {
    return (String[]) this.outputFieldMap.values().toArray();
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
        && Objects.equals(merkle_tree_index, deposit.merkle_tree_index)
        && Objects.equals(outputFieldMap, deposit.outputFieldMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        pubkey, withdrawal_credentials, signature, amount, merkle_tree_index, outputFieldMap);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pubkey", pubkey)
        .add("withdrawal_credentials", withdrawal_credentials)
        .add("signature", signature)
        .add("amount", amount)
        .add("merkle_tree_index", merkle_tree_index)
        .add("outputFieldMap", outputFieldMap)
        .toString();
  }
}
