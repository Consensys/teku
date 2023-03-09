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

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import com.google.common.collect.Maps;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class ValidatorRegistrator implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();
  private static final UInt64 SLOT_IN_THE_EPOCH_TO_RUN_REGISTRATION = UInt64.valueOf(3);

  private final Map<BLSPublicKey, SignedValidatorRegistration> cachedValidatorRegistrations =
      Maps.newConcurrentMap();

  private final AtomicBoolean firstCallDone = new AtomicBoolean(false);
  private final AtomicBoolean registrationInProgress = new AtomicBoolean(false);
  private final AtomicReference<UInt64> lastRunEpoch = new AtomicReference<>();

  private final Spec spec;
  private final TimeProvider timeProvider;
  private final OwnedValidators ownedValidators;
  private final ProposerConfigPropertiesProvider validatorRegistrationPropertiesProvider;
  private final ValidatorRegistrationBatchSender validatorRegistrationBatchSender;

  public ValidatorRegistrator(
      final Spec spec,
      final TimeProvider timeProvider,
      final OwnedValidators ownedValidators,
      final ProposerConfigPropertiesProvider validatorRegistrationPropertiesProvider,
      final ValidatorRegistrationBatchSender validatorRegistrationBatchSender) {
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.ownedValidators = ownedValidators;
    this.validatorRegistrationPropertiesProvider = validatorRegistrationPropertiesProvider;
    this.validatorRegistrationBatchSender = validatorRegistrationBatchSender;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (!isReadyToRegister()) {
      return;
    }
    if (registrationNeedsToBeRun(slot)) {
      final UInt64 epoch = spec.computeEpochAtSlot(slot);
      lastRunEpoch.set(epoch);
      registerValidators();
    }
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {}

  @Override
  public void onPossibleMissedEvents() {
    if (!isReadyToRegister()) {
      return;
    }
    registerValidators();
  }

  @Override
  public void onValidatorsAdded() {
    // don't execute if the first call hasn't been done yet
    if (!isReadyToRegister() || !firstCallDone.get()) {
      return;
    }

    final List<Validator> newlyAddedValidators =
        ownedValidators.getActiveValidators().stream()
            .filter(
                validator -> !cachedValidatorRegistrations.containsKey(validator.getPublicKey()))
            .collect(Collectors.toList());

    registerValidators(newlyAddedValidators).finish(VALIDATOR_LOGGER::registeringValidatorsFailed);
  }

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {}

  public int getNumberOfCachedRegistrations() {
    return cachedValidatorRegistrations.size();
  }

  private boolean isReadyToRegister() {
    if (validatorRegistrationPropertiesProvider.isReadyToProvideProperties()) {
      return true;
    }
    LOG.debug("Not ready to register validator(s).");
    return false;
  }

  private boolean registrationNeedsToBeRun(final UInt64 slot) {
    final boolean isFirstCall = firstCallDone.compareAndSet(false, true);
    if (isFirstCall) {
      return true;
    }
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    final boolean slotIsApplicable =
        slot.mod(spec.getSlotsPerEpoch(slot))
            .equals(SLOT_IN_THE_EPOCH_TO_RUN_REGISTRATION.minus(1));
    return slotIsApplicable
        && currentEpoch
            .minus(lastRunEpoch.get())
            .isGreaterThanOrEqualTo(Constants.EPOCHS_PER_VALIDATOR_REGISTRATION_SUBMISSION);
  }

  private void registerValidators() {
    if (!registrationInProgress.compareAndSet(false, true)) {
      LOG.debug(
          "Validator registration(s) is still in progress. Will skip sending registration(s).");
      return;
    }
    final List<Validator> activeValidators = ownedValidators.getActiveValidators();
    registerValidators(activeValidators)
        .handleException(VALIDATOR_LOGGER::registeringValidatorsFailed)
        .always(
            () -> {
              registrationInProgress.set(false);
              cleanupCache(activeValidators);
            });
  }

  private SafeFuture<Void> registerValidators(final List<Validator> validators) {
    if (validators.isEmpty()) {
      return SafeFuture.COMPLETE;
    }

    return validatorRegistrationPropertiesProvider
        .refresh()
        .thenCompose(
            __ -> {
              final Stream<SafeFuture<SignedValidatorRegistration>> validatorRegistrationsFutures =
                  createValidatorRegistrations(validators);
              return SafeFuture.collectAllSuccessful(validatorRegistrationsFutures)
                  .thenCompose(validatorRegistrationBatchSender::sendInBatches);
            });
  }

  private Stream<SafeFuture<SignedValidatorRegistration>> createValidatorRegistrations(
      final List<Validator> validators) {
    return validators.stream()
        .map(
            validator ->
                createSignedValidatorRegistration(
                    validator,
                    throwable -> {
                      final String errorMessage =
                          String.format(
                              "Exception while creating a validator registration for %s. Creation will be attempted again next epoch.",
                              validator.getPublicKey());
                      LOG.warn(errorMessage, throwable);
                    }))
        .flatMap(Optional::stream);
  }

  private Optional<SafeFuture<SignedValidatorRegistration>> createSignedValidatorRegistration(
      final Validator validator, final Consumer<Throwable> errorHandler) {
    return createSignedValidatorRegistration(validator)
        .map(registrationFuture -> registrationFuture.whenException(errorHandler));
  }

  private Optional<SafeFuture<SignedValidatorRegistration>> createSignedValidatorRegistration(
      final Validator validator) {

    final BLSPublicKey publicKey = validator.getPublicKey();

    final boolean builderEnabled =
        validatorRegistrationPropertiesProvider.isBuilderEnabled(publicKey);

    if (!builderEnabled) {
      LOG.trace("Validator registration is disabled for {}", publicKey);
      return Optional.empty();
    }

    final Optional<Eth1Address> maybeFeeRecipient =
        validatorRegistrationPropertiesProvider.getFeeRecipient(publicKey);

    if (maybeFeeRecipient.isEmpty()) {
      LOG.debug(
          "Couldn't retrieve fee recipient for {}. Will skip registering this validator.",
          publicKey);
      return Optional.empty();
    }

    final Eth1Address feeRecipient = maybeFeeRecipient.get();
    final UInt64 gasLimit = validatorRegistrationPropertiesProvider.getGasLimit(publicKey);

    final Optional<UInt64> maybeTimestampOverride =
        validatorRegistrationPropertiesProvider.getBuilderRegistrationTimestampOverride(publicKey);
    final Optional<BLSPublicKey> maybePublicKeyOverride =
        validatorRegistrationPropertiesProvider.getBuilderRegistrationPublicKeyOverride(publicKey);

    final ValidatorRegistration validatorRegistration =
        createValidatorRegistration(
            maybePublicKeyOverride.orElse(publicKey),
            feeRecipient,
            gasLimit,
            maybeTimestampOverride.orElse(timeProvider.getTimeInSeconds()));

    return Optional.ofNullable(cachedValidatorRegistrations.get(publicKey))
        .filter(
            cachedValidatorRegistration -> {
              final boolean needsUpdate =
                  registrationNeedsUpdating(
                      cachedValidatorRegistration.getMessage(),
                      validatorRegistration,
                      maybeTimestampOverride);
              if (needsUpdate) {
                LOG.debug(
                    "The cached registration for {} needs updating. Will create a new one.",
                    publicKey);
              }
              return !needsUpdate;
            })
        .map(SafeFuture::completedFuture)
        .or(
            () -> {
              final Signer signer = validator.getSigner();
              return Optional.of(
                  signAndCacheValidatorRegistration(publicKey, validatorRegistration, signer));
            });
  }

  private ValidatorRegistration createValidatorRegistration(
      final BLSPublicKey publicKey,
      final Eth1Address feeRecipient,
      final UInt64 gasLimit,
      final UInt64 timestamp) {
    return ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA.create(
        feeRecipient, gasLimit, timestamp, publicKey);
  }

  private SafeFuture<SignedValidatorRegistration> signAndCacheValidatorRegistration(
      final BLSPublicKey cacheKey,
      final ValidatorRegistration validatorRegistration,
      final Signer signer) {
    return signer
        .signValidatorRegistration(validatorRegistration)
        .thenApply(
            signature -> {
              final SignedValidatorRegistration signedValidatorRegistration =
                  ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
                      validatorRegistration, signature);
              LOG.debug("Validator registration signed for {}", cacheKey);
              cachedValidatorRegistrations.put(cacheKey, signedValidatorRegistration);
              return signedValidatorRegistration;
            });
  }

  public boolean registrationNeedsUpdating(
      final ValidatorRegistration cachedValidatorRegistration,
      final ValidatorRegistration newValidatorRegistration,
      final Optional<UInt64> newMaybeTimestampOverride) {
    final boolean cachedTimestampIsDifferentThanOverride =
        newMaybeTimestampOverride
            .map(
                newTimestampOverride ->
                    !cachedValidatorRegistration.getTimestamp().equals(newTimestampOverride))
            .orElse(false);
    return !cachedValidatorRegistration
            .getFeeRecipient()
            .equals(newValidatorRegistration.getFeeRecipient())
        || !cachedValidatorRegistration.getGasLimit().equals(newValidatorRegistration.getGasLimit())
        || !cachedValidatorRegistration
            .getPublicKey()
            .equals(newValidatorRegistration.getPublicKey())
        || cachedTimestampIsDifferentThanOverride;
  }

  private void cleanupCache(final List<Validator> activeValidators) {
    if (cachedValidatorRegistrations.isEmpty()
        || cachedValidatorRegistrations.size() == activeValidators.size()) {
      return;
    }

    final Set<BLSPublicKey> activeValidatorsPublicKeys =
        activeValidators.stream()
            .map(Validator::getPublicKey)
            .collect(Collectors.toCollection(HashSet::new));

    cachedValidatorRegistrations
        .keySet()
        .removeIf(
            cachedPublicKey -> {
              final boolean requiresRemoving =
                  !activeValidatorsPublicKeys.contains(cachedPublicKey);
              if (requiresRemoving) {
                LOG.debug(
                    "Removing cached registration for {} because validator is no longer active.",
                    cachedPublicKey);
              }
              return requiresRemoving;
            });
  }
}
