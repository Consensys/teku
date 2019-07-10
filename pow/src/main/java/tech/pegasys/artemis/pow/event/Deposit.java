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
import com.google.common.primitives.UnsignedLong;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.interfaces.IRecordAdapter;
import tech.pegasys.artemis.pow.api.DepositEvent;
import tech.pegasys.artemis.pow.contract.DepositContract.DepositEventResponse;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Deposit extends AbstractEvent<DepositEventResponse>
    implements DepositEvent, IRecordAdapter {
  // processed fields
  private BLSPublicKey pubkey;
  private Bytes32 withdrawal_credentials;
  private BLSSignature signature;
  private UnsignedLong amount;
  private UnsignedLong merkle_tree_index;

  private static final ObjectMapper mapper = new ObjectMapper();

  private Map<String, Object> outputFieldMap = new HashMap<>();

  public Deposit(DepositEventResponse response) {
    super(response);

    ArrayUtils.reverse(response.merkle_tree_index);
    this.merkle_tree_index = UnsignedLong.valueOf(Bytes.wrap(response.merkle_tree_index).toLong());

    ArrayUtils.reverse(response.pubkey);
    this.pubkey = BLSPublicKey.fromBytesCompressed(Bytes.wrap(response.pubkey));

    ArrayUtils.reverse(response.withdrawal_credentials);
    this.withdrawal_credentials = Bytes32.wrap(response.withdrawal_credentials);

    ArrayUtils.reverse(response.signature);
    this.signature = BLSSignature.fromBytes(Bytes.wrap(response.signature));

    ArrayUtils.reverse(response.amount);
    this.amount = UnsignedLong.valueOf(Bytes.wrap(response.amount).toLong());
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

  public void setPubkey(BLSPublicKey pubkey) {
    this.pubkey = pubkey;
  }

  public void setWithdrawal_credentials(Bytes32 withdrawal_credentials) {
    this.withdrawal_credentials = withdrawal_credentials;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  public void setAmount(UnsignedLong amount) {
    this.amount = amount;
  }

  public void setMerkle_tree_index(UnsignedLong merkle_tree_index) {
    this.merkle_tree_index = merkle_tree_index;
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
    String jsonOutputString = null;
    jsonOutputString = mapper.writerFor(Map.class).writeValueAsString(this.outputFieldMap);
    return jsonOutputString;
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
}
