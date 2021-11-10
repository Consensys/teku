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

import java.nio.file.Path;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;

public class KeyManager {

  private static final Logger LOG = LogManager.getLogger();
  private final ValidatorLoader validatorLoader;

  public KeyManager(final ValidatorLoader validatorLoader) {
    this.validatorLoader = validatorLoader;
  }

  public Set<BLSPublicKey> getValidatorKeys() {
    return validatorLoader.getOwnedValidators().getPublicKeys();
  }

  public boolean isValidKeystoreFile(final Path filePath, final String password) {
    try {
      KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(filePath);
      return KeyStore.validatePassword(password, keyStoreData);
    } catch (KeyStoreValidationException e) {
      LOG.error("Received Keystore is invalid: ", e);
      return false;
    }
  }
}
