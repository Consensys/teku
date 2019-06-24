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

package tech.pegasys.artemis.validator.client;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import tech.pegasys.artemis.datastructures.event.Deposit;
import tech.pegasys.artemis.datastructures.event.Eth2Genesis;
import tech.pegasys.artemis.datastructures.interfaces.IRecordAdapter;
import tech.pegasys.artemis.datastructures.operations.DepositData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DepositSimulation implements IRecordAdapter {

  private static class DepositSerializer extends JsonSerializer<Deposit> {

    @Override
    public void serialize(
        Deposit deposit, JsonGenerator jGen, SerializerProvider serializerProvider)
        throws IOException {
      jGen.writeStartObject();
      jGen.writeStringField("eventType", "Deposit");
      jGen.writeStringField("merkle_tree_index", deposit.getMerkle_tree_index().toHexString());
      jGen.writeEndObject();
    }
  }

  private static class DepositModule extends SimpleModule {

    public DepositModule() {
      super("deposit");
      addSerializer(Deposit.class, new DepositSerializer());
    }
  }

  private Validator validator;
  private DepositData data;
  private List<Deposit> deposits;
  private List<Eth2Genesis> eth2Geneses;
  private Map<String, Object> outputFieldMap = new HashMap<>();
  private static final ObjectMapper mapper =
      new ObjectMapper().registerModule(new DepositModule());;

  public DepositSimulation(Validator validator, DepositData data) {
    this.validator = validator;
    this.data = data;
    deposits = new ArrayList<Deposit>();
    eth2Geneses = new ArrayList<Eth2Genesis>();
  }

  public List<Deposit> getDeposits() {
    return deposits;
  }

  public List<Eth2Genesis> getEth2Geneses() {
    return eth2Geneses;
  }

  public Validator getValidator() {
    return validator;
  }

  @Override
  public void filterOutputFields(List<String> outputFields) {

    for (String field : outputFields) {
      switch (field) {
        case "secp":
          this.outputFieldMap.put(
              "secp", validator.getSecpKeys().secretKey().bytes().toHexString());
          break;

        case "bls":
          this.outputFieldMap.put(
              "bls", validator.getBlsKeys().secretKey().toBytes().toHexString());
          break;

        case "events":
          this.outputFieldMap.put("events", deposits);
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
