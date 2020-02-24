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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.artemis.bls.keystore.model.CipherFunction.AES_128_CTR;
import static tech.pegasys.artemis.bls.keystore.model.CryptoFunction.PBKDF2;
import static tech.pegasys.artemis.bls.keystore.model.CryptoFunction.SCRYPT;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.io.Resources;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.bls.keystore.builder.CipherBuilder;
import tech.pegasys.artemis.bls.keystore.builder.CipherParamBuilder;
import tech.pegasys.artemis.bls.keystore.builder.Pbkdf2ParamBuilder;
import tech.pegasys.artemis.bls.keystore.builder.SCryptParamBuilder;
import tech.pegasys.artemis.bls.keystore.model.ChecksumFunction;
import tech.pegasys.artemis.bls.keystore.model.Cipher;
import tech.pegasys.artemis.bls.keystore.model.CipherParam;
import tech.pegasys.artemis.bls.keystore.model.Crypto;
import tech.pegasys.artemis.bls.keystore.model.CryptoFunction;
import tech.pegasys.artemis.bls.keystore.model.KdfParam;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.artemis.bls.keystore.model.SCryptParam;

class KeyStoreTest {
  private static final String PASSWORD = "testpassword";
  private static final Bytes BLS_PRIVATE_KEY =
      Bytes.fromHexString("0x000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");
  private static final Bytes32 SALT =
      Bytes32.fromHexString("d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");
  private static final Bytes AES_IV_PARAM = Bytes.fromHexString("264daa3f303d7259501c93d997d84fe6");

  private static final String scryptTestVectorJsonResource = "scryptTestVector.json";
  private static final String pbkdf2TestVectorJsonResource = "pbkdf2TestVector.json";
  private static final String missingKdfParamTestVectorJsonResource =
      "missingKdfSectionTestVector.json";
  private static final String unsupportedChecksumFunctionJsonResource =
      "unsupportedChecksumFunction.json";

