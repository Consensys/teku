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

package tech.pegasys.artemis;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.validator.client.ValidatorClientUtil;

@ExtendWith(BouncyCastleExtension.class)
public class PowchainServiceTest {

  @Test
  void generateValidatorDepositJSON() {
    int validatorCount = 100;
    JsonArray array = new JsonArray();
    for (int i = 0; i < validatorCount; i++) {

      // key generation and signature
      Bytes32 withdrawal_credentials = Bytes32.random();
      KeyPair blsKeys = KeyPair.random();
      SECP256K1.KeyPair secpKeys = SECP256K1.KeyPair.random();
      long amount = 32000000000l;
      Bytes proof_of_posssesion =
          ValidatorClientUtil.blsSignatureHelper(blsKeys, withdrawal_credentials, amount);

      // JSON object creation
      JsonObject object = new JsonObject();
      object.addProperty("withdrawal_credentials", withdrawal_credentials.toHexString());
      object.addProperty("blsKey", blsKeys.secretKey().toBytes().toHexString());
      object.addProperty("secpKey", secpKeys.secretKey().bytes().toHexString());
      object.addProperty("amount", amount);
      object.addProperty("proof_of_posssesion", proof_of_posssesion.toHexString());
      array.add(object);
    }

    // Write JSON file
    try (Writer file = Files.newBufferedWriter(Paths.get("../ValidatorDeposit.json"), UTF_8)) {
      file.write(array.toString());
      file.flush();

    } catch (IOException e) {
      System.out.println(e.toString());
    }
  }
}
