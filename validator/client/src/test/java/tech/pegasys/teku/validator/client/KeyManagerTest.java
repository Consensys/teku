/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.core.signatures.NoOpSigner.NO_OP_SIGNER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

class KeyManagerTest {

  final ValidatorLoader validatorLoader = Mockito.mock(ValidatorLoader.class);
  final OwnedValidators ownedValidators = Mockito.mock(OwnedValidators.class);

  @Test
  void shouldReturnActiveValidatorsList() {
    final KeyManager keyManager = new KeyManager(validatorLoader);
    List<Validator> validatorsList = generateActiveValidatosList();
    when(ownedValidators.getActiveValidators()).thenReturn(validatorsList);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    List<Validator> activeValidatorList = keyManager.getActiveValidatorKeys();

    assertThat(activeValidatorList).isEqualTo(validatorsList);
  }

  @Test
  void shouldReturnEmptyKeyList() {
    final KeyManager keyManager = new KeyManager(validatorLoader);
    List<Validator> validatorsList = Collections.emptyList();
    when(ownedValidators.getActiveValidators()).thenReturn(validatorsList);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    List<Validator> activeValidatorList = keyManager.getActiveValidatorKeys();

    assertThat(activeValidatorList).isEmpty();
  }

  @Test
  void shoudNotImportValidatorsWithWrongPass() {
    final List<String> keystoreList = getKeystoresList();
    List<String> passwordList = getKeystorePasswordList();
    passwordList.set(0, "invalidPass");
    passwordList.set(1, "invalidPass");

    final KeyManager keyManager = new KeyManager(validatorLoader);
    final List<PostKeyResult> retList = keyManager.importValidators(keystoreList, passwordList, "");

    assertThat(retList.size()).isEqualTo(2);
    assertThat(retList.get(0).getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(retList.get(0).getMessage().get()).isEqualTo("Invalid password.");

    assertThat(retList.get(1).getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(retList.get(1).getMessage().get()).isEqualTo("Invalid password.");
  }

  @Test
  void shoudNotImportValidatorsWithInvalidJson() {
    List<String> keystoreList = getKeystoresList();
    final List<String> passwordList = getKeystorePasswordList();
    keystoreList.set(0, "{invalid:1, fake:\"msg\"}");
    keystoreList.set(1, "{invalid:2, fake:\"msg\"}");

    final KeyManager keyManager = new KeyManager(validatorLoader);
    final List<PostKeyResult> retList = keyManager.importValidators(keystoreList, passwordList, "");

    assertThat(retList.size()).isEqualTo(2);
    assertThat(retList.get(0).getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(retList.get(0).getMessage().get()).isEqualTo("Invalid keystore.");

    assertThat(retList.get(1).getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(retList.get(1).getMessage().get()).isEqualTo("Invalid keystore.");
  }

  @Test
  void shoudNotImportValidatorsWhenListDoesnMatch() {
    List<String> keystoreList = getKeystoresList();
    final List<String> passwordList = getKeystorePasswordList();
    keystoreList.remove(1);

    final KeyManager keyManager = new KeyManager(validatorLoader);
    final List<PostKeyResult> retList = keyManager.importValidators(keystoreList, passwordList, "");

    assertThat(retList.size()).isEqualTo(1);
    assertThat(retList.get(0).getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(retList.get(0).getMessage().get())
        .isEqualTo("Quantity of keystores and passwords must be the same.");
  }

  private List<Validator> generateActiveValidatosList() {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    Validator validator1 =
        new Validator(keyPair1.getPublicKey(), NO_OP_SIGNER, Optional::empty, true);
    Validator validator2 =
        new Validator(keyPair2.getPublicKey(), NO_OP_SIGNER, Optional::empty, false);
    return Arrays.asList(validator1, validator2);
  }

  private List<String> getKeystorePasswordList() {
    return new ArrayList<>(Arrays.asList("testpassword", "testpassword"));
  }

  private List<String> getKeystoresList() {
    return new ArrayList<>(
        Arrays.asList(
            "{\n"
                + "  \"crypto\" : {\n"
                + "    \"kdf\" : {\n"
                + "      \"function\" : \"pbkdf2\",\n"
                + "      \"params\" : {\n"
                + "        \"dklen\" : 32,\n"
                + "        \"c\" : 1,\n"
                + "        \"prf\" : \"hmac-sha256\",\n"
                + "        \"salt\" : \"1298e29fdbf3fdf22808796f62be5e263b247385aaf6435acd47d24c7c81e6ff\"\n"
                + "      },\n"
                + "      \"message\" : \"\"\n"
                + "    },\n"
                + "    \"checksum\" : {\n"
                + "      \"function\" : \"sha256\",\n"
                + "      \"params\" : { },\n"
                + "      \"message\" : \"af54d8726627bdefe352dc874a4a78a495607c6f4844f4adf518f61665cf9b77\"\n"
                + "    },\n"
                + "    \"cipher\" : {\n"
                + "      \"function\" : \"aes-128-ctr\",\n"
                + "      \"params\" : {\n"
                + "        \"iv\" : \"682b7cf72ea5ca3c22befaa4a5d5c6ea\"\n"
                + "      },\n"
                + "      \"message\" : \"ffb1991a37893ccbf0da79461e9fe4e8ff717a4ec672d6b97e9991c2d7707862\"\n"
                + "    }\n"
                + "  },\n"
                + "  \"pubkey\" : \"a057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a\",\n"
                + "  \"version\" : 4,\n"
                + "  \"path\" : \"\",\n"
                + "  \"uuid\" : \"626ba318-2307-432e-834e-a88f5f1c6126\"\n"
                + "}",
            "{\n"
                + "  \"crypto\": {\n"
                + "    \"kdf\": {\n"
                + "      \"function\": \"pbkdf2\",\n"
                + "      \"params\": {\n"
                + "        \"dklen\": 32,\n"
                + "        \"c\": 262144,\n"
                + "        \"prf\": \"hmac-sha256\",\n"
                + "        \"salt\": \"d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3\"\n"
                + "      },\n"
                + "      \"message\": \"\"\n"
                + "    },\n"
                + "    \"checksum\": {\n"
                + "      \"function\": \"sha256\",\n"
                + "      \"params\": {},\n"
                + "      \"message\": \"18b148af8e52920318084560fd766f9d09587b4915258dec0676cba5b0da09d8\"\n"
                + "    },\n"
                + "    \"cipher\": {\n"
                + "      \"function\": \"aes-128-ctr\",\n"
                + "      \"params\": {\n"
                + "        \"iv\": \"264daa3f303d7259501c93d997d84fe6\"\n"
                + "      },\n"
                + "      \"message\": \"a9249e0ca7315836356e4c7440361ff22b9fe71e2e2ed34fc1eb03976924ed48\"\n"
                + "    }\n"
                + "  },\n"
                + "  \"pubkey\": \"9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07\",\n"
                + "  \"path\": \"m/12381/60/0/0\",\n"
                + "  \"uuid\": \"64625def-3331-4eea-ab6f-782f3ed16a83\",\n"
                + "  \"version\": 4\n"
                + "}"));
  }
}