  @SuppressWarnings("UnusedMethod")
  private static Stream<Arguments> keystorePaths() {
    try {
      final Path scryptTestVectorPath =
          Paths.get(Resources.getResource(scryptTestVectorJsonResource).toURI());
      final Path pbkdf2TestVectorPath =
          Paths.get(Resources.getResource(pbkdf2TestVectorJsonResource).toURI());
      return Stream.of(Arguments.of(scryptTestVectorPath), Arguments.of(pbkdf2TestVectorPath));
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("UnusedMethod")
  private static Stream<Arguments> invalidKeyStorePaths() {
    try {
      final Path scryptTestVectorPath =
          Paths.get(Resources.getResource(missingKdfParamTestVectorJsonResource).toURI());
      final Path pbkdf2TestVectorPath =
          Paths.get(Resources.getResource(unsupportedChecksumFunctionJsonResource).toURI());
      return Stream.of(Arguments.of(scryptTestVectorPath), Arguments.of(pbkdf2TestVectorPath));
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("UnusedMethod")
  private static Stream<Arguments> kdfParams() {
    // KdfParam, expected checksum, expected encrypted cipher message
    return Stream.of(
        Arguments.of(
            SCryptParamBuilder.aSCryptParam().withSalt(SALT).build(),
            Bytes.fromHexString("149aafa27b041f3523c53d7acba1905fa6b1c90f9fef137568101f44b531a3cb"),
            Bytes.fromHexString(
                "54ecc8863c0550351eee5720f3be6a5d4a016025aa91cd6436cfec938d6a8d30")),
        Arguments.of(
            Pbkdf2ParamBuilder.aPbkdf2Param().withSalt(SALT).build(),
            Bytes.fromHexString("18b148af8e52920318084560fd766f9d09587b4915258dec0676cba5b0da09d8"),
            Bytes.fromHexString(
                "a9249e0ca7315836356e4c7440361ff22b9fe71e2e2ed34fc1eb03976924ed48")));
  }

  @SuppressWarnings("UnusedMethod")
  private static Stream<Arguments> basicKdfParam() {
    return Stream.of(
        Arguments.of(
            SCryptParamBuilder.aSCryptParam().build(), CipherParamBuilder.aCipherParam().build()),
        Arguments.of(
            Pbkdf2ParamBuilder.aPbkdf2Param().build(), CipherParamBuilder.aCipherParam().build()));
  }

  @ParameterizedTest
  @MethodSource("keystorePaths")
  void loadKeyStoreAndDecryptKey(final Path keyStorePath) throws Exception {
    final KeyStore keyStore = KeyStoreLoader.loadFromFile(keyStorePath);
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
    assertTrue(keyStore.validatePassword(PASSWORD));
    assertFalse(keyStore.validatePassword("test"));

    final Bytes decryptedKey = keyStore.decrypt(PASSWORD);
    assertEquals(BLS_PRIVATE_KEY, decryptedKey);
  }

  @ParameterizedTest
  @MethodSource("invalidKeyStorePaths")
  void loadingKeyStoreWithInvalidKdfParamsThrowsException(final Path invalidJsonPath) {
    assertThrows(JsonMappingException.class, () -> KeyStoreLoader.loadFromFile(invalidJsonPath));
  }

  @ParameterizedTest
  @MethodSource("kdfParams")
  void encryptWithKdfAndCipherFunction(
      final KdfParam kdfParam, final Bytes expectedChecksum, final Bytes encryptedCipherMessage) {
    final CipherParam cipherParam = CipherParamBuilder.aCipherParam().withIv(AES_IV_PARAM).build();
    final Cipher cipher =
        CipherBuilder.aCipher()
            .withCipherParam(cipherParam)
            .withCipherFunction(AES_128_CTR)
            .build();
    final Crypto crypto =
        KeyStore.encryptUsingCipherFunction(
            BLS_PRIVATE_KEY, PASSWORD, kdfParam, cipher, ChecksumFunction.SHA256);

    assertEquals(expectedChecksum, crypto.getChecksum().getMessage());
    assertEquals(encryptedCipherMessage, crypto.getCipher().getMessage());
  }

  @ParameterizedTest
  @MethodSource("basicKdfParam")
  void encryptWithRandomSaltAndIv(final KdfParam kdfParam, final CipherParam cipherParam) {
    final Cipher cipher =
        CipherBuilder.aCipher()
            .withCipherParam(cipherParam)
            .withCipherFunction(AES_128_CTR)
            .build();
    final KeyStore encryptedKeyStore =
        KeyStore.encrypt(BLS_PRIVATE_KEY, "test", "", kdfParam, cipher);
    assertTrue(encryptedKeyStore.validatePassword("test"));
    final Bytes decryptedKey = encryptedKeyStore.decrypt("test");
    assertEquals(BLS_PRIVATE_KEY, decryptedKey);
  }

  @ParameterizedTest
  @EnumSource(CryptoFunction.class)
  void encryptLargeDataWithRandomSaltAndIv(final CryptoFunction cryptoFunction) {
    final KdfParam kdfParam =
        cryptoFunction == SCRYPT
            ? SCryptParamBuilder.aSCryptParam().build()
            : Pbkdf2ParamBuilder.aPbkdf2Param().build();
    final CipherParam cipherParam = CipherParamBuilder.aCipherParam().build();
    final Cipher cipher =
        CipherBuilder.aCipher()
            .withCipherParam(cipherParam)
            .withCipherFunction(AES_128_CTR)
            .build();
    final Bytes largeData = Bytes.random(1024);
    final Crypto crypto =
        KeyStore.encryptUsingCipherFunction(
            largeData, PASSWORD, kdfParam, cipher, ChecksumFunction.SHA256);
    assertEquals(32, crypto.getChecksum().getMessage().size());
  }

  @ParameterizedTest
  @EnumSource(CryptoFunction.class)
  void encryptSmallDataWithRandomSaltAndIv(final CryptoFunction cryptoFunction) {
    final KdfParam kdfParam =
        cryptoFunction == SCRYPT
            ? SCryptParamBuilder.aSCryptParam().build()
            : Pbkdf2ParamBuilder.aPbkdf2Param().build();
    final CipherParam cipherParam = CipherParamBuilder.aCipherParam().build();
    final Cipher cipher =
        CipherBuilder.aCipher()
            .withCipherParam(cipherParam)
            .withCipherFunction(AES_128_CTR)
            .build();
    final Bytes smallData = Bytes.random(8);
    final Crypto crypto =
        KeyStore.encryptUsingCipherFunction(
            smallData, PASSWORD, kdfParam, cipher, ChecksumFunction.SHA256);
    assertEquals(32, crypto.getChecksum().getMessage().size());
  }

  @ParameterizedTest
  @EnumSource(CryptoFunction.class)
  void checksumFunctionSha512(final CryptoFunction cryptoFunction) {
    final KdfParam kdfParam =
        cryptoFunction == SCRYPT
            ? SCryptParamBuilder.aSCryptParam().build()
            : Pbkdf2ParamBuilder.aPbkdf2Param().build();
    final CipherParam cipherParam = CipherParamBuilder.aCipherParam().build();
    final Cipher cipher =
        CipherBuilder.aCipher()
            .withCipherParam(cipherParam)
            .withCipherFunction(AES_128_CTR)
            .build();
    final Bytes smallData = Bytes.random(8);
    final Crypto crypto =
        KeyStore.encryptUsingCipherFunction(
            smallData, PASSWORD, kdfParam, cipher, ChecksumFunction.SHA512);
    assertEquals(64, crypto.getChecksum().getMessage().size());
  }

  @ParameterizedTest
  @EnumSource(CryptoFunction.class)
  void encryptionWithInvalidDkLenShouldFail(final CryptoFunction cryptoFunction) {
    final KdfParam kdfParam =
        cryptoFunction == SCRYPT
            ? SCryptParamBuilder.aSCryptParam().withDklen(15).build()
            : Pbkdf2ParamBuilder.aPbkdf2Param().withDklen(15).build();
    final CipherParam cipherParam = CipherParamBuilder.aCipherParam().build();
    final Cipher cipher =
        CipherBuilder.aCipher()
            .withCipherParam(cipherParam)
            .withCipherFunction(AES_128_CTR)
            .build();
    final Bytes data = Bytes.random(8);
    final IllegalArgumentException illegalArgumentException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                KeyStore.encryptUsingCipherFunction(
                    data, PASSWORD, kdfParam, cipher, ChecksumFunction.SHA256));
    assertEquals(
        "aes-128-ctr requires kdf dklen greater than 16", illegalArgumentException.getMessage());
  }
}
