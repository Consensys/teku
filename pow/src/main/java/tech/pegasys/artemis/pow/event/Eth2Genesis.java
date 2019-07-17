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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.interfaces.IRecordAdapter;

public class Eth2Genesis implements IRecordAdapter {

  private UnsignedLong time;
  private Bytes32 deposit_root;
  private UnsignedLong deposit_count;
  private Map<String, Object> outputFieldMap = new HashMap<>();
  private static final ObjectMapper mapper = new ObjectMapper();

  public Eth2Genesis(Bytes32 deposit_root, UnsignedLong deposit_count, UnsignedLong time) {
    this.deposit_root = deposit_root;
    this.deposit_count = deposit_count;
    this.time = time;
  }

  public Bytes32 getDeposit_root() {
    return deposit_root;
  }

  public void setDeposit_root(Bytes32 deposit_root) {
    this.deposit_root = deposit_root;
  }

  public UnsignedLong getDeposit_count() {
    return deposit_count;
  }

  public void setDeposit_count(UnsignedLong deposit_count) {
    this.deposit_count = deposit_count;
  }

  public UnsignedLong getTime() {
    return time;
  }

  public void setTime(UnsignedLong time) {
    this.time = time;
  }

  @Override
  public void filterOutputFields(List<String> outputFields) {
    this.outputFieldMap.put("eventType", "Eth2Genesis");
    for (String field : outputFields) {
      switch (field) {
        case "deposit_root":
          this.outputFieldMap.put("deposit_root", deposit_root.toHexString());
          break;

        case "deposit_count":
          this.outputFieldMap.put("deposit_count", deposit_count);
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
