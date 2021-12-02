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

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.core.signatures.SigningRootUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.ssz.type.Bytes4;
import tech.pegasys.teku.validator.api.KeyStoreFilesLocator;
import tech.pegasys.teku.validator.client.loader.ValidatorSource.ValidatorProvider;

class LocalValidatorSourceTest {

  private static final String EXPECTED_PASSWORD = "testpassword";
  private static final Bytes32 BLS_PRIVATE_KEY =
      Bytes32.fromHexString("0x000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");
  private static final BLSKeyPair EXPECTED_BLS_KEY_PAIR =
      new BLSKeyPair(BLSSecretKey.fromBytes(BLS_PRIVATE_KEY));

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final KeystoreLocker keystoreLocker = mock(KeystoreLocker.class);
  private final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
  private final KeyStoreFilesLocator keyStoreFilesLocator = mock(KeyStoreFilesLocator.class);

  private final LocalValidatorSource validatorSource =
      new LocalValidatorSource(spec, true, keystoreLocker, keyStoreFilesLocator, asyncRunner, true);

  @Test
  void shouldLoadKeysFromKeyStores(@TempDir final Path tempDir) throws Exception {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").toURI());
    final Path pbkdf2Keystore = Path.of(Resources.getResource("pbkdf2TestVector.json").toURI());

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");
    writeString(tempPasswordFile, EXPECTED_PASSWORD);

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(
            Pair.of(scryptKeystore, tempPasswordFile), Pair.of(pbkdf2Keystore, tempPasswordFile));

    when(keyStoreFilesLocator.parse()).thenReturn(keystorePasswordFilePairs);

    final List<ValidatorProvider> availableValidators = validatorSource.getAvailableValidators();
    assertThat(availableValidators).hasSize(2);
    // Both keystores encyrpt the same key.
    assertProviderMatchesKey(availableValidators.get(0), EXPECTED_BLS_KEY_PAIR);
    assertProviderMatchesKey(availableValidators.get(1), EXPECTED_BLS_KEY_PAIR);
  }

  @Test
  void shouldThrowExceptionWhenPasswordFileIsEmpty(@TempDir final Path tempDir) throws Exception {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").toURI());

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(Pair.of(scryptKeystore, tempPasswordFile));

    when(keyStoreFilesLocator.parse()).thenReturn(keystorePasswordFilePairs);

    assertThatThrownBy(validatorSource::getAvailableValidators)
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Keystore password cannot be empty: " + tempPasswordFile);
  }

  @Test
  void shouldThrowExceptionWhenPasswordIsIncorrect(@TempDir final Path tempDir) throws Exception {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").toURI());

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");
    writeString(tempPasswordFile, "invalidpassword");

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(Pair.of(scryptKeystore, tempPasswordFile));

    when(keyStoreFilesLocator.parse()).thenReturn(keystorePasswordFilePairs);

    assertThatThrownBy(() -> validatorSource.getAvailableValidators().get(0).createSigner())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage(
            "Failed to decrypt keystore " + scryptKeystore + ". Check the password is correct.");
  }

  @Test
  void shouldThrowExceptionWhenPasswordFileDoesNotExist(@TempDir final Path tempDir)
      throws Exception {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").toURI());

    // create password file
    final Path tempPasswordFile = tempDir.resolve("nonexistent.txt");

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(Pair.of(scryptKeystore, tempPasswordFile));

    when(keyStoreFilesLocator.parse()).thenReturn(keystorePasswordFilePairs);

    assertThatThrownBy(validatorSource::getAvailableValidators)
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Keystore password file not found: " + tempPasswordFile);
  }

  @Test
  void shouldThrowExceptionWhenKeystoreFileDoesNotExist(@TempDir final Path tempDir)
      throws IOException {
    // load keystores from resources
    final Path scryptKeystore = tempDir.resolve("scryptTestVector.json");

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");
    writeString(tempPasswordFile, EXPECTED_PASSWORD);

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(Pair.of(scryptKeystore, tempPasswordFile));

    when(keyStoreFilesLocator.parse()).thenReturn(keystorePasswordFilePairs);

    assertThatThrownBy(validatorSource::getAvailableValidators)
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("KeyStore file not found: " + scryptKeystore);
  }

  private void assertProviderMatchesKey(
      final ValidatorProvider provider, final BLSKeyPair expectedKeyPair) {
    assertThat(provider.getPublicKey()).isEqualTo(expectedKeyPair.getPublicKey());
    final Signer signer = provider.createSigner();
    final Bytes4 version = Bytes4.fromHexString("0x00000000");
    final UInt64 epoch = UInt64.ZERO;
    final ForkInfo forkInfo = new ForkInfo(new Fork(version, version, UInt64.ZERO), Bytes32.ZERO);
    final Bytes signingRoot = signingRootUtil.signingRootForRandaoReveal(epoch, forkInfo);

    final SafeFuture<BLSSignature> signingFuture = signer.createRandaoReveal(epoch, forkInfo);
    asyncRunner.executeQueuedActions();
    assertThat(signingFuture).isCompleted();
    final BLSSignature signature = signingFuture.getNow(null);
    assertThat(BLS.verify(expectedKeyPair.getPublicKey(), signingRoot, signature)).isTrue();
  }

  @Test
  void shouldThrowExceptionWhenAddValidator() {
    assertThatThrownBy(() -> validatorSource.addValidator(null, "pass"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldSayFalseToAddValidators() {
    assertThat(validatorSource.canAddValidator()).isFalse();
  }
}
