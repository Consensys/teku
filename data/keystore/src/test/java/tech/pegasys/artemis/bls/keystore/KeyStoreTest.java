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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.bls.keystore.model.Cipher;
import tech.pegasys.artemis.bls.keystore.model.KdfParam;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.artemis.bls.keystore.model.Pbkdf2PseudoRandomFunction;
import tech.pegasys.artemis.bls.keystore.model.SCryptParam;

class KeyStoreTest {
  private static final int DKLEN = 32;
  private static final int ITERATIVE_COUNT = 262144;
  private static final int MEMORY_CPU_COST = 262144;
  private static final int PARALLELIZATION = 1;
  private static final int BLOCKSIZE = 8;

  private static final String PASSWORD = "testpassword";
  private static final Bytes BLS_PRIVATE_KEY =
      Bytes.fromHexString("0x000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");
  private static final Bytes BLS_PUB_KEY =
      Bytes.fromHexString(
          "9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07");
  private static final Bytes32 SALT =
      Bytes32.fromHexString("d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");
  private static final Bytes AES_IV_PARAM = Bytes.fromHexString("264daa3f303d7259501c93d997d84fe6");

  private static final String SCRYPT_KEYSTORE_RESOURCE = "scryptTestVector.json";
  private static final String PBKDF2_KEYSTORE_RESOURCE = "pbkdf2TestVector.json";
  private static final String MISSING_SECTION_KEYSTORE_RESOURCE =
      "missingKdfSectionTestVector.json";
  private static final String UNSUPPORTED_VERSION_JSON_RESOURCE = "v3TestVector.json";
  private static final String UNSUPPORTED_CHECKSUM_FUNCTION_JSON =
      "unsupportedChecksumFunction.json";
  private static final String UNSUPPORTED_CIPHER_FUNCTION_JSON = "unsupportedCipherFunction.json";
  private static final String UNSUPPORTED_KDF_FUNCTION_JSON = "unsupportedKdfFunction.json";
  private static final String UNSUPPORTED_PKKDF2_PRF_FUNCTION_JSON = "unsupportedPBKDF2Prf.json";
  private static final String UNSUPPORTED_DKLEN_FUNCTION_JSON = "unsupportedDkLen.json";
  private static final Cipher CIPHER = new Cipher(AES_IV_PARAM);

  @SuppressWarnings("UnusedMethod")
  private static Stream<Arguments> encryptWithKdfAndCipherArguments() {
    // KdfParam, expected checksum, expected encrypted cipher message
    return Stream.of(
        Arguments.of(
            new SCryptParam(DKLEN, MEMORY_CPU_COST, PARALLELIZATION, BLOCKSIZE, SALT),
            Bytes.fromHexString("149aafa27b041f3523c53d7acba1905fa6b1c90f9fef137568101f44b531a3cb"),
            Bytes.fromHexString(
                "54ecc8863c0550351eee5720f3be6a5d4a016025aa91cd6436cfec938d6a8d30")),
        Arguments.of(
            new Pbkdf2Param(DKLEN, ITERATIVE_COUNT, Pbkdf2PseudoRandomFunction.HMAC_SHA256, SALT),
            Bytes.fromHexString("18b148af8e52920318084560fd766f9d09587b4915258dec0676cba5b0da09d8"),
            Bytes.fromHexString(
                "a9249e0ca7315836356e4c7440361ff22b9fe71e2e2ed34fc1eb03976924ed48")));
  }

  @Test
  void loadSCryptKeyStoreAndDecryptKey() {
    loadKeyStoreAndDecryptKey(SCRYPT_KEYSTORE_RESOURCE);
  }

  @Test
  void loadPBKDF2KeyStoreAndDecryptKey() {
    loadKeyStoreAndDecryptKey(PBKDF2_KEYSTORE_RESOURCE);
  }

  @Test
  void loadSCryptKeyStoreAndTestInvalidPassword() {
    invalidPasswordValidation(SCRYPT_KEYSTORE_RESOURCE);
  }

  @Test
  void loadPBKDF2KeyStoreAndTestInvalidPassword() {
    invalidPasswordValidation(PBKDF2_KEYSTORE_RESOURCE);
  }

  private void loadKeyStoreAndDecryptKey(final String resourcePath) {
    final KeyStoreData keyStoreData = loadKeyStoreFromResource(resourcePath);
    final Bytes decryptedBlsPrivateKey = KeyStore.decrypt(PASSWORD, keyStoreData);
    assertThat(decryptedBlsPrivateKey).isEqualTo(BLS_PRIVATE_KEY);
  }

  private void invalidPasswordValidation(final String resourcePath) {
    final KeyStoreData keyStoreData = loadKeyStoreFromResource(resourcePath);
    assertThat(KeyStore.validatePassword("invalidpassword", keyStoreData)).isFalse();
  }

