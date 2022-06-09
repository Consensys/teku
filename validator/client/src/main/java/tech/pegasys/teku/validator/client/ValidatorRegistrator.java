/*
 * Copyright 2022 ConsenSys AG.
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
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszUtils;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ValidatorRegistration;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

public class ValidatorRegistrator implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<BLSPublicKey, SignedValidatorRegistration> cachedValidatorRegistrations =
      Maps.newConcurrentMap();

  private final AtomicBoolean firstCallDone = new AtomicBoolean(false);
  private final AtomicReference<UInt64> lastProcessedEpoch = new AtomicReference<>();

  private final Spec spec;
  private final TimeProvider timeProvider;
  private final OwnedValidators ownedValidators;
  private final ProposerConfigProvider proposerConfigProvider;
  private final ValidatorConfig validatorConfig;
  private final FeeRecipientProvider feeRecipientProvider;
  private final ValidatorApiChannel validatorApiChannel;

  public ValidatorRegistrator(
      final Spec spec,
      final TimeProvider timeProvider,
      final OwnedValidators ownedValidators,
      final ProposerConfigProvider proposerConfigProvider,
      final ValidatorConfig validatorConfig,
      final FeeRecipientProvider feeRecipientProvider,
      final ValidatorApiChannel validatorApiChannel) {
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.ownedValidators = ownedValidators;
    this.proposerConfigProvider = proposerConfigProvider;
    this.feeRecipientProvider = feeRecipientProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.validatorConfig = validatorConfig;
  }

  @Override
  public void onSlot(UInt64 slot) {
    if (isNotReadyToRegister()) {
      return;
    }
    if (firstCallDone.compareAndSet(false, true) || isBeginningOfEpoch(slot)) {
      final UInt64 epoch = spec.computeEpochAtSlot(slot);
      lastProcessedEpoch.set(epoch);
      final List<Validator> activeValidators = ownedValidators.getActiveValidators();
      LOG.debug(
          "Checking if registration is required for {} validator(s) at epoch {}",
          activeValidators.size(),
          epoch);
      registerValidators(activeValidators, epoch)
          .finish(
              () -> cleanupCache(activeValidators), VALIDATOR_LOGGER::registeringValidatorsFailed);
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
    if (isNotReadyToRegister() || lastProcessedEpoch.get() == null) {
      return;
    }

    final List<Validator> newlyAddedValidators =
        ownedValidators.getActiveValidators().stream()
            .filter(
                validator -> !cachedValidatorRegistrations.containsKey(validator.getPublicKey()))
            .collect(Collectors.toList());

    registerValidators(newlyAddedValidators, lastProcessedEpoch.get())
        .finish(VALIDATOR_LOGGER::registeringValidatorsFailed);
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

  private boolean isNotReadyToRegister() {
    if (!feeRecipientProvider.isReadyToProvideFeeRecipient()) {
      LOG.debug("Not ready to register validator(s).");
      return true;
    }
    return false;
  }

  private boolean isBeginningOfEpoch(final UInt64 slot) {
    return slot.mod(spec.getSlotsPerEpoch(slot)).isZero();
  }

  private SafeFuture<Void> registerValidators(
      final List<Validator> validators, final UInt64 epoch) {
    if (validators.isEmpty()) {
      return SafeFuture.completedFuture(null);
    }

    return proposerConfigProvider
        .getProposerConfig()
        .thenCompose(
            maybeProposerConfig -> {
              final Stream<SafeFuture<SignedValidatorRegistration>> validatorRegistrationsFutures =
                  validators.stream()
                      .map(
                          validator ->
                              createSignedValidatorRegistration(
                                  maybeProposerConfig, validator, epoch))
                      .flatMap(Optional::stream);

              return SafeFuture.collectAll(validatorRegistrationsFutures)
                  .thenCompose(this::sendValidatorRegistrations);
            });
  }

  private Optional<SafeFuture<SignedValidatorRegistration>> createSignedValidatorRegistration(
      final Optional<ProposerConfig> maybeProposerConfig,
      final Validator validator,
      final UInt64 epoch) {
    final BLSPublicKey publicKey = validator.getPublicKey();

    if (!registrationIsEnabled(maybeProposerConfig, publicKey)) {
      LOG.trace("Validator registration is disabled for {}", publicKey);
      return Optional.empty();
    }

    final Optional<Eth1Address> maybeFeeRecipient = feeRecipientProvider.getFeeRecipient(publicKey);

    if (maybeFeeRecipient.isEmpty()) {
      LOG.debug(
          "Couldn't retrieve fee recipient for {}. Will skip registering this validator.",
          publicKey);
      return Optional.empty();
    }

    final Eth1Address feeRecipient = maybeFeeRecipient.get();
    final UInt64 gasLimit = getGasLimit(maybeProposerConfig, publicKey);

    return Optional.ofNullable(cachedValidatorRegistrations.get(publicKey))
        .filter(
            cachedValidatorRegistration -> {
              final boolean needsUpdate =
                  registrationNeedsUpdating(cachedValidatorRegistration, feeRecipient, gasLimit);
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
              final ValidatorRegistration validatorRegistration =
                  createValidatorRegistration(publicKey, feeRecipient, gasLimit);
              final Signer signer = validator.getSigner();
              return Optional.of(
                  signAndCacheValidatorRegistration(validatorRegistration, signer, epoch));
            });
  }

  private SafeFuture<Void> sendValidatorRegistrations(
      final List<SignedValidatorRegistration> validatorRegistrations) {
    if (validatorRegistrations.isEmpty()) {
      LOG.debug("No validator(s) require registering.");
      return SafeFuture.completedFuture(null);
    }

    final SszList<SignedValidatorRegistration> sszValidatorRegistrations =
        SszUtils.toSszList(
            ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA, validatorRegistrations);

    return validatorApiChannel
        .registerValidators(sszValidatorRegistrations)
        .thenPeek(__ -> LOG.info("{} validator(s) registered.", sszValidatorRegistrations.size()));
  }

  private boolean registrationIsEnabled(
      final Optional<ProposerConfig> maybeProposerConfig, final BLSPublicKey publicKey) {
    return maybeProposerConfig
        .flatMap(
            proposerConfig -> proposerConfig.isValidatorRegistrationEnabledForPubKey(publicKey))
        .orElse(validatorConfig.isValidatorsRegistrationDefaultEnabled());
  }

  private UInt64 getGasLimit(
      final Optional<ProposerConfig> maybeProposerConfig, final BLSPublicKey publicKey) {
    return maybeProposerConfig
        .flatMap(
            proposerConfig -> proposerConfig.getValidatorRegistrationGasLimitForPubKey(publicKey))
        .orElse(validatorConfig.getValidatorsRegistrationDefaultGasLimit());
  }

  private ValidatorRegistration createValidatorRegistration(
      final BLSPublicKey publicKey, final Eth1Address feeRecipient, final UInt64 gasLimit) {
    return ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA.create(
        feeRecipient, gasLimit, timeProvider.getTimeInSeconds(), publicKey);
  }

  private SafeFuture<SignedValidatorRegistration> signAndCacheValidatorRegistration(
      final ValidatorRegistration validatorRegistration, final Signer signer, final UInt64 epoch) {
    return signer
        .signValidatorRegistration(validatorRegistration, epoch)
        .thenApply(
            signature -> {
              final SignedValidatorRegistration signedValidatorRegistration =
                  ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
                      validatorRegistration, signature);
              final BLSPublicKey publicKey = validatorRegistration.getPublicKey();
              LOG.debug("Validator registration signed for {}", publicKey);
              cachedValidatorRegistrations.put(publicKey, signedValidatorRegistration);
              return signedValidatorRegistration;
            });
  }

  public boolean registrationNeedsUpdating(
      final SignedValidatorRegistration signedValidatorRegistration,
      final Eth1Address feeRecipient,
      final UInt64 gasLimit) {
    final ValidatorRegistration validatorRegistration = signedValidatorRegistration.getMessage();
    return !validatorRegistration.getFeeRecipient().equals(feeRecipient)
        || !validatorRegistration.getGasLimit().equals(gasLimit);
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
              boolean requiresRemoving = !activeValidatorsPublicKeys.contains(cachedPublicKey);
              if (requiresRemoving) {
                LOG.debug(
                    "Removing cached registration for {} because validator is no longer active.",
                    cachedPublicKey);
              }
              return requiresRemoving;
            });
  }
}
