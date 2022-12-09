/*
 * Copyright ConsenSys Software Inc., 2022
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.data.SlashingProtectionIncrementalExporter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.doppelganger.DoppelgangerDetectionAction;
import tech.pegasys.teku.validator.client.doppelganger.DoppelgangerDetector;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteRemoteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ExternalValidator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class ActiveKeyManager implements KeyManager {
  private static final String EXPORT_FAILED =
      "{\"metadata\":{\"interchange_format_version\":\"5\"},\"data\":[]}";
  private static final Logger LOG = LogManager.getLogger();
  private final ValidatorLoader validatorLoader;
  private final ValidatorTimingChannel validatorTimingChannel;

  public ActiveKeyManager(
      final ValidatorLoader validatorLoader, final ValidatorTimingChannel validatorTimingChannel) {
    this.validatorLoader = validatorLoader;
    this.validatorTimingChannel = validatorTimingChannel;
  }

  /**
   * Get a listing of active validator keys
   *
   * @return a list of active validators
   */
  @Override
  public List<Validator> getActiveValidatorKeys() {
    return validatorLoader.getOwnedValidators().getActiveValidators().stream()
        .filter(validator -> validator.getSigner().isLocal())
        .collect(Collectors.toList());
  }

  @Override
  public List<ExternalValidator> getActiveRemoteValidatorKeys() {
    return validatorLoader.getOwnedValidators().getActiveValidators().stream()
        .filter(validator -> !validator.getSigner().isLocal())
        .map(ExternalValidator::create)
        .collect(Collectors.toList());
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
  @Override
  public synchronized DeleteKeysResponse deleteValidators(
      final List<BLSPublicKey> validators, final Path slashingProtectionPath) {
    final SlashingProtectionIncrementalExporter exporter =
        new SlashingProtectionIncrementalExporter(slashingProtectionPath);
    final List<DeleteKeyResult> deletionResults = removeValidators(validators, exporter);
    String exportedData;
    try {
      exportedData = exporter.finalise();
    } catch (JsonProcessingException e) {
      LOG.error("Failed to serialize slashing export data", e);
      exportedData = EXPORT_FAILED;
    }
    return new DeleteKeysResponse(deletionResults, exportedData);
  }

  private List<DeleteKeyResult> removeValidators(
      final List<BLSPublicKey> publicKeys, final SlashingProtectionIncrementalExporter exporter) {
    final List<DeleteKeyResult> deletionResults = new ArrayList<>();
    for (final BLSPublicKey publicKey : publicKeys) {
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
    return deletionResults;
  }

  @Override
  public DeleteRemoteKeysResponse deleteExternalValidators(final List<BLSPublicKey> validators) {
    final List<DeleteKeyResult> deletionResults = new ArrayList<>();
    for (final BLSPublicKey publicKey : validators) {
      Optional<Validator> maybeValidator =
          validatorLoader.getOwnedValidators().getValidator(publicKey);

      // read-only check in a non-destructive manner
      if (maybeValidator.isPresent() && maybeValidator.get().isReadOnly()) {
        deletionResults.add(DeleteKeyResult.error("Cannot remove read-only validator"));
        continue;
      }
      // delete validator from owned validators list
      maybeValidator = validatorLoader.getOwnedValidators().getValidator(publicKey);
      if (maybeValidator.isPresent()) {
        DeleteKeyResult result = deleteExternalValidator(maybeValidator.get());
        deletionResults.add(result);
        if (result.equals(DeleteKeyResult.success())) {
          validatorLoader.getOwnedValidators().removeValidator(publicKey);
        }
      } else {
        deletionResults.add(DeleteKeyResult.notFound());
      }
    }
    return new DeleteRemoteKeysResponse(deletionResults);
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
    LOG.info("Removed validator: {}", activeValidator.getPublicKey().toString());
    final DeleteKeyResult deleteKeyResult =
        validatorLoader.deleteLocalMutableValidator(activeValidator.getPublicKey());
    if (deleteKeyResult.getStatus() == DeletionStatus.DELETED) {
      Optional<String> error =
          exporter.addPublicKeyToExport(activeValidator.getPublicKey(), LOG::debug);
      if (error.isPresent()) {
        return DeleteKeyResult.error(error.get());
      }
    }
    return deleteKeyResult;
  }

  DeleteKeyResult deleteExternalValidator(final Validator activeValidator) {
    final Signer signer = activeValidator.getSigner();
    signer.delete();
    LOG.info("Removed remote validator: {}", activeValidator.getPublicKey().toString());
    return validatorLoader.deleteExternalMutableValidator(activeValidator.getPublicKey());
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
  @Override
  public List<PostKeyResult> importValidators(
      final List<String> keystores,
      final List<String> passwords,
      final Optional<SlashingProtectionImporter> slashingProtectionImporter,
      final Optional<DoppelgangerDetector> maybeDoppelgangerDetector,
      final DoppelgangerDetectionAction doppelgangerDetectionAction) {
    final List<LocalValidatorImportResult> importResults = new ArrayList<>();
    boolean reloadRequired;

    if (maybeDoppelgangerDetector.isPresent()) {
      reloadRequired =
          importValidators(keystores, passwords, slashingProtectionImporter, importResults, false);
      if (reloadRequired) {
        maybeDoppelgangerDetector
            .get()
            .performDoppelgangerDetection(filterImportedPubKeys(importResults))
            .thenAccept(
                doppelgangers ->
                    handleValidatorDoppelgangers(
                        doppelgangerDetectionAction, importResults, doppelgangers))
            .exceptionally(
                throwable -> {
                  logFailedDoppelgangerCheck(filterImportedPubKeys(importResults), throwable);
                  loadLocalValidators(importResults);
                  validatorTimingChannel.onValidatorsAdded();
                  return null;
                })
            .ifExceptionGetsHereRaiseABug();
      }
    } else {
      reloadRequired =
          importValidators(keystores, passwords, slashingProtectionImporter, importResults, true);
      if (reloadRequired) {
        validatorTimingChannel.onValidatorsAdded();
      }
    }
    return importResults.stream()
        .map(ValidatorImportResult::getPostKeyResult)
        .collect(Collectors.toList());
  }

  private void handleValidatorDoppelgangers(
      final DoppelgangerDetectionAction doppelgangerDetectionAction,
      final List<LocalValidatorImportResult> importResults,
      final Map<UInt64, BLSPublicKey> doppelgangers) {
    final List<BLSPublicKey> doppelgangerList = new ArrayList<>(doppelgangers.values());
    if (!doppelgangerList.isEmpty()) {
      doppelgangerDetectionAction.alert(doppelgangerList);
      importEligibleLocalValidators(importResults, doppelgangerList);
    }
    validatorTimingChannel.onValidatorsAdded();
  }

  private void logFailedDoppelgangerCheck(
      final Set<BLSPublicKey> importResults, final Throwable throwable) {
    LOG.error(
        "Failed to perform doppelganger detection for public keys {}",
        importResults.stream()
            .map(BLSPublicKey::toAbbreviatedString)
            .collect(Collectors.joining(", ")),
        throwable);
  }

  private void loadLocalValidators(final List<LocalValidatorImportResult> importResults) {
    importResults.stream()
        .filter(
            validatorImportResult ->
                validatorImportResult
                        .getPostKeyResult()
                        .getImportStatus()
                        .equals(ImportStatus.IMPORTED)
                    && validatorImportResult.getKeyStoreData().isPresent()
                    && validatorImportResult.getPublicKey().isPresent())
        .forEach(
            validatorImportResult -> {
              importEligibleValidator(validatorImportResult);
            });
  }

  private void reportValidatorImport(
      final ValidatorImportResult importResult,
      final Optional<BLSPublicKey> validatorImportResult) {
    if (importResult.getPostKeyResult().getImportStatus().equals(ImportStatus.IMPORTED)) {
      LOG.info("Added validator {}", validatorImportResult.get());
    } else {
      LOG.error(
          "Unable to add validator {}. {}",
          validatorImportResult.get(),
          importResult.getPostKeyResult().getMessage().orElse(""));
    }
  }

  private boolean importValidators(
      final List<String> keystores,
      final List<String> passwords,
      final Optional<SlashingProtectionImporter> slashingProtectionImporter,
      final List<LocalValidatorImportResult> importResults,
      final boolean addToOwnedValidators) {
    boolean reloadRequired = false;
    for (int i = 0; i < keystores.size(); i++) {
      final Optional<KeyStoreData> maybeKeystoreData =
          getKeyStoreData(importResults, keystores.get(i), passwords.get(i));
      if (maybeKeystoreData.isPresent()) {
        LocalValidatorImportResult localValidatorImportResult =
            validatorLoader.loadLocalMutableValidator(
                maybeKeystoreData.get(),
                passwords.get(i),
                slashingProtectionImporter,
                addToOwnedValidators);
        importResults.add(localValidatorImportResult);
        if (localValidatorImportResult.getPostKeyResult().getImportStatus()
            == ImportStatus.IMPORTED) {
          reloadRequired = true;
        }
      }
    }
    return reloadRequired;
  }

  private Optional<KeyStoreData> getKeyStoreData(
      final List<LocalValidatorImportResult> importResults,
      final String keystoreString,
      final String password) {
    final KeyStoreData keyStoreData = KeyStoreLoader.loadFromString(keystoreString);
    try {
      keyStoreData.validate();
    } catch (KeyStoreValidationException ex) {
      importResults.add(
          new LocalValidatorImportResult.Builder(PostKeyResult.error(ex.getMessage()), password)
              .build());
      return Optional.empty();
    }
    return Optional.of(keyStoreData);
  }

  private Set<BLSPublicKey> filterImportedPubKeys(
      final List<? extends ValidatorImportResult> validatorImportResults) {
    return validatorImportResults.stream()
        .filter(
            validatorImportResult ->
                validatorImportResult
                        .getPostKeyResult()
                        .getImportStatus()
                        .equals(ImportStatus.IMPORTED)
                    && validatorImportResult.getPublicKey().isPresent())
        .map(validatorImportResult -> validatorImportResult.getPublicKey().get())
        .collect(Collectors.toSet());
  }

  private Set<BLSPublicKey> filterExternallyImportedPubKeys(
      final List<ExternalValidatorImportResult> externalValidatorImportResults) {
    return externalValidatorImportResults.stream()
        .filter(
            externalValidatorImportResult ->
                externalValidatorImportResult.getPublicKey().isPresent()
                    && externalValidatorImportResult
                        .getPostKeyResult()
                        .getImportStatus()
                        .equals(ImportStatus.IMPORTED))
        .map(externalValidatorImportResult -> externalValidatorImportResult.getPublicKey().get())
        .collect(Collectors.toSet());
  }

  @Override
  public List<PostKeyResult> importExternalValidators(
      final List<ExternalValidator> validators,
      final Optional<DoppelgangerDetector> maybeDoppelgangerDetector,
      final DoppelgangerDetectionAction doppelgangerDetectionAction) {
    final List<ExternalValidatorImportResult> importResults = new ArrayList<>();
    boolean reloadRequired = false;

    if (maybeDoppelgangerDetector.isPresent()) {
      for (ExternalValidator externalValidator : validators) {
        try {
          importResults.add(
              validatorLoader.loadExternalMutableValidator(
                  externalValidator.getPublicKey(), externalValidator.getUrl(), false));
          if (importResults.get(importResults.size() - 1).getPostKeyResult().getImportStatus()
              == ImportStatus.IMPORTED) {
            reloadRequired = true;
          }
        } catch (Exception e) {
          importResults.add(
              new ExternalValidatorImportResult.Builder(
                      PostKeyResult.error(e.getMessage()), externalValidator.getUrl())
                  .publicKey(Optional.of(externalValidator.getPublicKey()))
                  .build());
        }
      }
      if (reloadRequired) {
        maybeDoppelgangerDetector
            .get()
            .performDoppelgangerDetection(filterExternallyImportedPubKeys(importResults))
            .thenAccept(
                doppelgangers ->
                    handleExternalValidatorDoppelgangers(
                        doppelgangerDetectionAction, importResults, doppelgangers))
            .exceptionally(
                throwable -> {
                  logFailedDoppelgangerCheck(filterImportedPubKeys(importResults), throwable);
                  loadExternalValidators(importResults);
                  validatorTimingChannel.onValidatorsAdded();
                  return null;
                })
            .ifExceptionGetsHereRaiseABug();
      }
    } else {
      for (ExternalValidator externalValidator : validators) {
        try {
          importResults.add(
              validatorLoader.loadExternalMutableValidator(
                  externalValidator.getPublicKey(), externalValidator.getUrl(), true));
          if (importResults.get(importResults.size() - 1).getPostKeyResult().getImportStatus()
              == ImportStatus.IMPORTED) {
            reloadRequired = true;
          }
        } catch (Exception e) {
          importResults.add(
              new ExternalValidatorImportResult.Builder(
                      PostKeyResult.error(e.getMessage()), externalValidator.getUrl())
                  .publicKey(Optional.of(externalValidator.getPublicKey()))
                  .build());
        }
      }
      if (reloadRequired) {
        validatorTimingChannel.onValidatorsAdded();
      }
    }

    return importResults.stream()
        .map(ValidatorImportResult::getPostKeyResult)
        .collect(Collectors.toList());
  }

  private void handleExternalValidatorDoppelgangers(
      final DoppelgangerDetectionAction doppelgangerDetectionAction,
      final List<ExternalValidatorImportResult> importResults,
      final Map<UInt64, BLSPublicKey> doppelgangers) {
    final List<BLSPublicKey> doppelgangerList = new ArrayList<>(doppelgangers.values());
    if (!doppelgangerList.isEmpty()) {
      doppelgangerDetectionAction.alert(doppelgangerList);
      importEligibleExternalValidators(importResults, doppelgangerList);
    }
    validatorTimingChannel.onValidatorsAdded();
  }

  private void importEligibleLocalValidators(
      final List<LocalValidatorImportResult> importResults,
      final List<BLSPublicKey> doppelgangerList) {
    importResults.stream()
        .filter(
            validatorImportResult ->
                isValidatorNotDoppelganger(doppelgangerList, validatorImportResult))
        .forEach(
            validatorImportResult -> {
              importEligibleValidator(validatorImportResult);
            });
  }

  private void importEligibleValidator(final LocalValidatorImportResult validatorImportResult) {
    final ValidatorImportResult importResult =
        validatorLoader.addValidator(
            validatorImportResult.getKeyStoreData().get(),
            validatorImportResult.getPassword(),
            validatorImportResult.getPublicKey().get());
    reportValidatorImport(importResult, validatorImportResult.getPublicKey());
  }

  private boolean isValidatorNotDoppelganger(
      final List<BLSPublicKey> doppelgangerList,
      final LocalValidatorImportResult validatorImportResult) {
    return validatorImportResult.getPostKeyResult().getImportStatus().equals(ImportStatus.IMPORTED)
        && validatorImportResult.getKeyStoreData().isPresent()
        && validatorImportResult.getPublicKey().isPresent()
        && !doppelgangerList.contains(validatorImportResult.getPublicKey().get());
  }

  private void importEligibleExternalValidators(
      final List<ExternalValidatorImportResult> importResults,
      final List<BLSPublicKey> doppelgangerList) {
    importResults.stream()
        .filter(
            validatorImportResult ->
                validatorImportResult
                        .getPostKeyResult()
                        .getImportStatus()
                        .equals(ImportStatus.IMPORTED)
                    && validatorImportResult.getPublicKey().isPresent()
                    && !doppelgangerList.contains(validatorImportResult.getPublicKey().get()))
        .forEach(
            validatorImportResult -> {
              ValidatorImportResult importResult =
                  validatorLoader.addExternalValidator(
                      validatorImportResult.getSignerUrl(),
                      validatorImportResult.getPublicKey().get());
              reportValidatorImport(importResult, validatorImportResult.getPublicKey());
            });
  }

  private void loadExternalValidators(final List<ExternalValidatorImportResult> importResults) {
    importResults.stream()
        .filter(
            validatorImportResult ->
                validatorImportResult
                        .getPostKeyResult()
                        .getImportStatus()
                        .equals(ImportStatus.IMPORTED)
                    && validatorImportResult.getPublicKey().isPresent())
        .forEach(
            validatorImportResult -> {
              ValidatorImportResult importResult =
                  validatorLoader.addExternalValidator(
                      validatorImportResult.getSignerUrl(),
                      validatorImportResult.getPublicKey().get());
              reportValidatorImport(importResult, validatorImportResult.getPublicKey());
            });
  }
}
