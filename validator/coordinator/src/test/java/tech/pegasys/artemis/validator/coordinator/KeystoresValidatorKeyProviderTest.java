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

package tech.pegasys.artemis.validator.coordinator;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.writeString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.validator.coordinator.KeystoresValidatorKeyProvider.KEY_LENGTH;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSecretKey;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

class KeystoresValidatorKeyProviderTest {
  private static final String EXPECTED_PASSWORD = "testpassword";
  private final ArtemisConfiguration config = mock(ArtemisConfiguration.class);
  private final KeystoresValidatorKeyProvider keystoresValidatorKeyProvider =
      new KeystoresValidatorKeyProvider();
  private static final Bytes BLS_PRIVATE_KEY =
      Bytes.fromHexString(
          "0x000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f", KEY_LENGTH);
  private static final BLSKeyPair EXPECTED_BLS_KEY_PAIR =
      new BLSKeyPair(BLSSecretKey.fromBytes(BLS_PRIVATE_KEY));

  @Test
  void shouldLoadKeysFromKeyStores(@TempDir final Path tempDir) throws IOException {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").getPath());
    final Path pbkdf2Keystore = Path.of(Resources.getResource("pbkdf2TestVector.json").getPath());

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");
    writeString(tempPasswordFile, EXPECTED_PASSWORD);

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(
            Pair.of(scryptKeystore, tempPasswordFile), Pair.of(pbkdf2Keystore, tempPasswordFile));

    when(config.getValidatorKeystorePasswordFilePairs()).thenReturn(keystorePasswordFilePairs);

    final List<BLSKeyPair> blsKeyPairs = keystoresValidatorKeyProvider.loadValidatorKeys(config);

    // since both test vectors encrypted same private key, we should get 1 element
    Assertions.assertThat(blsKeyPairs).containsExactly(EXPECTED_BLS_KEY_PAIR);
  }

  @Test
  void emptyPasswordFileThrowsError(@TempDir final Path tempDir) throws IOException {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").getPath());
    final Path pbkdf2Keystore = Path.of(Resources.getResource("pbkdf2TestVector.json").getPath());

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(
            Pair.of(scryptKeystore, tempPasswordFile), Pair.of(pbkdf2Keystore, tempPasswordFile));

    when(config.getValidatorKeystorePasswordFilePairs()).thenReturn(keystorePasswordFilePairs);

    Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> keystoresValidatorKeyProvider.loadValidatorKeys(config))
        .withMessage("Keystore password cannot be empty: " + tempPasswordFile);
  }

  @Test
  void invalidPasswordThrowsError(@TempDir final Path tempDir) throws IOException {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").getPath());
    final Path pbkdf2Keystore = Path.of(Resources.getResource("pbkdf2TestVector.json").getPath());

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");
    writeString(tempPasswordFile, "invalidpassword");

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(
            Pair.of(scryptKeystore, tempPasswordFile), Pair.of(pbkdf2Keystore, tempPasswordFile));

    when(config.getValidatorKeystorePasswordFilePairs()).thenReturn(keystorePasswordFilePairs);

    Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> keystoresValidatorKeyProvider.loadValidatorKeys(config))
        .withMessage("Invalid keystore password: " + scryptKeystore);
  }

  @Test
  void nonExistentPasswordFileThrowsError(@TempDir final Path tempDir) {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").getPath());
    final Path pbkdf2Keystore = Path.of(Resources.getResource("pbkdf2TestVector.json").getPath());

    // create password file
    final Path tempPasswordFile = tempDir.resolve("nonexistent.txt");

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(
            Pair.of(scryptKeystore, tempPasswordFile), Pair.of(pbkdf2Keystore, tempPasswordFile));

    when(config.getValidatorKeystorePasswordFilePairs()).thenReturn(keystorePasswordFilePairs);

    Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> keystoresValidatorKeyProvider.loadValidatorKeys(config))
        .withMessage("Keystore password file not found: " + tempPasswordFile);
  }

  @Test
  void nonExistentKeystoreFileThrowsError(@TempDir final Path tempDir) throws IOException {
    // load keystores from resources
    final Path scryptKeystore = tempDir.resolve("scryptTestVector.json");
    final Path pbkdf2Keystore = tempDir.resolve("pbkdf2TestVector.json");

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");
    writeString(tempPasswordFile, EXPECTED_PASSWORD);

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(
            Pair.of(scryptKeystore, tempPasswordFile), Pair.of(pbkdf2Keystore, tempPasswordFile));

    when(config.getValidatorKeystorePasswordFilePairs()).thenReturn(keystorePasswordFilePairs);

    Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> keystoresValidatorKeyProvider.loadValidatorKeys(config))
        .withMessage("KeyStore file not found: " + scryptKeystore);
  }
}
