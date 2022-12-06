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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.data.SlashingProtectionIncrementalExporter;
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
    final List<Pair<BLSPublicKey, DeleteKeyResult>> deletionResults =
        removeValidators(validators, exporter);
    String exportedData;
    try {
      exportedData = exporter.finalise();
    } catch (JsonProcessingException e) {
      LOG.error("Failed to serialize slashing export data", e);
      exportedData = EXPORT_FAILED;
    }
    return new DeleteKeysResponse(
        deletionResults.stream().map(Pair::getRight).collect(Collectors.toList()), exportedData);
  }

  private List<Pair<BLSPublicKey, DeleteKeyResult>> removeValidators(
      final List<BLSPublicKey> publicKeys, final SlashingProtectionIncrementalExporter exporter) {
    final List<Pair<BLSPublicKey, DeleteKeyResult>> deletionResults = new ArrayList<>();
    for (final BLSPublicKey publicKey : publicKeys) {
      Optional<Validator> maybeValidator =
          validatorLoader.getOwnedValidators().getValidator(publicKey);

      // read-only check in a non-destructive manner
      if (maybeValidator.isPresent() && maybeValidator.get().isReadOnly()) {
        deletionResults.add(
            Pair.of(publicKey, DeleteKeyResult.error("Cannot remove read-only validator")));
        continue;
      }
      // delete validator from owned validators list
      maybeValidator = validatorLoader.getOwnedValidators().removeValidator(publicKey);
      if (maybeValidator.isPresent()) {
        deletionResults.add(Pair.of(publicKey, deleteValidator(maybeValidator.get(), exporter)));
      } else {
        deletionResults.add(
            Pair.of(publicKey, attemptToGetSlashingDataForInactiveValidator(publicKey, exporter)));
      }
    }
    return deletionResults;
  }

  @Override
  public DeleteRemoteKeysResponse deleteExternalValidators(List<BLSPublicKey> validators) {
    final List<Pair<BLSPublicKey, DeleteKeyResult>> deletionResults =
        removeExternalValidators(validators);
    return new DeleteRemoteKeysResponse(
        deletionResults.stream().map(Pair::getRight).collect(Collectors.toList()));
  }

  private List<Pair<BLSPublicKey, DeleteKeyResult>> removeExternalValidators(
      final List<BLSPublicKey> publicKeys) {
    final List<Pair<BLSPublicKey, DeleteKeyResult>> deletionResults = new ArrayList<>();
    for (final BLSPublicKey publicKey : publicKeys) {
      Optional<Validator> maybeValidator =
          validatorLoader.getOwnedValidators().getValidator(publicKey);

      // read-only check in a non-destructive manner
      if (maybeValidator.isPresent() && maybeValidator.get().isReadOnly()) {
        deletionResults.add(
            Pair.of(publicKey, DeleteKeyResult.error("Cannot remove read-only validator")));
        continue;
      }
      // delete validator from owned validators list
      maybeValidator = validatorLoader.getOwnedValidators().getValidator(publicKey);
      if (maybeValidator.isPresent()) {
        DeleteKeyResult result = deleteExternalValidator(maybeValidator.get());
        deletionResults.add(Pair.of(publicKey, result));
        if (result.equals(DeleteKeyResult.success())) {
          validatorLoader.getOwnedValidators().removeValidator(publicKey);
        }
      } else {
        deletionResults.add(Pair.of(publicKey, DeleteKeyResult.notFound()));
      }
    }
    return deletionResults;
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
      final DoppelgangerDetectionAction doppelgangerDetectionAction,
      final Path slashingProtectionPath) {
    final List<Pair<Optional<BLSPublicKey>, PostKeyResult>> importResults = new ArrayList<>();
    boolean reloadRequired = false;
    for (int i = 0; i < keystores.size(); i++) {
      importResults.add(
          importValidatorFromKeystore(
              keystores.get(i), passwords.get(i), slashingProtectionImporter));
      if (importResults.get(i).getValue().getImportStatus() == ImportStatus.IMPORTED) {
        reloadRequired = true;
      }
    }
    if (reloadRequired) {
      if (maybeDoppelgangerDetector.isPresent()) {
        maybeDoppelgangerDetector
            .get()
            .performDoppelgangerDetection(filterImportedPubKeys(importResults))
            .thenAccept(
                doppelgangers -> {
                  if (!doppelgangers.isEmpty()) {
                    List<BLSPublicKey> doppelgangerList = new ArrayList<>(doppelgangers.values());
                    doppelgangerDetectionAction.alert(doppelgangerList);
                    final SlashingProtectionIncrementalExporter exporter =
                        new SlashingProtectionIncrementalExporter(slashingProtectionPath);
                    List<Pair<BLSPublicKey, DeleteKeyResult>> deletionResults =
                        removeValidators(doppelgangerList, exporter);
                    reportDoppelgangersRemoval(deletionResults);
                  }
                  validatorTimingChannel.onValidatorsAdded();
                })
            .exceptionally(
                throwable -> {
                  LOG.error(
                      "Failed to perform doppelganger detection for public keys {}",
                      String.join(
                          ", ",
                          filterImportedPubKeys(importResults).stream()
                              .map(BLSPublicKey::toAbbreviatedString)
                              .collect(Collectors.toSet())),
                      throwable);
                  validatorTimingChannel.onValidatorsAdded();
                  return null;
                })
            .ifExceptionGetsHereRaiseABug();
      } else {
        validatorTimingChannel.onValidatorsAdded();
      }
    }
    return importResults.stream().map(Pair::getValue).collect(Collectors.toList());
  }

  private Set<BLSPublicKey> filterImportedPubKeys(
      List<Pair<Optional<BLSPublicKey>, PostKeyResult>> importResults) {
    return importResults.stream()
        .filter(
            pubKeyPostKeyResultPair ->
                pubKeyPostKeyResultPair.getValue().getImportStatus().equals(ImportStatus.IMPORTED)
                    && pubKeyPostKeyResultPair.getKey().isPresent())
        .map(optionalPostKeyResultPair -> optionalPostKeyResultPair.getKey().get())
        .collect(Collectors.toSet());
  }

  private Set<BLSPublicKey> filterExternallyImportedPubKeys(
      List<Pair<BLSPublicKey, PostKeyResult>> importResults) {
    return importResults.stream()
        .filter(
            pubKeyPostKeyResultPair ->
                pubKeyPostKeyResultPair.getValue().getImportStatus().equals(ImportStatus.IMPORTED))
        .map(Pair::getKey)
        .collect(Collectors.toSet());
  }

  private Pair<Optional<BLSPublicKey>, PostKeyResult> importValidatorFromKeystore(
      final String keystoreString,
      final String password,
      final Optional<SlashingProtectionImporter> slashingProtectionImporter) {
    final KeyStoreData keyStoreData = KeyStoreLoader.loadFromString(keystoreString);
    try {
      keyStoreData.validate();
    } catch (KeyStoreValidationException ex) {
      return Pair.of(Optional.empty(), PostKeyResult.error(ex.getMessage()));
    }

    return validatorLoader.loadLocalMutableValidator(
        keyStoreData, password, slashingProtectionImporter);
  }

  @Override
  public List<PostKeyResult> importExternalValidators(
      final List<ExternalValidator> validators,
      final Optional<DoppelgangerDetector> maybeDoppelgangerDetector,
      final DoppelgangerDetectionAction doppelgangerDetectionAction) {
    final List<Pair<BLSPublicKey, PostKeyResult>> importResults = new ArrayList<>();
    boolean reloadRequired = false;
    for (ExternalValidator v : validators) {
      try {
        importResults.add(
            validatorLoader.loadExternalMutableValidator(v.getPublicKey(), v.getUrl()));
        if (importResults.get(importResults.size() - 1).getValue().getImportStatus()
            == ImportStatus.IMPORTED) {
          reloadRequired = true;
        }
      } catch (Exception e) {
        importResults.add(Pair.of(v.getPublicKey(), PostKeyResult.error(e.getMessage())));
      }
    }
    if (reloadRequired) {
      if (maybeDoppelgangerDetector.isPresent()) {
        maybeDoppelgangerDetector
            .get()
            .performDoppelgangerDetection(filterExternallyImportedPubKeys(importResults))
            .thenAccept(
                doppelgangers -> {
                  if (!doppelgangers.isEmpty()) {
                    doppelgangerDetectionAction.alert(new ArrayList<>(doppelgangers.values()));
                    List<BLSPublicKey> doppelgangerList = new ArrayList<>(doppelgangers.values());
                    List<Pair<BLSPublicKey, DeleteKeyResult>> deletionResults =
                        removeExternalValidators(doppelgangerList);
                    reportDoppelgangersRemoval(deletionResults);
                  }
                  validatorTimingChannel.onValidatorsAdded();
                })
            .exceptionally(
                throwable -> {
                  LOG.error(
                      "Failed to perform doppelganger detection for public keys {}",
                      String.join(
                          ", ",
                          filterExternallyImportedPubKeys(importResults).stream()
                              .map(BLSPublicKey::toAbbreviatedString)
                              .collect(Collectors.toSet())),
                      throwable);
                  validatorTimingChannel.onValidatorsAdded();
                  return null;
                })
            .ifExceptionGetsHereRaiseABug();
      } else {
        validatorTimingChannel.onValidatorsAdded();
      }
    }
    return importResults.stream().map(Pair::getValue).collect(Collectors.toList());
  }

  private void reportDoppelgangersRemoval(
      List<Pair<BLSPublicKey, DeleteKeyResult>> deletionResults) {
    deletionResults.forEach(
        deletionResult -> {
          if (deletionResult.getRight().getStatus().equals(DeletionStatus.DELETED)) {
            LOG.info("Removed doppelganger: {}", deletionResult.getLeft());
          } else {
            LOG.error(
                "Unable to remove validator doppelganger public key {}. {}",
                deletionResult.getLeft(),
                deletionResult.getRight().getMessage().orElse(""));
          }
        });
  }
}
