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

package tech.pegasys.artemis.util.bls.keystore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.artemis.util.bls.keystore.CryptoFunction.PBKDF2;
import static tech.pegasys.artemis.util.bls.keystore.CryptoFunction.SCRYPT;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KeyStoreDataJsonTest {
  private static final String sCryptJson =
      "{\n"
          + "    \"crypto\": {\n"
          + "        \"kdf\": {\n"
          + "            \"function\": \"scrypt\",\n"
          + "            \"params\": {\n"
          + "                \"dklen\": 32,\n"
          + "                \"n\": 262144,\n"
          + "                \"p\": 1,\n"
          + "                \"r\": 8,\n"
          + "                \"salt\": \"d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3\"\n"
          + "            },\n"
          + "            \"message\": \"\"\n"
          + "        },\n"
          + "        \"checksum\": {\n"
          + "            \"function\": \"sha256\",\n"
          + "            \"params\": {},\n"
          + "            \"message\": \"149aafa27b041f3523c53d7acba1905fa6b1c90f9fef137568101f44b531a3cb\"\n"
          + "        },\n"
          + "        \"cipher\": {\n"
          + "            \"function\": \"aes-128-ctr\",\n"
          + "            \"params\": {\n"
          + "                \"iv\": \"264daa3f303d7259501c93d997d84fe6\"\n"
          + "            },\n"
          + "            \"message\": \"54ecc8863c0550351eee5720f3be6a5d4a016025aa91cd6436cfec938d6a8d30\"\n"
          + "        }\n"
          + "    },\n"
          + "    \"pubkey\": \"9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07\",\n"
          + "    \"path\": \"m/12381/60/3141592653/589793238\",\n"
          + "    \"uuid\": \"1d85ae20-35c5-4611-98e8-aa14a633906f\",\n"
          + "    \"version\": 4\n"
          + "}";
  private static final String pbkdf2Json =
      "{\n"
          + "    \"crypto\": {\n"
          + "        \"kdf\": {\n"
          + "            \"function\": \"pbkdf2\",\n"
          + "            \"params\": {\n"
          + "                \"dklen\": 32,\n"
          + "                \"c\": 262144,\n"
          + "                \"prf\": \"hmac-sha256\",\n"
          + "                \"salt\": \"d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3\"\n"
          + "            },\n"
          + "            \"message\": \"\"\n"
          + "        },\n"
          + "        \"checksum\": {\n"
          + "            \"function\": \"sha256\",\n"
          + "            \"params\": {},\n"
          + "            \"message\": \"18b148af8e52920318084560fd766f9d09587b4915258dec0676cba5b0da09d8\"\n"
          + "        },\n"
          + "        \"cipher\": {\n"
          + "            \"function\": \"aes-128-ctr\",\n"
          + "            \"params\": {\n"
          + "                \"iv\": \"264daa3f303d7259501c93d997d84fe6\"\n"
          + "            },\n"
          + "            \"message\": \"a9249e0ca7315836356e4c7440361ff22b9fe71e2e2ed34fc1eb03976924ed48\"\n"
          + "        }\n"
          + "    },\n"
          + "    \"pubkey\": \"9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07\",\n"
          + "    \"path\": \"m/12381/60/0/0\",\n"
          + "    \"uuid\": \"64625def-3331-4eea-ab6f-782f3ed16a83\",\n"
          + "    \"version\": 4\n"
          + "}";

  private static final String missingKdfParamJson =
      "{\n"
          + "    \"crypto\": {\n"
          + "        \"kdf\": {\n"
          + "            \"function\": \"pbkdf2\",\n"
          + "            \"message\": \"\"\n"
          + "        },\n"
          + "        \"checksum\": {\n"
          + "            \"function\": \"sha256\",\n"
          + "            \"params\": {},\n"
          + "            \"message\": \"18b148af8e52920318084560fd766f9d09587b4915258dec0676cba5b0da09d8\"\n"
          + "        },\n"
          + "        \"cipher\": {\n"
          + "            \"function\": \"aes-128-ctr\",\n"
          + "            \"params\": {\n"
          + "                \"iv\": \"264daa3f303d7259501c93d997d84fe6\"\n"
          + "            },\n"
          + "            \"message\": \"a9249e0ca7315836356e4c7440361ff22b9fe71e2e2ed34fc1eb03976924ed48\"\n"
          + "        }\n"
          + "    },\n"
          + "    \"pubkey\": \"9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07\",\n"
          + "    \"path\": \"m/12381/60/0/0\",\n"
          + "    \"uuid\": \"64625def-3331-4eea-ab6f-782f3ed16a83\",\n"
          + "    \"version\": 4\n"
          + "}";

  private static final String emptyKdfParams =
      "{\n"
          + "    \"crypto\": {\n"
          + "        \"kdf\": {\n"
          + "            \"function\": \"pbkdf2\",\n"
          + "            \"params\": {},\n"
          + "            \"message\": \"\"\n"
          + "        },\n"
          + "        \"checksum\": {\n"
          + "            \"function\": \"sha256\",\n"
          + "            \"params\": {},\n"
          + "            \"message\": \"18b148af8e52920318084560fd766f9d09587b4915258dec0676cba5b0da09d8\"\n"
          + "        },\n"
          + "        \"cipher\": {\n"
          + "            \"function\": \"aes-128-ctr\",\n"
          + "            \"params\": {\n"
          + "                \"iv\": \"264daa3f303d7259501c93d997d84fe6\"\n"
          + "            },\n"
          + "            \"message\": \"a9249e0ca7315836356e4c7440361ff22b9fe71e2e2ed34fc1eb03976924ed48\"\n"
          + "        }\n"
          + "    },\n"
          + "    \"pubkey\": \"9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07\",\n"
          + "    \"path\": \"m/12381/60/0/0\",\n"
          + "    \"uuid\": \"64625def-3331-4eea-ab6f-782f3ed16a83\",\n"
          + "    \"version\": 4\n"
          + "}";

  @ParameterizedTest
  @ValueSource(strings = {sCryptJson, pbkdf2Json})
  void validatePasswordOfCryptoTestVectors(final String keyStoreJson) throws Exception {
    final KeyStore keyStore = KeyStoreFactory.loadFromJson(sCryptJson);
    final KeyStoreData keyStoreData = keyStore.getKeyStoreData();
    assertNotNull(keyStoreData);
    final KdfParam param = keyStoreData.getCrypto().getKdf().getParam();
    assertNotNull(param);
    if (keyStoreData.getCrypto().getKdf().getCryptoFunction() == SCRYPT) {
      assertTrue(param instanceof SCryptParam);
    } else if (keyStoreData.getCrypto().getKdf().getCryptoFunction() == PBKDF2) {
      assertTrue(param instanceof Pbkdf2Param);
    } else {
      fail("Unsupported crypto function");
    }
    assertTrue(keyStore.validatePassword("testpassword"));
    assertFalse(keyStore.validatePassword("test"));
  }

  @ParameterizedTest
  @ValueSource(strings = {missingKdfParamJson, emptyKdfParams})
  void loadingKeyStoreWithInvalidKdfParamsThrowsException(final String invalidJson) {
    Assertions.assertThrows(
        JsonMappingException.class, () -> KeyStoreFactory.loadFromJson(invalidJson));
  }
}
