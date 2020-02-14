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

package tech.pegasys.artemis.datastructures.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.primitives.UnsignedLong;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.ValidatorRead;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

class MockStartBeaconStateGeneratorTest {
  private static final Logger LOG = LogManager.getLogger();

  @Test
  public void shouldCreateInitialBeaconChainState() {
    final UnsignedLong genesisTime = UnsignedLong.valueOf(498294294824924924L);
    final int validatorCount = 10;

    final List<BLSKeyPair> validatorKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount);

    final List<DepositData> deposits =
        new MockStartDepositGenerator().createDeposits(validatorKeyPairs);

    final BeaconState initialBeaconState =
        new MockStartBeaconStateGenerator().createInitialBeaconState(genesisTime, deposits);

    assertEquals(validatorCount, initialBeaconState.getValidators().size());
    assertEquals(validatorCount, initialBeaconState.getEth1_data().getDeposit_count().longValue());

    final List<BLSPublicKey> actualValidatorPublicKeys =
        initialBeaconState.getValidators().stream()
            .map(ValidatorRead::getPubkey)
            .collect(Collectors.toList());
    final List<BLSPublicKey> expectedValidatorPublicKeys =
        validatorKeyPairs.stream().map(BLSKeyPair::getPublicKey).collect(Collectors.toList());
    assertEquals(expectedValidatorPublicKeys, actualValidatorPublicKeys);
  }

  @Test
  @Disabled
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void newDepositFileGeneration() {

    List<JSONObject> depositData = new ArrayList<>();
    List<JSONObject> keys = new ArrayList<>();

    JSONObject jsonObject = new JSONObject();

    final UnsignedLong genesisTime = UnsignedLong.valueOf(1567630429);
    final int validatorCount = 8;

    final List<BLSKeyPair> validatorKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount);

    validatorKeyPairs.forEach(
        blsKeyPair -> {
          JSONObject keypair = new JSONObject();
          keypair.put("public_key", blsKeyPair.getPublicKey().toBytes().toHexString());
          keypair.put(
              "private_key", blsKeyPair.getSecretKey().getSecretKey().toBytes().toHexString());
          keys.add(keypair);
        });

    final List<DepositData> deposits =
        new MockStartDepositGenerator().createDeposits(validatorKeyPairs);

    deposits.forEach(
        deposit -> {
          JSONObject data = new JSONObject();
          // List<String> data = new ArrayList<>();
          data.put("amount", deposit.getAmount());
          data.put("public_key", deposit.getPubkey().toBytes().toHexString());
          data.put("signature", deposit.getSignature().toBytes().toHexString());
          data.put("withdrawal_credentials", deposit.getWithdrawal_credentials().toHexString());
          depositData.add(data);
        });

    jsonObject.put("genesis_time", genesisTime);
    jsonObject.put("validator_count", validatorCount);
    jsonObject.put("deposits", depositData);
    jsonObject.put("validator_credentials", keys);

    final byte[] eth1BlockHashBytes = new byte[32];
    Arrays.fill(eth1BlockHashBytes, (byte) 0x42);
    Bytes32 block_hash = Bytes32.wrap(eth1BlockHashBytes);

    jsonObject.put("block_hash", block_hash.toHexString());

    UnsignedLong ETH1_TIMESTAMP = UnsignedLong.valueOf(new BigInteger("2").pow(40));

    jsonObject.put("eth1_timestamp", ETH1_TIMESTAMP);

    // Write JSON file
    try (Writer file =
        Files.newBufferedWriter(Paths.get("../../interopDepositsAndKeys.json"), UTF_8)) {
      file.write(jsonObject.toJSONString());
      file.flush();

    } catch (IOException e) {
      System.out.println(e.toString());
    }
  }

  public static class GenesisState {
    Bytes32 root;
    BeaconState beacon_state;

    public GenesisState(Bytes32 root, BeaconState beacon_state) {
      this.root = root;
      this.beacon_state = beacon_state;
    }
  }

  @Test
  @Disabled
  public void printBeaconState() throws Exception {
    final UnsignedLong genesisTime = UnsignedLong.valueOf(1567719788L);
    final int validatorCount = 16;

    final List<BLSKeyPair> validatorKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount);

    final List<DepositData> deposits =
        new MockStartDepositGenerator().createDeposits(validatorKeyPairs);

    final BeaconState initialBeaconState =
        new MockStartBeaconStateGenerator().createInitialBeaconState(genesisTime, deposits);

    ExclusionStrategy strategy =
        new ExclusionStrategy() {
          @Override
          public boolean shouldSkipField(FieldAttributes field) {
            return field.getDeclaringClass() == BeaconStateWithCache.class;
          }

          @Override
          public boolean shouldSkipClass(Class<?> clazz) {
            return false;
          }
        };

    GsonBuilder builder = new GsonBuilder().addSerializationExclusionStrategy(strategy);

    builder.registerTypeAdapter(
        UnsignedLong.class,
        (JsonSerializer<UnsignedLong>)
            (src, typeOfSrc, context) -> new JsonPrimitive(src.bigIntegerValue()));
    builder.registerTypeAdapter(
        UnsignedLong.class,
        (JsonDeserializer<UnsignedLong>)
            (json, typeOfT, context) -> UnsignedLong.valueOf(json.getAsLong()));

    builder.registerTypeAdapter(
        Bytes.class,
        (JsonSerializer<Bytes>)
            (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString().toLowerCase()));
    builder.registerTypeAdapter(
        Bytes.class,
        (JsonDeserializer<Bytes>)
            (json, typeOfT, context) -> Bytes.fromHexString(json.getAsString()));

    builder.registerTypeAdapter(
        Bytes32.class,
        (JsonSerializer<Bytes32>)
            (src, typeOfSrc, context) -> new JsonPrimitive(src.toHexString().toLowerCase()));
    builder.registerTypeAdapter(
        Bytes32.class,
        (JsonDeserializer<Bytes32>)
            (json, typeOfT, context) -> Bytes32.fromHexString(json.getAsString()));

    builder.registerTypeAdapter(
        Bytes4.class,
        (JsonSerializer<Bytes4>)
            (src, typeOfSrc, context) ->
                new JsonPrimitive(src.getWrappedBytes().toHexString().toLowerCase()));
    builder.registerTypeAdapter(
        Bytes32.class,
        (JsonDeserializer<Bytes4>)
            (json, typeOfT, context) -> new Bytes4(Bytes.fromHexString(json.getAsString())));

    builder.registerTypeAdapter(
        BLSPublicKey.class,
        (JsonSerializer<BLSPublicKey>)
            (src, typeOfSrc, context) ->
                new JsonPrimitive(src.toBytes().toHexString().toLowerCase()));
    builder.registerTypeAdapter(
        BLSPublicKey.class,
        (JsonDeserializer<BLSPublicKey>)
            (json, typeOfT, context) ->
                BLSPublicKey.fromBytes(Bytes32.fromHexString(json.getAsString())));

    builder.registerTypeAdapter(
        Bitvector.class,
        (JsonSerializer<Bitvector>)
            (src, typeOfSrc, context) ->
                new JsonPrimitive(Bytes.wrap(src.getByteArray()).toHexString().toLowerCase()));
    builder.registerTypeAdapter(
        Bitvector.class,
        (JsonDeserializer<Bitvector>)
            (json, typeOfT, context) -> {
              Bytes bytes = Bytes.fromHexString(json.getAsString());
              int length = bytes.bitLength();
              return Bitvector.fromBytes(bytes, length);
            });

    GenesisState state = new GenesisState(initialBeaconState.hash_tree_root(), initialBeaconState);
    Gson gson = builder.create();
    String jsonString = gson.toJson(state);

    try (Writer file =
        Files.newBufferedWriter(Paths.get("../../artemis_genesis_beacon_state.json"), UTF_8)) {
      file.write(jsonString);
      file.flush();
    } catch (IOException e) {
      System.out.println(e.toString());
    }
  }

  public static InputStream getInputStream(String file) {
    URL url = null;
    InputStream in = null;
    try {
      url = new URL(file);
      in = url.openConnection().getInputStream();
    } catch (IOException e) {
      LOG.warn("Failed to load " + file, e);
    }
    return in;
  }

  public static Object getObjectFromYAMLInputStream(InputStream in) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Object object = null;
    try {
      object = mapper.readerFor(Map.class).readValue(in);
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
    return object;
  }

  public static Object fileToObject(String file) {
    return getObjectFromYAMLInputStream(getInputStream(file));
  }
}
