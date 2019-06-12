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
import tech.pegasys.artemis.pow.api.Eth2GenesisEvent;
import tech.pegasys.artemis.pow.contract.DepositContract.Eth2GenesisEventResponse;
import tech.pegasys.artemis.pow.event.AbstractEvent;

public class Eth2Genesis extends AbstractEvent<Eth2GenesisEventResponse>
    implements Eth2GenesisEvent, IRecordAdapter {

  private Bytes32 deposit_root;
  private long deposit_count;
  private long time;
  private Map<String, Object> outputFieldMap = new HashMap<>();
  private static final ObjectMapper mapper = new ObjectMapper();

  public Eth2Genesis(Eth2GenesisEventResponse response) {
    super(response);
    this.deposit_root = Bytes32.leftPad(Bytes.wrap(response.deposit_root));
    this.deposit_count = Bytes.wrap(response.deposit_count).toLong(ByteOrder.LITTLE_ENDIAN);
    this.time = Bytes.wrap(response.time).toLong(ByteOrder.LITTLE_ENDIAN);
  }

  public Bytes32 getDeposit_root() {
    return deposit_root;
  }

  public long getDeposit_count() {
    return deposit_count;
  }

  public long getTime() {
    return time;
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

        case "time":
          this.outputFieldMap.put("time", time);
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
