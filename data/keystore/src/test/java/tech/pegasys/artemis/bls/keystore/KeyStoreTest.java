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

import com.google.common.io.Resources;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.bls.keystore.model.Cipher;
import tech.pegasys.artemis.bls.keystore.model.KdfParam;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.artemis.bls.keystore.model.SCryptParam;

class KeyStoreTest {
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
  private static final String missingKdfParamTestVectorJsonResource =
      "missingKdfSectionTestVector.json";
  private static final String unsupportedChecksumFunctionJsonResource =
      "unsupportedChecksumFunction.json";

  @SuppressWarnings("UnusedMethod")
  private static Stream<Arguments> encryptWithKdfAndCipherArguments() {
    // KdfParam, expected checksum, expected encrypted cipher message
    return Stream.of(
        Arguments.of(
            new SCryptParam(SALT),
            Bytes.fromHexString("149aafa27b041f3523c53d7acba1905fa6b1c90f9fef137568101f44b531a3cb"),
            Bytes.fromHexString(
                "54ecc8863c0550351eee5720f3be6a5d4a016025aa91cd6436cfec938d6a8d30")),
        Arguments.of(
            new Pbkdf2Param(SALT),
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
    final Cipher cipher = new Cipher(AES_IV_PARAM);
    final KeyStoreData keyStoreData =
        KeyStore.encrypt(BLS_PRIVATE_KEY, PASSWORD, "", kdfParam, cipher);
    assertThat(keyStoreData.getCrypto().getChecksum().getMessage()).isEqualTo(expectedChecksum);
    assertThat(keyStoreData.getCrypto().getCipher().getMessage()).isEqualTo(encryptedCipherMessage);
    assertThat(keyStoreData.getVersion()).isEqualTo(KeyStoreData.KEYSTORE_VERSION);
    assertThat(keyStoreData.getPubkey()).isEqualTo(BLS_PUB_KEY);
    assertThat(keyStoreData.getUuid()).isNotNull();
  }

  // TODO: Test invalid keystore version after custom exception
  // TODO: Test invalid json loading after custom exception
  // TODO: Test DKLEN after custom exception
}
