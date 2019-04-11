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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class TimeSeriesRecord implements IRecordAdapter {

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

  public TimeSeriesRecord() {
    // new Hello(1, 1, Bytes32.random(), UInt64.valueOf(0), Bytes32.random(), UInt64.valueOf(0))
    this.index = Long.MAX_VALUE;
    this.slot = Long.MAX_VALUE;
    this.epoch = Long.MAX_VALUE;

    this.block_root = Bytes32.random().toHexString();
    this.block_parent_root = Bytes32.random().toHexString();

    this.lastJustifiedBlockRoot = Bytes32.random().toHexString();
    this.lastJustifiedStateRoot = Bytes32.random().toHexString();
    this.lastFinalizedBlockRoot = Bytes32.random().toHexString();
    this.lastFinalizedStateRoot = Bytes32.random().toHexString();
  }

  public TimeSeriesRecord(
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
  public String toJSON() {
    Gson gson = new GsonBuilder().create();
    GsonBuilder gsonBuilder = new GsonBuilder();

    Type bytes32Type = new TypeToken<Bytes32>() {}.getType();
    JsonSerializer<Bytes32> serializer =
        (src, typeOfSrc, context) -> {
          JsonObject obj = new JsonObject();
          obj.addProperty("Bytes32", src.toHexString());
          return obj;
        };

    Type blsSignatureType = new TypeToken<BLSSignature>() {}.getType();
    JsonSerializer<BLSSignature> blsSerializer =
        (src, typeOfSrc, context) -> {
          JsonObject obj = new JsonObject();
          obj.addProperty("BLSSignature", src.toString());
          return obj;
        };

    Type blsPublicKeyType = new TypeToken<BLSPublicKey>() {}.getType();
    JsonSerializer<BLSPublicKey> blsPubKeySerializer =
        (src, typeOfSrc, context) -> {
          JsonObject obj = new JsonObject();
          obj.addProperty("BLSPublicKey", src.toString());
          return obj;
        };

    Type validatorJoinType = new TypeToken<ValidatorJoin>() {}.getType();
    JsonSerializer<ValidatorJoin> validatorJoinJsonSerializer =
        new JsonSerializer<ValidatorJoin>() {
          @Override
          public JsonElement serialize(
              ValidatorJoin src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("pubkey", src.getValidator().getPubkey().toString());
            obj.addProperty("balance", src.getBalance().toString());
            return obj;
          }
        };
    gsonBuilder.registerTypeAdapter(validatorJoinType, validatorJoinJsonSerializer);

    Gson customGson = gsonBuilder.setPrettyPrinting().create();
    return customGson.toJson(this);
  }

  @Override
  public String toCSV() {
    return null;
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
