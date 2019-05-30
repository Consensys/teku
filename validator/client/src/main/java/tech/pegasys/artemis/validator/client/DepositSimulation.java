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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import tech.pegasys.artemis.data.IRecordAdapter;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.Eth2Genesis;

public class DepositSimulation implements IRecordAdapter {

  private Validator validator;
  private Bytes deposit_data;
  private List<Deposit> deposits;
  private List<Eth2Genesis> eth2Geneses;
  private Map<String, Object> outputFieldMap = new HashMap<>();

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

  public JsonArray depositEvents() {
	  JsonArray arr = new JsonArray();
	  IntStream.range(0, deposits.size())
      .forEach(
          i -> {
            JsonObject deposit = new JsonObject();
            deposit.addProperty("eventType", "Deposit");
            deposit.addProperty("data", deposits.get(i).getData().toHexString());
            deposit.addProperty(
                "merkle_tree_index", deposits.get(i).getMerkle_tree_index().toHexString());
            arr.add(deposit);
          });
	  return arr;	  
  } 
  
  @Override
  public void filterOutputFields(List<String> outputFields) {
  
	  for(String field : outputFields) {		  
		  switch(field) {		  
		  	case "secp":
		  		this.outputFieldMap.put("secp", validator.getSecpKeys().secretKey().bytes().toHexString());
		  		break;
		  		
		  	case "bls" :
		  		this.outputFieldMap.put("bls", validator.getBlsKeys().secretKey().toBytes().toHexString());
		  		break;
		  		
		  	case "deposit_data" :
		  		this.outputFieldMap.put("deposit_data", deposit_data.toHexString());
		  		break;
		  		
		  	case "events" :
		  		this.outputFieldMap.put("events", depositEvents());
		  		break;
		  		
 		  }
	  }
	  	  
  }
  
  @Override
  public String toJSON() {
	  Gson gson = new GsonBuilder().create();
	  GsonBuilder gsonBuilder = new GsonBuilder();
	  String jsonString = gson.toJson(this.outputFieldMap);
	  JsonObject deposit = gson.fromJson(jsonString, JsonObject.class);    
	  Gson customGson = gsonBuilder.setPrettyPrinting().create();
	  return customGson.toJson(deposit);
	}

  @Override
  public String toCSV() {
	    String csvOutputString = "";    
	    for(Object obj : this.outputFieldMap.values()) {
	        csvOutputString += "'"
	                + obj.toString()
	                + "',";
	    }        
	    return csvOutputString.substring(0, csvOutputString.length() - 1);
  }

  @Override
  public String[] toLabels() {
	  return (String[]) this.outputFieldMap.values().toArray();
  }


}
