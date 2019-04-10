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
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class TimeSeriesRecord implements IRecordAdapter {

  private Long index;
  private Long slot;
  private Long epoch;
  private BeaconBlock block;
  private BeaconState state;

  private String lastJustifiedBlockRoot;
  private String lastJustifiedStateRoot;
  private String lastFinalizedBlockRoot;
  private String lastFinalizedStateRoot;

  public TimeSeriesRecord() {
    // new Hello(1, 1, Bytes32.random(), UInt64.valueOf(0), Bytes32.random(), UInt64.valueOf(0))
    this.index = Long.MAX_VALUE;
    this.slot = Long.MAX_VALUE;
    this.epoch = Long.MAX_VALUE;
    this.block =
        new BeaconBlock(
            1,
            Bytes32.random(),
            Bytes32.random(),
            BLSSignature.random(),
            new Eth1Data(Bytes32.random(), Bytes32.random()),
            null,
            BLSSignature.random());
    this.state = new BeaconState();
    this.lastJustifiedBlockRoot = Bytes32.random().toHexString();
    this.lastJustifiedStateRoot = Bytes32.random().toHexString();
    this.lastFinalizedBlockRoot = Bytes32.random().toHexString();
    this.lastFinalizedStateRoot = Bytes32.random().toHexString();
  }

  public TimeSeriesRecord(
      Long index,
      Long slot,
      Long epoch,
      BeaconBlock block,
      BeaconState state,
      String lastJustifiedBlockRoot,
      String lastJustifiedStateRoot,
      String lastFinalizedBlockRoot,
      String lastFinalizedStateRoot) {
    this.index = index;
    this.slot = slot;
    this.epoch = epoch;
    this.block = block;
    this.state = state;
    this.lastJustifiedBlockRoot = lastJustifiedBlockRoot;
    this.lastJustifiedStateRoot = lastJustifiedStateRoot;
    this.lastFinalizedBlockRoot = lastFinalizedBlockRoot;
    this.lastFinalizedStateRoot = lastFinalizedStateRoot;
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

    gsonBuilder.registerTypeAdapter(blsPublicKeyType, blsPubKeySerializer);

    Gson customGson = gsonBuilder.create();
    return customGson.toJson(this);
  }

  @Override
  public String toCSV() {
    return null;
  }

  public Long getIndex() {
    return index;
  }

  public void setIndex(Long index) {
    this.index = index;
  }

  public Long getSlot() {
    return slot;
  }

  public void setSlot(Long slot) {
    this.slot = slot;
  }

  public Long getEpoch() {
    return epoch;
  }

  public void setEpoch(Long epoch) {
    this.epoch = epoch;
  }

  public BeaconBlock getBlock() {
    return block;
  }

  public void setBlock(BeaconBlock block) {
    this.block = block;
  }

  public BeaconState getState() {
    return state;
  }

  public void setState(BeaconState state) {
    this.state = state;
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
}
