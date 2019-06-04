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

package tech.pegasys.artemis.data;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.pegasys.artemis.util.json.BytesModule;

public class TimeSeriesRecord implements IRecordAdapter {

  private static final Logger logger = LoggerFactory.getLogger(TimeSeriesRecord.class);

  private static class ValidatorJoinSerializer extends JsonSerializer<ValidatorJoin> {

    @Override
    public void serialize(
        ValidatorJoin validatorJoin, JsonGenerator jGen, SerializerProvider serializerProvider)
        throws IOException {
      jGen.writeStartObject();
      jGen.writeStringField("pubkey", validatorJoin.getValidator().getPubkey().toString());
      jGen.writeStringField("balance", "" + validatorJoin.getBalance());
      jGen.writeEndObject();
    }
  }

  private static class ValidatorJoinModule extends SimpleModule {

    public ValidatorJoinModule() {
      super("validatorJoin");
      addSerializer(ValidatorJoin.class, new ValidatorJoinSerializer());
    }
  }

  private static final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new BytesModule())
          .registerModule(new ValidatorJoinModule());

  private Date date;
  private long index;
  private long slot;
  private long epoch;

  private String block_root;
  private String block_parent_root;
  private String block_body;

  private String lastJustifiedBlockRoot;
  private String lastJustifiedStateRoot;
  private String lastFinalizedBlockRoot;
  private String lastFinalizedStateRoot;

  private List<ValidatorJoin> validators;
  private Map<String, Object> outputFieldMap = new HashMap<>();

  public TimeSeriesRecord() {
    // new Hello(1, 1, Bytes32.random(), UInt64.valueOf(0), Bytes32.random(), UInt64.valueOf(0))
    this.date = new Date();
    this.index = Long.MAX_VALUE;
    this.slot = Long.MAX_VALUE;
    this.epoch = Long.MAX_VALUE;

    this.block_root = Bytes32.random().toHexString();
    this.block_parent_root = Bytes32.random().toHexString();
    this.validators = new ArrayList<>();

    this.lastJustifiedBlockRoot = Bytes32.random().toHexString();
    this.lastJustifiedStateRoot = Bytes32.random().toHexString();
    this.lastFinalizedBlockRoot = Bytes32.random().toHexString();
    this.lastFinalizedStateRoot = Bytes32.random().toHexString();

    logger.info("TEST_EPOCH {}", this.toJSON());
  }

  public TimeSeriesRecord(
      Date date,
      long index,
      long slot,
      long epoch,
      String block_root,
      String block_parent_root,
      String block_body,
      String lastJustifiedBlockRoot,
      String lastJustifiedStateRoot,
      String lastFinalizedBlockRoot,
      String lastFinalizedStateRoot,
      List<ValidatorJoin> validators) {
    this.date = date;
    this.index = index;
    this.slot = slot;
    this.epoch = epoch;
    this.block_root = block_root;
    this.block_parent_root = block_parent_root;
    this.block_body = block_body;
    this.lastJustifiedBlockRoot = lastJustifiedBlockRoot;
    this.lastJustifiedStateRoot = lastJustifiedStateRoot;
    this.lastFinalizedBlockRoot = lastFinalizedBlockRoot;
    this.lastFinalizedStateRoot = lastFinalizedStateRoot;
    this.validators = validators;
  }

  @Override
  public void filterOutputFields(List<String> outputFields) {
    for (String field : outputFields) {
      switch (field) {
        case "date":
          this.outputFieldMap.put("date", this.date.toInstant().toEpochMilli());
          break;

        case "index":
          this.outputFieldMap.put("index", getIndex());
          break;

        case "slot":
          this.outputFieldMap.put("slot", getSlot());
          break;

        case "epoch":
          this.outputFieldMap.put("epoch", getEpoch());
          break;

        case "block_root":
          this.outputFieldMap.put("block_root", getBlock_root());
          break;

        case "block_body":
          this.outputFieldMap.put("block_body", getBlock_body());
          break;

        case "lastFinalizedBlockRoot":
          this.outputFieldMap.put("lastFinalizedBlockRoot", getLastFinalizedBlockRoot());
          break;

        case "lastFinalizedStateRoot":
          this.outputFieldMap.put("lastFinalizedStateRoot", getLastFinalizedStateRoot());
          break;

        case "block_parent_root":
          this.outputFieldMap.put("block_parent_root", getBlock_parent_root());
          break;

        case "validators":
          this.outputFieldMap.put("validators", getValidators());
          break;

        case "validators_size":
          this.outputFieldMap.put("validators_size", getValidators().size());
          break;

        case "lastJustifiedBlockRoot":
          this.outputFieldMap.put("lastJustifiedBlockRoot", getLastJustifiedBlockRoot());
          break;

        case "lastJustifiedStateRoot":
          this.outputFieldMap.put("lastJustifiedStateRoot", getLastJustifiedStateRoot());
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

  public long getDate() {
    return date.toInstant().toEpochMilli();
  }

  public void setDate(long date) {
    this.date = new Date(date);
  }

  public long getIndex() {
    return index;
  }

  public void setIndex(long index) {
    this.index = index;
  }

  public long getSlot() {
    return slot;
  }

  public void setSlot(long slot) {
    this.slot = slot;
  }

  public long getEpoch() {
    return epoch;
  }

  public void setEpoch(long epoch) {
    this.epoch = epoch;
  }

  public String getBlock_root() {
    return block_root;
  }

  public void setBlock_root(String block_root) {
    this.block_root = block_root;
  }

  public String getBlock_parent_root() {
    return block_parent_root;
  }

  public void setBlock_parent_root(String block_parent_root) {
    this.block_parent_root = block_parent_root;
  }

  public String getBlock_body() {
    return block_body;
  }

  public void setBlock_body(String block_body) {
    this.block_body = block_body;
  }

  public String getLastJustifiedBlockRoot() {
    return lastJustifiedBlockRoot;
  }

  public void setLastJustifiedBlockRoot(String lastJustifiedBlockRoot) {
    this.lastJustifiedBlockRoot = lastJustifiedBlockRoot;
  }

  public String getLastJustifiedStateRoot() {
    return lastJustifiedStateRoot;
  }

  public void setLastJustifiedStateRoot(String lastJustifiedStateRoot) {
    this.lastJustifiedStateRoot = lastJustifiedStateRoot;
  }

  public String getLastFinalizedBlockRoot() {
    return lastFinalizedBlockRoot;
  }

  public void setLastFinalizedBlockRoot(String lastFinalizedBlockRoot) {
    this.lastFinalizedBlockRoot = lastFinalizedBlockRoot;
  }

  public String getLastFinalizedStateRoot() {
    return lastFinalizedStateRoot;
  }

  public void setLastFinalizedStateRoot(String lastFinalizedStateRoot) {
    this.lastFinalizedStateRoot = lastFinalizedStateRoot;
  }

  public List<ValidatorJoin> getValidators() {
    return validators;
  }

  public void setValidators(List<ValidatorJoin> validators) {
    this.validators = validators;
  }
}
