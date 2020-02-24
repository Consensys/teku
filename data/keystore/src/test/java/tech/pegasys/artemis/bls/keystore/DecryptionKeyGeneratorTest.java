/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.bls.keystore;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.keystore.builder.Pbkdf2ParamBuilder;
import tech.pegasys.artemis.bls.keystore.builder.SCryptParamBuilder;
import tech.pegasys.artemis.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.artemis.bls.keystore.model.SCryptParam;

class DecryptionKeyGeneratorTest {
  private static final Bytes32 SALT =
      Bytes32.fromHexString("d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");

  @Test
  void testBigPassword() {
    final SCryptParam kdfParam = SCryptParamBuilder.aSCryptParam().withSalt(SALT).build();
    final Bytes decryptionKey =
        DecryptionKeyGenerator.generate(Bytes.random(264).toArrayUnsafe(), kdfParam);
    assertEquals(32, decryptionKey.size());
  }

  @Test
  void testSCryptFunctionKeyLength() {
    final SCryptParam sCryptParam =
        SCryptParamBuilder.aSCryptParam().withSalt(SALT).withDklen(128).build();
    final Bytes decryptionKey =
        DecryptionKeyGenerator.generate("testpassword".getBytes(UTF_8), sCryptParam);
    assertEquals(128, decryptionKey.size());
  }

  @Test
  void testPBKDF2FunctionKeyLength() {
    final Pbkdf2Param pbkdf2Param =
        Pbkdf2ParamBuilder.aPbkdf2Param().withSalt(SALT).withDklen(128).build();
    final Bytes decryptionKey =
        DecryptionKeyGenerator.generate("testpassword".getBytes(UTF_8), pbkdf2Param);
    assertEquals(128, decryptionKey.size());
  }
}
