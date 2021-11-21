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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.io.Resources;
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
  void shouldNotImportValidatorsWithWrongPass() throws URISyntaxException, IOException {
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
  void shouldNotImportValidatorsWithInvalidJson() throws URISyntaxException, IOException {
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
  void shouldNotImportValidatorsWhenListDoesntMatch() throws URISyntaxException, IOException {
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

  private List<String> getKeystoresList() throws URISyntaxException, IOException {
    final URL resource1 = Resources.getResource("testKeystore.json");
    final URL resource2 = Resources.getResource("pbkdf2TestVector.json");
    String keystore1 =
            new String(Files.readAllBytes(Path.of(resource1.toURI())), Charset.defaultCharset());
    String keystore2 =
            new String(Files.readAllBytes(Path.of(resource2.toURI())), Charset.defaultCharset());
    return new ArrayList<>(Arrays.asList(keystore1, keystore2));
  }
}
