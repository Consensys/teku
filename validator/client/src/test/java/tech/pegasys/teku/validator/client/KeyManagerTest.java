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

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;

class KeyManagerTest {

  final ValidatorLoader validatorLoader = Mockito.mock(ValidatorLoader.class);
  final OwnedValidators ownedValidators = Mockito.mock(OwnedValidators.class);

  @Test
  void shouldReturnKeyList() {
    final KeyManager keyManager = new KeyManager(validatorLoader);
    Set<BLSPublicKey> keySet = getList();
    when(ownedValidators.getPublicKeys()).thenReturn(keySet);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    Set<BLSPublicKey> receivedKeySet = keyManager.getValidatorKeys();

    assertThat(receivedKeySet).isEqualTo(keySet);
  }

  @Test
  void shouldReturnEmptyKeyList() {
    final KeyManager keyManager = new KeyManager(validatorLoader);
    Set<BLSPublicKey> keySet = Collections.emptySet();
    when(ownedValidators.getPublicKeys()).thenReturn(keySet);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    Set<BLSPublicKey> receivedKeySet = keyManager.getValidatorKeys();

    assertThat(receivedKeySet).isEmpty();
  }

  @Test
  void shouldDecryptReceivedKeystoreFile(@TempDir final Path tempDir)
      throws IOException, URISyntaxException {
    final Path pbkdf2Keystore = Path.of(Resources.getResource("pbkdf2TestVector.json").toURI());
    KeyStoreData receivedKeyStoreFromRest = KeyStoreLoader.loadFromFile(pbkdf2Keystore);
    KeyStoreData encryptedKeystore =
        KeyStore.encrypt(
            receivedKeyStoreFromRest.getPubkey(),
            receivedKeyStoreFromRest.getPubkey(),
            "testpassword",
            receivedKeyStoreFromRest.getPath(),
            receivedKeyStoreFromRest.getCrypto().getKdf().getParam(),
            receivedKeyStoreFromRest.getCrypto().getCipher());

    final KeyManager keyManager = new KeyManager(validatorLoader);
    Path receivedKeystoreFile = Files.createTempFile(tempDir, "newKeystore", "json");
    KeyStoreLoader.saveToFile(receivedKeystoreFile, encryptedKeystore);
    boolean isValid = keyManager.isValidKeystoreFile(receivedKeystoreFile, "testpassword");
    
    assertThat(isValid).isTrue();
  }

  @Test
  void shouldNotDecryptReceivedKeystoreFileWithWrongPass(@TempDir final Path tempDir)
      throws IOException, URISyntaxException {
    final Path pbkdf2Keystore = Path.of(Resources.getResource("pbkdf2TestVector.json").toURI());
    KeyStoreData receivedKeyStoreFromRest = KeyStoreLoader.loadFromFile(pbkdf2Keystore);
    KeyStoreData encryptedKeystore =
        KeyStore.encrypt(
            receivedKeyStoreFromRest.getPubkey(),
            receivedKeyStoreFromRest.getPubkey(),
            "testpassword",
            receivedKeyStoreFromRest.getPath(),
            receivedKeyStoreFromRest.getCrypto().getKdf().getParam(),
            receivedKeyStoreFromRest.getCrypto().getCipher());

    final KeyManager keyManager = new KeyManager(validatorLoader);
    Path receivedKeystoreFile = Files.createTempFile(tempDir, "newKeystore", "json");
    KeyStoreLoader.saveToFile(receivedKeystoreFile, encryptedKeystore);
    boolean isValid = keyManager.isValidKeystoreFile(receivedKeystoreFile, "wrongPass");
    
    assertThat(isValid).isFalse();
  }

  private Set<BLSPublicKey> getList() {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    BLSKeyPair keyPair3 = BLSTestUtil.randomKeyPair(3);

    Set<BLSPublicKey> keySet = new HashSet<>();
    keySet.add(keyPair1.getPublicKey());
    keySet.add(keyPair2.getPublicKey());
    keySet.add(keyPair3.getPublicKey());

    return keySet;
  }
}
