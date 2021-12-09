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
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.data.SlashingProtectionIncrementalExporter;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class KeyManager {
  private static final String EXPORT_FAILED =
      "{\"metadata\":{\"interchange_format_version\":\"5\"},\"data\":[]}";
  private static final Logger LOG = LogManager.getLogger();
  private final ValidatorLoader validatorLoader;
  private final DataDirLayout dataDirLayout;

  public KeyManager(final ValidatorLoader validatorLoader, final DataDirLayout dataDirLayout) {
    this.validatorLoader = validatorLoader;
    this.dataDirLayout = dataDirLayout;
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
  public synchronized DeleteKeysResponse deleteValidators(final List<BLSPublicKey> validators) {
    final List<DeleteKeyResult> deletionResults = new ArrayList<>();
    final SlashingProtectionIncrementalExporter exporter =
        new SlashingProtectionIncrementalExporter(
            ValidatorClientService.getSlashingProtectionPath(dataDirLayout));
    for (final BLSPublicKey publicKey : validators) {
      Optional<Validator> maybeValidator =
          validatorLoader.getOwnedValidators().getValidator(publicKey);

      // read-only check in a non-destructive manner
      if (maybeValidator.isPresent() && maybeValidator.get().isReadOnly()) {
        deletionResults.add(DeleteKeyResult.error("Cannot remove read-only validator"));
        continue;
      }
      // delete validator from owned validators list
      maybeValidator = validatorLoader.getOwnedValidators().removeValidator(publicKey);
      if (maybeValidator.isPresent()) {
        deletionResults.add(deleteValidator(maybeValidator.get(), exporter));
      } else {
        deletionResults.add(attemptToGetSlashingDataForInactiveValidator(publicKey, exporter));
      }
    }
    String exportedData;
    try {
      exportedData = exporter.finalise();
    } catch (JsonProcessingException e) {
      LOG.error("Failed to serialize slashing export data", e);
      exportedData = EXPORT_FAILED;
    }
    return new DeleteKeysResponse(deletionResults, exportedData);
  }

  @VisibleForTesting
  DeleteKeyResult attemptToGetSlashingDataForInactiveValidator(
      final BLSPublicKey publicKey, final SlashingProtectionIncrementalExporter exporter) {
    if (exporter.haveSlashingProtectionData(publicKey)) {
      final Optional<String> error = exporter.addPublicKeyToExport(publicKey, LOG::debug);
      return error.map(DeleteKeyResult::error).orElseGet(DeleteKeyResult::notActive);
    } else {
      return DeleteKeyResult.notFound();
    }
  }

  @VisibleForTesting
  DeleteKeyResult deleteValidator(
      final Validator activeValidator, final SlashingProtectionIncrementalExporter exporter) {
    final Signer signer = activeValidator.getSigner();
    signer.delete();
    LOG.info("Removed validator: {}", activeValidator.getPublicKey().toAbbreviatedString());
    final DeleteKeyResult deleteKeyResult =
        validatorLoader.deleteMutableValidator(activeValidator.getPublicKey());
    if (deleteKeyResult.getStatus() == DeletionStatus.DELETED) {
      Optional<String> error =
          exporter.addPublicKeyToExport(activeValidator.getPublicKey(), LOG::debug);
      if (error.isPresent()) {
        return DeleteKeyResult.error(error.get());
      }
    }
    return deleteKeyResult;
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
   * @param slashingProtectionImporter slashing protection importer with loaded context
   * @return a list of 1 status per keystore that was attempted to be imported
   */
  public List<PostKeyResult> importValidators(
      final List<String> keystores,
      final List<String> passwords,
      final Optional<SlashingProtectionImporter> slashingProtectionImporter) {
    final List<PostKeyResult> importResults = new ArrayList<>();
    for (int i = 0; i < keystores.size(); i++) {
      importResults.add(
          importValidatorFromKeystore(
              keystores.get(i), passwords.get(i), slashingProtectionImporter));
    }
    return importResults;
  }

  public DataDirLayout getDataDirLayout() {
    return dataDirLayout;
  }

  private PostKeyResult importValidatorFromKeystore(
      final String keystoreString,
      final String password,
      final Optional<SlashingProtectionImporter> slashingProtectionImporter) {
    final KeyStoreData keyStoreData = KeyStoreLoader.loadFromString(keystoreString);
    try {
      keyStoreData.validate();
    } catch (KeyStoreValidationException ex) {
      return PostKeyResult.error(ex.getMessage());
    }

    return validatorLoader.loadMutableValidator(keyStoreData, password, slashingProtectionImporter);
  }
}
