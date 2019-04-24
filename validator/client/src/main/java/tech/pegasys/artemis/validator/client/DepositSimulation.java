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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.data.IRecordAdapter;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.Eth2Genesis;

public class DepositSimulation implements IRecordAdapter {

  private Validator validator;
  private Bytes deposit_data;
  private List<Deposit> deposits;
  private List<Eth2Genesis> eth2Geneses;

  public DepositSimulation(Validator validator, Bytes deposit_data) {
    this.validator = validator;
    this.deposit_data = deposit_data;
    deposits = new ArrayList<Deposit>();
    eth2Geneses = new ArrayList<Eth2Genesis>();
  }

  public Bytes getDeposit_data() {
    return deposit_data;
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
  public String toJSON() {
    Gson gson = new GsonBuilder().create();
    GsonBuilder gsonBuilder = new GsonBuilder();

    JsonObject obj = new JsonObject();
    obj.addProperty("secp", validator.getSecpKeys().secretKey().bytes().toHexString());
    obj.addProperty("bls", validator.getBlsKeys().secretKey().toBytes().toHexString());
    obj.addProperty("deposit_data", deposit_data.toHexString());
    JsonArray arr = new JsonArray();

    IntStream.range(0, deposits.size())
        .forEach(
            i -> {
              JsonObject deposit = new JsonObject();
              deposit.addProperty("eventType", "Deposit");
              deposit.addProperty("data", deposits.get(i).getData().toHexString());
              deposit.addProperty(
                  "merkle_tree_index", deposits.get(i).getMerkel_tree_index().toHexString());
              arr.add(deposit);
            });

    obj.add("events", arr);
    Gson customGson = gsonBuilder.setPrettyPrinting().create();
    return customGson.toJson(obj);
  }

  @Override
  public String toCSV() {
    return null;
  }
}