  private KeyStoreData loadKeyStoreFromResource(final String resourcePath) {
    final Path testKeyStorePath = Path.of(Resources.getResource(resourcePath).getPath());
    return KeyStoreLoader.loadFromFile(testKeyStorePath);
  }

  @ParameterizedTest
  @MethodSource("encryptWithKdfAndCipherArguments")
  void encryptWithKdfAndCipherFunction(
      final KdfParam kdfParam, final Bytes expectedChecksum, final Bytes encryptedCipherMessage) {
    final KeyStoreData keyStoreData =
        KeyStore.encrypt(BLS_PRIVATE_KEY, PASSWORD, "", kdfParam, CIPHER);
    assertThat(keyStoreData.getCrypto().getChecksum().getMessage()).isEqualTo(expectedChecksum);
    assertThat(keyStoreData.getCrypto().getCipher().getMessage()).isEqualTo(encryptedCipherMessage);
    assertThat(keyStoreData.getVersion()).isEqualTo(KeyStoreData.KEYSTORE_VERSION);
    assertThat(keyStoreData.getPubkey()).isEqualTo(BLS_PUB_KEY);
    assertThat(keyStoreData.getUuid()).isNotNull();
  }

  @Test
  void invalidJsonLoadingThrowsException() {
    Assertions.assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(() -> loadKeyStoreFromResource(MISSING_SECTION_KEYSTORE_RESOURCE))
        .withMessageStartingWith(
            "Invalid KeyStore: Missing property 'params' for external type id 'function'");
  }

  @Test
  void unsupportedVersionThrowsException() {
    Assertions.assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(() -> loadKeyStoreFromResource(UNSUPPORTED_VERSION_JSON_RESOURCE))
        .withMessage("The KeyStore version 3 is not supported");
  }

  @Test
  void unsupportedChecksumFunctionThrowsException() {
    Assertions.assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(() -> loadKeyStoreFromResource(UNSUPPORTED_CHECKSUM_FUNCTION_JSON))
        .withMessage("Checksum function [sha128] is not supported.");
  }

  @Test
  void unsupportedCipherFunctionThrowsException() {
    Assertions.assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(() -> loadKeyStoreFromResource(UNSUPPORTED_CIPHER_FUNCTION_JSON))
        .withMessage("Cipher function [aes-256-ctr] is not supported.");
  }

  @Test
  void unsupportedKdfFunctionThrowsException() {
    Assertions.assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(() -> loadKeyStoreFromResource(UNSUPPORTED_KDF_FUNCTION_JSON))
        .withMessage("Kdf function [pbkdf3] is not supported.");
  }

  @Test
  void unsupportedPBKDF2PrfFunctionThrowsException() {
    Assertions.assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(() -> loadKeyStoreFromResource(UNSUPPORTED_PKKDF2_PRF_FUNCTION_JSON))
        .withMessage("PBKDF2 pseudorandom function (prf) [hmac-sha512] is not supported.");
  }

  @Test
  void unsupportedDkLenThrowsException() {
    Assertions.assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(() -> loadKeyStoreFromResource(UNSUPPORTED_DKLEN_FUNCTION_JSON))
        .withMessage("Generated key length parameter dklen must be >= 32.");
  }

  @Test
  void encryptUsingSCryptAndSaveKeyStore(@TempDir final Path tempDir) throws IOException {
    final KdfParam kdfParam =
        new SCryptParam(DKLEN, MEMORY_CPU_COST, PARALLELIZATION, BLOCKSIZE, SALT);
    encryptSaveAndReloadKeyStore(tempDir, kdfParam);
  }

  @Test
  void encryptUsingPBKDF2AndSaveKeyStore(@TempDir final Path tempDir) throws IOException {
    final KdfParam kdfParam =
        new Pbkdf2Param(DKLEN, ITERATIVE_COUNT, Pbkdf2PseudoRandomFunction.HMAC_SHA256, SALT);
    encryptSaveAndReloadKeyStore(tempDir, kdfParam);
  }

  private void encryptSaveAndReloadKeyStore(final Path tempDir, final KdfParam kdfParam)
      throws IOException {
    final KeyStoreData keyStoreData =
        KeyStore.encrypt(BLS_PRIVATE_KEY, PASSWORD, "", kdfParam, CIPHER);
    final Path tempKeyStoreFile = Files.createTempFile(tempDir, "keystore", ".json");
    assertThatCode(() -> KeyStoreLoader.saveToFile(tempKeyStoreFile, keyStoreData))
        .doesNotThrowAnyException();

    // reload it back
    final KeyStoreData loadedKeyStore = KeyStoreLoader.loadFromFile(tempKeyStoreFile);
    assertThat(loadedKeyStore.getUuid()).isEqualByComparingTo(keyStoreData.getUuid());
    assertThat(loadedKeyStore.getCrypto().getChecksum().getMessage())
        .isEqualTo(keyStoreData.getCrypto().getChecksum().getMessage());
  }
}
