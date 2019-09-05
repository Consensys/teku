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

import com.google.common.primitives.UnsignedLong;

import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

class MockStartBeaconStateGeneratorTest {
  @Test
  public void shouldCreateInitialBeaconChainState() {
    final UnsignedLong genesisTime = UnsignedLong.valueOf(498294294824924924L);
    final int validatorCount = 10;

    final List<BLSKeyPair> validatorKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount - 1);

    final List<DepositData> deposits =
        new MockStartDepositGenerator().createDeposits(validatorKeyPairs);

    final BeaconStateWithCache initialBeaconState =
        new MockStartBeaconStateGenerator().createInitialBeaconState(genesisTime, deposits);

    assertEquals(validatorCount, initialBeaconState.getValidators().size());
    assertEquals(validatorCount, initialBeaconState.getEth1_data().getDeposit_count().longValue());

    final List<BLSPublicKey> actualValidatorPublicKeys =
        initialBeaconState.getValidators().stream()
            .map(Validator::getPubkey)
            .collect(Collectors.toList());
    final List<BLSPublicKey> expectedValidatorPublicKeys =
        validatorKeyPairs.stream().map(BLSKeyPair::getPublicKey).collect(Collectors.toList());
    assertEquals(expectedValidatorPublicKeys, actualValidatorPublicKeys);
  }

  @Test
  public void newDepositFileGeneration() {


    List<JSONObject> depositData = new ArrayList<>();
    List<JSONObject> keys = new ArrayList<>();

    JSONObject jsonObject = new JSONObject();


    final UnsignedLong genesisTime = UnsignedLong.valueOf(1567630429);
    final int validatorCount = 8;

    final List<BLSKeyPair> validatorKeyPairs =
            new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount - 1);

    validatorKeyPairs.forEach(blsKeyPair -> {
      JSONObject keypair = new JSONObject();
      keypair.put("public_key",blsKeyPair.getPublicKey().toBytes().toHexString());
      keypair.put("private_key",blsKeyPair.getSecretKey().getSecretKey().toBytes().toHexString());
      keys.add(keypair);
    });

    final List<DepositData> deposits =
            new MockStartDepositGenerator().createDeposits(validatorKeyPairs);

    deposits.forEach(deposit -> {
      JSONObject data = new JSONObject();
      //List<String> data = new ArrayList<>();
      data.put("amount",deposit.getAmount());
      data.put("public_key",deposit.getPubkey().toBytes().toHexString());
      data.put("signature",deposit.getSignature().toBytes().toHexString());
      data.put("withdrawal_credentials",deposit.getWithdrawal_credentials().toHexString());
      depositData.add(data);
    });

    jsonObject.put("genesis_time",genesisTime);
    jsonObject.put("validator_count",validatorCount);
    jsonObject.put("deposits", depositData);
    jsonObject.put("validator_credentials", keys);


    final byte[] eth1BlockHashBytes = new byte[32];
    Arrays.fill(eth1BlockHashBytes, (byte) 0x42);
    Bytes32 block_hash = Bytes32.wrap(eth1BlockHashBytes);

    jsonObject.put("block_hash", block_hash.toHexString());

    UnsignedLong ETH1_TIMESTAMP =
            UnsignedLong.valueOf(new BigInteger("2").pow(40));

    jsonObject.put("eth1_timestamp", ETH1_TIMESTAMP);

    MerkleTree<DepositWithIndex> merkleTree = DepositUtil.generateMerkleTree(deposits);

    // Write JSON file
    try (Writer file =
                 Files.newBufferedWriter(Paths.get("../../interopDepositsAndKeys.json"), UTF_8)) {
      file.write(jsonObject.toJSONString());
      file.flush();

    } catch (IOException e) {
      System.out.println(e.toString());
    }
  }
}
