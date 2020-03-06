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

package tech.pegasys.artemis.deposit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.bls.keystore.KeyStore;
import tech.pegasys.artemis.bls.keystore.KeyStoreLoader;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;

class EncryptedKeystoreWriterTest {
  private static final SecretKey validator1SecretKey =
      SecretKey.fromBytes(
          Bytes.fromHexString(
              "0x000000000000000000000000000000002CF622DE0FD92C7D4E59539CBDA63100E02CF59349595356CD97FFE6CB486460"));
  private static final SecretKey withdrawal1SecretKey =
      SecretKey.fromBytes(
          Bytes.fromHexString(
              "0x000000000000000000000000000000006EA631AA885EC84AFA60BBD7887B5DBC91F594DEA29334E99576B51FAAD0E453"));
  private static final SecretKey validator2SecretKey =
      SecretKey.fromBytes(
          Bytes.fromHexString(
              "0x00000000000000000000000000000000147599AA450AADF69988F20FF1ADB3A3BF31BF9CDC77CF492FF95667708D8E79"));
  private static final SecretKey withdrawal2SecretKey =
      SecretKey.fromBytes(
          Bytes.fromHexString(
              "0x000000000000000000000000000000000610B84CD68FB0FAB2F04A2A05EE01CD5F7374EB8EA93E26DB9C61DD2704B5BD"));

  private static final String PASSWORD = "test123";

  @Test
  void keysAreWrittenToEncryptedKeystores(@TempDir final Path tempDir) {
    final KeysWriter keysWriter = new EncryptedKeystoreWriter(PASSWORD, PASSWORD, tempDir);
    keysWriter.writeKeys(
        new BLSKeyPair(new KeyPair(validator1SecretKey)),
        new BLSKeyPair(new KeyPair(withdrawal1SecretKey)));

    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve("validator_1/validator_keystore.json"), validator1SecretKey);
    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve("validator_1/withdrawal_keystore.json"), withdrawal1SecretKey);

    keysWriter.writeKeys(
        new BLSKeyPair(new KeyPair(validator2SecretKey)),
        new BLSKeyPair(new KeyPair(withdrawal2SecretKey)));

    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve("validator_2/validator_keystore.json"), validator2SecretKey);
    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve("validator_2/withdrawal_keystore.json"), withdrawal2SecretKey);
  }

  private void assertKeyStoreCreatedAndCanBeDecrypted(
      final Path keystorePath, final SecretKey blsSecretKey) {
    final KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(keystorePath);
    assertThat(KeyStore.validatePassword(PASSWORD, keyStoreData)).isTrue();
    assertThat(KeyStore.decrypt(PASSWORD, keyStoreData)).isEqualTo(blsSecretKey.toBytes());
  }
}
