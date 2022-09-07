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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Eth1Address;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.ProposerConfig.RegistrationOverrides;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

public class ValidatorRegistrator implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<BLSPublicKey, SignedValidatorRegistration> cachedValidatorRegistrations =
      Maps.newConcurrentMap();

  private final AtomicBoolean firstCallDone = new AtomicBoolean(false);
  private final AtomicBoolean registrationInProgress = new AtomicBoolean(false);
  private final AtomicReference<UInt64> lastRunEpoch = new AtomicReference<>();

  private final Spec spec;
  private final TimeProvider timeProvider;
  private final OwnedValidators ownedValidators;
  private final ProposerConfigProvider proposerConfigProvider;
  private final ValidatorConfig validatorConfig;
  private final ValidatorRegistrationPropertiesProvider validatorRegistrationPropertiesProvider;
  private final ValidatorRegistrationBatchSender validatorRegistrationBatchSender;

  public ValidatorRegistrator(
      final Spec spec,
      final TimeProvider timeProvider,
      final OwnedValidators ownedValidators,
      final ProposerConfigProvider proposerConfigProvider,
      final ValidatorConfig validatorConfig,
      final ValidatorRegistrationPropertiesProvider validatorRegistrationPropertiesProvider,
      final ValidatorRegistrationBatchSender validatorRegistrationBatchSender) {
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.ownedValidators = ownedValidators;
    this.proposerConfigProvider = proposerConfigProvider;
    this.validatorRegistrationPropertiesProvider = validatorRegistrationPropertiesProvider;
    this.validatorRegistrationBatchSender = validatorRegistrationBatchSender;
    this.validatorConfig = validatorConfig;
  }

  @Override
  public void onSlot(UInt64 slot) {
    if (isNotReadyToRegister()) {
      return;
    }
    if (registrationNeedsToBeRun(slot)) {
      final UInt64 epoch = spec.computeEpochAtSlot(slot);
      lastRunEpoch.set(epoch);
      final List<Validator> activeValidators = ownedValidators.getActiveValidators();
      LOG.debug(
          "Checking if registration is required for {} validator(s) at epoch {}",
          activeValidators.size(),
          epoch);
      registrationInProgress.set(true);
      registerValidators(activeValidators)
          .handleException(VALIDATOR_LOGGER::registeringValidatorsFailed)
          .always(
              () -> {
                registrationInProgress.set(false);
                cleanupCache(activeValidators);
              });
    }
  }

  @Override
  public void onHeadUpdate(
      UInt64 slot,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      Bytes32 headBlockRoot) {}

  @Override
  public void onPossibleMissedEvents() {}

  @Override
  public void onValidatorsAdded() {
    if (isNotReadyToRegister() || lastRunEpoch.get() == null) {
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
  public void onBlockProductionDue(UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(UInt64 slot) {}

  public int getNumberOfCachedRegistrations() {
    return cachedValidatorRegistrations.size();
  }

  private UInt64 getGasLimit(final BLSPublicKey publicKey) {
    return validatorRegistrationPropertiesProvider
        .getGasLimit(publicKey)
        .orElse(validatorConfig.getBuilderRegistrationDefaultGasLimit());
  }

  private boolean isNotReadyToRegister() {
    if (!validatorRegistrationPropertiesProvider.isReadyToProvideProperties()) {
      LOG.debug("Not ready to register validator(s).");
      return true;
    }
    return false;
  }

  private boolean registrationNeedsToBeRun(final UInt64 slot) {
    final boolean isFirstCall = firstCallDone.compareAndSet(false, true);
    if (isFirstCall) {
      return true;
    }
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    final boolean isBeginningOfEpoch = slot.mod(spec.getSlotsPerEpoch(slot)).isZero();
    if (isBeginningOfEpoch && registrationInProgress.get()) {
      LOG.warn(
          "Validator(s) registration for epoch {} is still in progress. Will skip registration for the current epoch {}.",
          lastRunEpoch.get(),
          currentEpoch);
      return false;
    }
    return isBeginningOfEpoch
        && currentEpoch
            .minus(lastRunEpoch.get())
            .isGreaterThanOrEqualTo(Constants.EPOCHS_PER_VALIDATOR_REGISTRATION_SUBMISSION);
  }

  private SafeFuture<Void> registerValidators(final List<Validator> validators) {
    if (validators.isEmpty()) {
      return SafeFuture.COMPLETE;
    }

    return proposerConfigProvider
        .getProposerConfig()
        .thenCompose(
            maybeProposerConfig -> {
              final Stream<SafeFuture<SignedValidatorRegistration>> validatorRegistrationsFutures =
                  validators.stream()
                      .map(
                          validator ->
                              createSignedValidatorRegistration(maybeProposerConfig, validator))
                      .flatMap(Optional::stream);

              return SafeFuture.collectAll(validatorRegistrationsFutures)
                  .thenCompose(validatorRegistrationBatchSender::sendInBatches);
            });
  }

  private Optional<SafeFuture<SignedValidatorRegistration>> createSignedValidatorRegistration(
      final Optional<ProposerConfig> maybeProposerConfig, final Validator validator) {

    final BLSPublicKey publicKey = validator.getPublicKey();

    if (!registrationIsEnabled(maybeProposerConfig, publicKey)) {
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
    final UInt64 gasLimit = getGasLimit(publicKey);

    final Optional<UInt64> maybeTimestampOverride =
        getTimestampOverride(maybeProposerConfig, publicKey);
    final Optional<BLSPublicKey> maybePublicKeyOverride =
        getPublicKeyOverride(maybeProposerConfig, publicKey);

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

  private boolean registrationIsEnabled(
      final Optional<ProposerConfig> maybeProposerConfig, final BLSPublicKey publicKey) {
    return maybeProposerConfig
        .flatMap(proposerConfig -> proposerConfig.isBuilderEnabledForPubKey(publicKey))
        .orElse(validatorConfig.isBuilderRegistrationDefaultEnabled());
  }

  private ValidatorRegistration createValidatorRegistration(
      final BLSPublicKey publicKey,
      final Eth1Address feeRecipient,
      final UInt64 gasLimit,
      final UInt64 timestamp) {
    return ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA.create(
        feeRecipient, gasLimit, timestamp, publicKey);
  }

  private Optional<UInt64> getTimestampOverride(
      final Optional<ProposerConfig> maybeProposerConfig, final BLSPublicKey publicKey) {
    return maybeProposerConfig
        .flatMap(proposerConfig -> proposerConfig.getBuilderRegistrationOverrides(publicKey))
        .flatMap(RegistrationOverrides::getTimestamp)
        .or(validatorConfig::getBuilderRegistrationTimestampOverride);
  }

  private Optional<BLSPublicKey> getPublicKeyOverride(
      final Optional<ProposerConfig> maybeProposerConfig, final BLSPublicKey publicKey) {
    return maybeProposerConfig
        .flatMap(proposerConfig -> proposerConfig.getBuilderRegistrationOverrides(publicKey))
        .flatMap(RegistrationOverrides::getPublicKey)
        .or(validatorConfig::getBuilderRegistrationPublicKeyOverride);
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
