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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;

class KeyManagerTest {

  final ValidatorLoader validatorLoader = Mockito.mock(ValidatorLoader.class);
  final OwnedValidators ownedValidators = Mockito.mock(OwnedValidators.class);

  @Test
  void shouldReturnKeyList(@TempDir Path tempDir) {
    final KeyManager keyManager = new KeyManager(validatorLoader, tempDir);
    Set<BLSPublicKey> keySet = getList();
    when(ownedValidators.getPublicKeys()).thenReturn(keySet);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    Set<BLSPublicKey> receivedKeySet = keyManager.getValidatorKeys();

    assertThat(receivedKeySet).isEqualTo(keySet);
  }

  @Test
  void shouldReturnEmptyKeyList(@TempDir Path tempDir) {
    final KeyManager keyManager = new KeyManager(validatorLoader, tempDir);
    Set<BLSPublicKey> keySet = Collections.emptySet();
    when(ownedValidators.getPublicKeys()).thenReturn(keySet);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    Set<BLSPublicKey> receivedKeySet = keyManager.getValidatorKeys();

    assertThat(receivedKeySet).isEmpty();
  }

  @Test
  void shouldCheckExistingPathStructure(@TempDir Path tempDir) throws IOException {
    Path createdPath = Files.createDirectory(tempDir.resolve("keystores"));
    final KeyManager keyManager = new KeyManager(validatorLoader, tempDir);
    Path keystorePath = keyManager.getKeystorePath();
    assertThat(keystorePath).isEqualTo(createdPath);
  }

  @Test
  void shouldCreatePathStructure(@TempDir Path tempDir) throws IOException {
    final KeyManager keyManager = new KeyManager(validatorLoader, tempDir);
    Path keystorePath = keyManager.getKeystorePath();
    assertThat(keystorePath).exists();
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
