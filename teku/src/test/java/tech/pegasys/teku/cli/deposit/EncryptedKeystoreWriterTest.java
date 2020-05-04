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

package tech.pegasys.teku.cli.deposit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;

class EncryptedKeystoreWriterTest {
  private static final BLSSecretKey validator1SecretKey =
      BLSSecretKey.fromBytes(
          Bytes.fromHexString(
              "0x000000000000000000000000000000002CF622DE0FD92C7D4E59539CBDA63100E02CF59349595356CD97FFE6CB486460"));
  private static final BLSSecretKey withdrawal1SecretKey =
      BLSSecretKey.fromBytes(
          Bytes.fromHexString(
              "0x000000000000000000000000000000006EA631AA885EC84AFA60BBD7887B5DBC91F594DEA29334E99576B51FAAD0E453"));
  private static final BLSSecretKey validator2SecretKey =
      BLSSecretKey.fromBytes(
          Bytes.fromHexString(
              "0x00000000000000000000000000000000147599AA450AADF69988F20FF1ADB3A3BF31BF9CDC77CF492FF95667708D8E79"));
  private static final BLSSecretKey withdrawal2SecretKey =
      BLSSecretKey.fromBytes(
          Bytes.fromHexString(
              "0x000000000000000000000000000000000610B84CD68FB0FAB2F04A2A05EE01CD5F7374EB8EA93E26DB9C61DD2704B5BD"));
  private static final String validator1PubKey = new BLSPublicKey(validator1SecretKey).toString();
  private static final String withdrawal1PubKey = new BLSPublicKey(withdrawal1SecretKey).toString();
  private static final String validator2PubKey = new BLSPublicKey(validator2SecretKey).toString();
  private static final String withdrawal2PubKey = new BLSPublicKey(withdrawal2SecretKey).toString();

  private static final String PASSWORD = "test123";

  @Test
  void keysAreWrittenToEncryptedKeystores(@TempDir final Path tempDir) {
    final KeysWriter keysWriter = new EncryptedKeystoreWriter(PASSWORD, PASSWORD, tempDir);
    keysWriter.writeKeys(new BLSKeyPair(validator1SecretKey), new BLSKeyPair(withdrawal1SecretKey));

    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve(
            "validator_"
                + trimPublicKey(validator1PubKey)
                + "/validator_"
                + trimPublicKey(validator1PubKey)
                + ".json"),
        validator1SecretKey);
    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve(
            "validator_"
                + trimPublicKey(validator1PubKey)
                + "/withdrawal_"
                + trimPublicKey(withdrawal1PubKey)
                + ".json"),
        withdrawal1SecretKey);

    keysWriter.writeKeys(new BLSKeyPair(validator2SecretKey), new BLSKeyPair(withdrawal2SecretKey));

    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve(
            "validator_"
                + trimPublicKey(validator2PubKey)
                + "/validator_"
                + trimPublicKey(validator2PubKey)
                + ".json"),
        validator2SecretKey);
    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve(
            "validator_"
                + trimPublicKey(validator2PubKey)
                + "/withdrawal_"
                + trimPublicKey(withdrawal2PubKey)
                + ".json"),
        withdrawal2SecretKey);
  }

  private void assertKeyStoreCreatedAndCanBeDecrypted(
      final Path keystorePath, final BLSSecretKey blsSecretKey) {
    final KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(keystorePath);
    assertThat(KeyStore.validatePassword(PASSWORD, keyStoreData)).isTrue();
    assertThat(KeyStore.decrypt(PASSWORD, keyStoreData))
        .isEqualTo(blsSecretKey.getSecretKey().toBytes());
  }

  private String trimPublicKey(final String publicKey) {
    if (publicKey.toLowerCase().startsWith("0x")) {
      return publicKey.substring(2, 9);
    }
    return publicKey.substring(0, 7);
  }
}
