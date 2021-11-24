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

package tech.pegasys.teku.validator.client.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.service.serviceutils.layout.SeparateServiceDataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

class MutableValidatorSourceTest {

  private static final String EXPECTED_PASSWORD = "testpassword";

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final KeystoreLocker keystoreLocker = mock(KeystoreLocker.class);

  private final LocalValidatorSource localValidatorSource = mock(LocalValidatorSource.class);

  @Test
  void shouldAddNewValidator(final @TempDir Path tempDir) throws IOException, URISyntaxException {
    final DataDirLayout dataDirLayout =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    createMutableDirectoryStructure(dataDirLayout);
    final KeyStoreData keyStoreData = generateKeystore();

    final String validatorFileName = keyStoreData.getPubkey().toUnprefixedHexString().toLowerCase();
    final Path validatorPath =
        ValidatorClientService.getAlterableKeystorePath(dataDirLayout)
            .resolve(validatorFileName + ".json");
    final Path validatorPasswordPath =
        ValidatorClientService.getAlterableKeystorePasswordPath(dataDirLayout)
            .resolve(validatorFileName + ".txt");

    final MutableValidatorSource validatorSource =
        new MutableValidatorSource(
            spec, true, keystoreLocker, asyncRunner, dataDirLayout, localValidatorSource);
    assertThat(validatorSource.canAddValidator()).isTrue();

    Map<PostKeyResult, Optional<Signer>> resultValidator =
        validatorSource.addValidator(keyStoreData, EXPECTED_PASSWORD);

    assertThat(resultValidator.keySet().stream().findFirst().get().getImportStatus())
        .isEqualTo(ImportStatus.IMPORTED);
    assertThat(resultValidator.values().stream().findFirst().get()).isNotEmpty();
    assertThat(Files.exists(validatorPath)).isTrue();
    assertThat(Files.exists(validatorPasswordPath)).isTrue();
  }

  @Test
  void shouldNotDecryptWithWrongPass(final @TempDir Path tempDir)
      throws IOException, URISyntaxException {
    final DataDirLayout dataDirLayout =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    createMutableDirectoryStructure(dataDirLayout);
    final KeyStoreData keyStoreData = generateKeystore();

    final String validatorFileName = keyStoreData.getPubkey().toUnprefixedHexString().toLowerCase();
    final Path validatorPath =
        ValidatorClientService.getAlterableKeystorePath(dataDirLayout)
            .resolve(validatorFileName + ".json");
    final Path validatorPasswordPath =
        ValidatorClientService.getAlterableKeystorePasswordPath(dataDirLayout)
            .resolve(validatorFileName + ".txt");

    final MutableValidatorSource validatorSource =
        new MutableValidatorSource(
            spec, true, keystoreLocker, asyncRunner, dataDirLayout, localValidatorSource);
    assertThat(validatorSource.canAddValidator()).isTrue();

    Map<PostKeyResult, Optional<Signer>> resultValidator =
        validatorSource.addValidator(keyStoreData, "wrongPass");

    assertThat(resultValidator.keySet().stream().findFirst().get().getImportStatus())
        .isEqualTo(ImportStatus.ERROR);
    assertThat(resultValidator.values().stream().findFirst().get()).isEmpty();
    assertThat(resultValidator.keySet().stream().findFirst().get().getMessage().get())
        .isEqualTo("Invalid password.");

    assertThat(Files.exists(validatorPath)).isFalse();
    assertThat(Files.exists(validatorPasswordPath)).isFalse();
  }

  @Test
  void shouldNotAddDuplicatedValidators(final @TempDir Path tempDir)
      throws IOException, URISyntaxException {
    final DataDirLayout dataDirLayout =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    createMutableDirectoryStructure(dataDirLayout);
    final KeyStoreData keyStoreData = generateKeystore();

    final String validatorFileName = keyStoreData.getPubkey().toUnprefixedHexString().toLowerCase();
    final Path validatorPath =
        ValidatorClientService.getAlterableKeystorePath(dataDirLayout)
            .resolve(validatorFileName + ".json");
    final Path validatorPasswordPath =
        ValidatorClientService.getAlterableKeystorePasswordPath(dataDirLayout)
            .resolve(validatorFileName + ".txt");

    assertThat(Files.exists(validatorPath)).isFalse();
    assertThat(Files.exists(validatorPasswordPath)).isFalse();

    Files.writeString(validatorPasswordPath, EXPECTED_PASSWORD);
    KeyStoreLoader.saveToFile(validatorPath, keyStoreData);

    assertThat(Files.exists(validatorPath)).isTrue();
    assertThat(Files.exists(validatorPasswordPath)).isTrue();

    final MutableValidatorSource validatorSource =
        new MutableValidatorSource(
            spec, true, keystoreLocker, asyncRunner, dataDirLayout, localValidatorSource);
    assertThat(validatorSource.canAddValidator()).isTrue();

    Map<PostKeyResult, Optional<Signer>> resultValidator =
        validatorSource.addValidator(keyStoreData, EXPECTED_PASSWORD);

    assertThat(resultValidator.keySet().stream().findFirst().get().getImportStatus())
        .isEqualTo(ImportStatus.DUPLICATE);
    assertThat(resultValidator.values().stream().findFirst().get()).isEmpty();

    assertThat(Files.exists(validatorPath)).isTrue();
    assertThat(Files.exists(validatorPasswordPath)).isTrue();
  }

  private KeyStoreData generateKeystore() throws URISyntaxException, IOException {
    final URL resource = Resources.getResource("testKeystore.json");
    KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(Path.of(resource.toURI()));
    return keyStoreData;
  }

  private void createMutableDirectoryStructure(DataDirLayout tempDir) throws IOException {
    Files.createDirectory(tempDir.getValidatorDataDirectory());
    Files.createDirectory(ValidatorClientService.getAlterableKeystorePath(tempDir));
    Files.createDirectory(ValidatorClientService.getAlterableKeystorePasswordPath(tempDir));
  }
}
