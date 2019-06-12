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

package tech.pegasys.artemis.datastructures.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.interfaces.IRecordAdapter;
import tech.pegasys.artemis.pow.api.DepositEvent;
import tech.pegasys.artemis.pow.contract.DepositContract.DepositEventResponse;
import tech.pegasys.artemis.pow.event.AbstractEvent;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class Deposit extends AbstractEvent<DepositEventResponse>
    implements DepositEvent, IRecordAdapter {
  // processed fields
  private BLSPublicKey pubkey;
  private Bytes32 withdrawal_credentials;
  private Bytes proof_of_possession;
  private long amount;

  private static final ObjectMapper mapper = new ObjectMapper();

  // raw fields
  private Bytes data;
  private Bytes merkel_tree_index;
  private Map<String, Object> outputFieldMap = new HashMap<>();

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

  public Bytes getMerkle_tree_index() {
    return merkel_tree_index;
  }

  @Override
  public String toString() {
    return data.toString() + "\n" + merkel_tree_index.toString();
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

        case "proof_of_possession":
          this.outputFieldMap.put("proof_of_possession", proof_of_possession.toHexString());
          break;

        case "amount":
          this.outputFieldMap.put("amount", amount);
          break;

        case "merkel_tree_index":
          this.outputFieldMap.put("merkel_tree_index", merkel_tree_index.toHexString());
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
