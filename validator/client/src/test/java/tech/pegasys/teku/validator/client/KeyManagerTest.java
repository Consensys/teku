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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.core.signatures.NoOpSigner.NO_OP_SIGNER;

import com.google.common.io.Resources;
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
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.service.serviceutils.layout.SeparateServiceDataDirLayout;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

class KeyManagerTest {

  final ValidatorLoader validatorLoader = Mockito.mock(ValidatorLoader.class);
  final OwnedValidators ownedValidators = Mockito.mock(OwnedValidators.class);

  @Test
  void shouldReturnActiveValidatorsList(@TempDir Path tempDir) {
    final DataDirLayout dataDir =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    final KeyManager keyManager = new KeyManager(validatorLoader, dataDir);
    final List<Validator> validatorsList = generateActiveValidatorsList();
    when(ownedValidators.getActiveValidators()).thenReturn(validatorsList);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    final List<Validator> activeValidatorList = keyManager.getActiveValidatorKeys();

    assertThat(activeValidatorList).isEqualTo(validatorsList);
  }

  @Test
  void shouldReturnEmptyKeyList(@TempDir Path tempDir) {
    final DataDirLayout dataDir =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    final KeyManager keyManager = new KeyManager(validatorLoader, dataDir);
    List<Validator> validatorsList = Collections.emptyList();
    when(ownedValidators.getActiveValidators()).thenReturn(validatorsList);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    List<Validator> activeValidatorList = keyManager.getActiveValidatorKeys();

    assertThat(activeValidatorList).isEmpty();
  }

  @Test
  void shouldCreateKeystoreFiles(@TempDir Path tempDir) throws URISyntaxException, IOException {
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    when(ownedValidators.hasValidator(any())).thenReturn(false);
    final DataDirLayout dataDir =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    final List<String> keystoreList = getKeystoresList();
    keystoreList.remove(1);
    final List<String> passwordList = getKeystorePasswordList();
    passwordList.remove(1);

    assertThat(ValidatorClientService.getAlterableKeystorePath(dataDir)).doesNotExist();
    assertThat(ValidatorClientService.getAlterableKeystorePasswordPath(dataDir)).doesNotExist();

    final KeyManager keyManager = new KeyManager(validatorLoader, dataDir);
    final List<PostKeyResult> postKeyResults =
        keyManager.importValidators(keystoreList, passwordList, "");

    assertThat(ValidatorClientService.getAlterableKeystorePath(dataDir)).exists();
    assertThat(ValidatorClientService.getAlterableKeystorePasswordPath(dataDir)).exists();

    final BLSPublicKey validator1 =
        BLSPublicKey.fromSSZBytes(
            Bytes.fromHexString(
                "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a"));

    final String validatorFileName = validator1.toSSZBytes().toUnprefixedHexString().toLowerCase();
    final Path validatorPath =
        ValidatorClientService.getAlterableKeystorePath(dataDir)
            .resolve(validatorFileName + ".json");
    final Path validatorPasswordPath =
        ValidatorClientService.getAlterableKeystorePasswordPath(dataDir)
            .resolve(validatorFileName + ".txt");

    assertThat(Files.exists(validatorPath)).isTrue();
    assertThat(Files.exists(validatorPasswordPath)).isTrue();

    final KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(validatorPath);
    final String password = Files.readString(validatorPasswordPath);

    assertThat(KeyStore.validatePassword(password, keyStoreData)).isTrue();

    assertThat(postKeyResults.size()).isEqualTo(1);
    assertThat(postKeyResults.get(0).getImportStatus()).isEqualTo(ImportStatus.IMPORTED);
    assertThat(postKeyResults.get(0).getMessage()).isEmpty();

    verify(validatorLoader, times(1)).loadValidators();
  }

