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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ActiveValidator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;

public class KeyManager {

  public enum ValidatorKeystoreDirectories {
    KEYSTORES,
    KEYSTORE_PASSWORDS
  }
  /**
   * Get a listing of active validator keys
   *
   * @return a list of active validators
   */
  public List<ActiveValidator> getActiveValidatorKeys() {
    List<ActiveValidator> activeValidatorList = new ArrayList<>();
    Set<BLSPublicKey> blsPublicKeySet = validatorLoader.getOwnedValidators().getPublicKeys();
    List<String> existingKeysOnKeystore = listSlashProtectionKeysOnKeystore();
    for (BLSPublicKey blsPublicKey : blsPublicKeySet) {
      if (existingKeysOnKeystore.contains(
              blsPublicKey.toBytesCompressed().toUnprefixedHexString().toLowerCase())) {
        activeValidatorList.add(new ActiveValidator(blsPublicKey, false));
      } else {
        activeValidatorList.add(new ActiveValidator(blsPublicKey, true));
      }
    }
    return activeValidatorList;
  }

  /**
   * Delete a collection of validators
   *
   * <p>The response must be symmetric, the list of validators coming in dictates the response
   * order.
   *
   * <p>An individual deletion failure MUST NOT cancel the operation, but rather cause an error for
   * that specific key Validator keys that are reported as deletionStatus.deleted MUST NOT be active
   * once the result is returned.
   *
   * <p>Each failure should result in a failure message for that specific key Slashing protection
   * data MUST be returned if we have the information
   *
   * <p>- NOT_FOUND should only be returned if the key was not there, and we didn't have slashing
   * protection information
   *
   * <p>- NOT_ACTIVE indicates the key wasn't active, but we had slashing data
   *
   * <p>- DELETED indicates the key was found, and we have stopped using it and removed it.
   *
   * <p>- ERROR indicates we couldn't stop using the key (read-only might be a reason)
   *
   * <p>If an exception is thrown, it will cause a 500 error on the API, so that is an undesirable
   * outcome.
   *
   * @param validators list of validator public keys that should be removed
   * @return The result of each deletion, and slashing protection data
   */
  public DeleteKeysResponse deleteValidators(final List<BLSPublicKey> validators) {
    throw new NotImplementedException("deleteValidators not implemented yet");
  }

  /**
   * Import a collection of validators.
   *
   * <p>The result needs to be symmetric with the input order Must supply a message in case of
   * error.
   *
   * <p>Super important that an individual error doesn't fail the whole process, each should be its
   * own process that can error out.
   *
   * @param keystores strings of keystore files
   * @param passwords strings of passwords
   * @param slashingProtection a combined slashing protection payload
   * @return a list of 1 status per keystore that was attempted to be imported
   */
  public List<ImportStatus> importValidators(
      final List<String> keystores, final List<String> passwords, final String slashingProtection) {
    throw new NotImplementedException("importValidators not implemented yet");
  }

  private final ValidatorLoader validatorLoader;
  private final Path validatorDataDirectory;

  public KeyManager(final ValidatorLoader validatorLoader, final Path validatorDataDirectory) {
    this.validatorLoader = validatorLoader;
    this.validatorDataDirectory = validatorDataDirectory;
  }

  public Set<BLSPublicKey> getValidatorKeys() {
    return validatorLoader.getOwnedValidators().getPublicKeys();
  }

  Path getKeystorePathFor(final ValidatorKeystoreDirectories directory) throws IOException {
    Path keystorePath = validatorDataDirectory.resolve(directory.toString().toLowerCase());
    if (!keystorePath.toFile().exists()) {
      Files.createDirectory(keystorePath);
    }
    return keystorePath;
  }

  private List<String> listSlashProtectionKeysOnKeystore() {
    try {
      Path directoryKeys = getKeystorePathFor(ValidatorKeystoreDirectories.KEYSTORES);
      try (Stream<Path> paths = Files.walk(directoryKeys)) {
        return paths
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(name -> name.substring(0, name.length() - ".yml".length()))
                .collect(Collectors.toList());
      }
    } catch (IOException e) {
      return Collections.emptyList();
    }
  }
}
