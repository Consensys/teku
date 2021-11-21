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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class KeyManager {

  private final ValidatorLoader validatorLoader;
  private final DataDirLayout dataDir;
  private final ObjectMapper jsonMapper = new JsonProvider().getObjectMapper();

  public KeyManager(final ValidatorLoader validatorLoader, final DataDirLayout dataDir) {
    this.validatorLoader = validatorLoader;
    this.dataDir = dataDir;
  }

  /**
   * Get a listing of active validator keys
   *
   * @return a list of active validators
   */
  public List<Validator> getActiveValidatorKeys() {
    return validatorLoader.getOwnedValidators().getActiveValidators();
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
    final List<DeleteKeyResult> deletionResults =
        validators.stream()
            .map(
                key ->
                    DeleteKeyResult.error(
                        String.format("error: key %s not deleted", key.toAbbreviatedString())))
            .collect(Collectors.toList());
    return new DeleteKeysResponse(deletionResults, "");
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
  public List<PostKeyResult> importValidators(
      final List<String> keystores, final List<String> passwords, final String slashingProtection) {

    List<PostKeyResult> postKeyResults = new ArrayList<>();

    if (keystores.size() == passwords.size()) {
      final Iterator<String> keystoreIterator = keystores.iterator();
      final Iterator<String> passwordIterator = passwords.iterator();
      while (keystoreIterator.hasNext() && passwordIterator.hasNext()) {
        try {
          final String password = passwordIterator.next();
          final KeyStoreData keystore = getKeystoreDataObject(keystoreIterator.next());
          if (KeyStore.validatePassword(password, keystore)) {
            postKeyResults.add(executeImport(keystore, password));
          } else {
            postKeyResults.add(PostKeyResult.error("Invalid password."));
          }
        } catch (JsonProcessingException e) {
          postKeyResults.add(PostKeyResult.error("Invalid keystore."));
        }
      }
    } else {
      postKeyResults.add(PostKeyResult.error("Keystores and passwords quantity must be the same."));
    }
    return postKeyResults;
  }

  private PostKeyResult executeImport(final KeyStoreData keyStoreData, final String password) {
    final Path keystorePath = ValidatorClientService.getAlterableKeystorePath(dataDir);
    final Path passwordPath = ValidatorClientService.getAlterableKeystorePasswordPath(dataDir);
    try {
      if (!keystorePath.toFile().exists()) {
        Files.createDirectories(keystorePath);
      }
      if (!passwordPath.toFile().exists()) {
        Files.createDirectories(passwordPath);
      }
      final BLSPublicKey validatorPublicKey = BLSPublicKey.fromSSZBytes(keyStoreData.getPubkey());
      final String validatorFileName =
          validatorPublicKey.toSSZBytes().toUnprefixedHexString().toLowerCase();
      if (!validatorLoader.getOwnedValidators().hasValidator(validatorPublicKey)
          && !keystorePath.resolve(validatorFileName + ".json").toFile().exists()
          && !passwordPath.resolve(validatorFileName + ".txt").toFile().exists()) {
        KeyStoreLoader.saveToFile(keystorePath.resolve(validatorFileName + ".json"), keyStoreData);
        Files.writeString(passwordPath.resolve(validatorFileName + ".txt"), password);
        return PostKeyResult.success();
      } else {
        return PostKeyResult.duplicate();
      }
    } catch (IOException e) {
      return PostKeyResult.error("Failed to save keystore file.");
    }
  }

  private KeyStoreData getKeystoreDataObject(final String keystore) throws JsonProcessingException {
    final KeyStoreData keyStoreData;
    keyStoreData = jsonMapper.readValue(keystore, KeyStoreData.class);
    keyStoreData.validate();
    return keyStoreData;
  }
}