  @Test
  void shouldNotCreateDuplicateKeystoreFiles(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    when(ownedValidators.hasValidator(any())).thenReturn(false);
    final DataDirLayout dataDir =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    final List<String> keystoreList = getKeystoresList();
    keystoreList.remove(1);
    final List<String> passwordList = getKeystorePasswordList();
    passwordList.remove(1);

    assertThat(ValidatorClientService.getAlterableKeystorePath(dataDir)).doesNotExist();
    assertThat(ValidatorClientService.getAlterableKeystorePasswordPath(dataDir)).doesNotExist();

    final KeyManager keyManager = new KeyManager(validatorLoader, dataDir);
    List<PostKeyResult> postKeyResults =
        keyManager.importValidators(keystoreList, passwordList, "");

    assertThat(ValidatorClientService.getAlterableKeystorePath(dataDir)).exists();
    assertThat(ValidatorClientService.getAlterableKeystorePasswordPath(dataDir)).exists();

    final BLSPublicKey validator1 =
        BLSPublicKey.fromSSZBytes(
            Bytes.fromHexString(
                "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a"));

    final String validatorFileName = validator1.toSSZBytes().toUnprefixedHexString().toLowerCase();
    final Path validatorPath =
        ValidatorClientService.getAlterableKeystorePath(dataDir)
            .resolve(validatorFileName + ".json");
    final Path validatorPasswordPath =
        ValidatorClientService.getAlterableKeystorePasswordPath(dataDir)
            .resolve(validatorFileName + ".txt");

    assertThat(Files.exists(validatorPath)).isTrue();
    assertThat(Files.exists(validatorPasswordPath)).isTrue();

    final KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(validatorPath);
    final String password = Files.readString(validatorPasswordPath);

    assertThat(KeyStore.validatePassword(password, keyStoreData)).isTrue();

    assertThat(postKeyResults.size()).isEqualTo(1);
    assertThat(postKeyResults.get(0).getImportStatus()).isEqualTo(ImportStatus.IMPORTED);
    assertThat(postKeyResults.get(0).getMessage()).isEmpty();

    postKeyResults = keyManager.importValidators(keystoreList, passwordList, "");

    assertThat(postKeyResults.size()).isEqualTo(1);
    assertThat(postKeyResults.get(0).getImportStatus()).isEqualTo(ImportStatus.DUPLICATE);

    verify(validatorLoader, times(1)).loadValidators();
  }

  @Test
  void shouldNotImportValidatorsWithWrongPass(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    when(ownedValidators.hasValidator(any())).thenReturn(false);
    final DataDirLayout dataDir =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    final List<String> keystoreList = getKeystoresList();
    final List<String> passwordList = getKeystorePasswordList();
    passwordList.set(0, "invalidPass");

    final KeyManager keyManager = new KeyManager(validatorLoader, dataDir);
    final List<PostKeyResult> importResult =
        keyManager.importValidators(keystoreList, passwordList, "");

    assertThat(importResult.size()).isEqualTo(2);
    assertThat(importResult.get(0).getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(importResult.get(0).getMessage().get()).isEqualTo("Invalid password.");

    assertThat(importResult.get(1).getImportStatus()).isEqualTo(ImportStatus.IMPORTED);

    verify(validatorLoader, times(1)).loadValidators();
  }

  @Test
  void shouldNotImportValidatorsWithInvalidJson(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final DataDirLayout dataDir =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    final List<String> keystoreList = getKeystoresList();
    final List<String> passwordList = getKeystorePasswordList();
    keystoreList.set(0, "{invalid:1, fake:\"msg\"}");
    keystoreList.set(1, "{invalid:2, fake:\"msg\"}");

    final KeyManager keyManager = new KeyManager(validatorLoader, dataDir);
    final List<PostKeyResult> importResult =
        keyManager.importValidators(keystoreList, passwordList, "");

    assertThat(importResult.size()).isEqualTo(2);
    assertThat(importResult.get(0).getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(importResult.get(0).getMessage().get()).isEqualTo("Invalid keystore.");

    assertThat(importResult.get(1).getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(importResult.get(1).getMessage().get()).isEqualTo("Invalid keystore.");
  }

  @Test
  void shouldNotImportValidatorsWhenListDoesntMatch(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final DataDirLayout dataDir =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    final List<String> keystoreList = getKeystoresList();
    final List<String> passwordList = getKeystorePasswordList();
    keystoreList.remove(1);

    final KeyManager keyManager = new KeyManager(validatorLoader, dataDir);
    final List<PostKeyResult> importResult =
        keyManager.importValidators(keystoreList, passwordList, "");

    assertThat(importResult.size()).isEqualTo(1);
    assertThat(importResult.get(0).getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(importResult.get(0).getMessage().get())
        .isEqualTo("Keystores and passwords quantity must be the same.");
  }

  private List<Validator> generateActiveValidatorsList() {
    final BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    final BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    final Validator validator1 =
        new Validator(keyPair1.getPublicKey(), NO_OP_SIGNER, Optional::empty, true);
    final Validator validator2 =
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
