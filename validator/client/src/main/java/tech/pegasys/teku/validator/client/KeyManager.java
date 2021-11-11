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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;

public class KeyManager {

  public enum ValidatorKeystoreDirectories {
    KEYSTORES,
    KEYSTORE_PASSWORDS
  }

  private final ValidatorLoader validatorLoader;
  private final Path validatorDataDirectory;

  public KeyManager(final ValidatorLoader validatorLoader, Path validatorDataDirectory) {
    this.validatorLoader = validatorLoader;
    this.validatorDataDirectory = validatorDataDirectory;
  }

  public Set<BLSPublicKey> getValidatorKeys() {
    return validatorLoader.getOwnedValidators().getPublicKeys();
  }

  public Path getKeystorePathFor(final ValidatorKeystoreDirectories directory) throws IOException {
    Path keystorePath = validatorDataDirectory.resolve(directory.toString().toLowerCase());
    if (!keystorePath.toFile().exists()) {
      Files.createDirectory(keystorePath);
    }
    return keystorePath;
  }
}
