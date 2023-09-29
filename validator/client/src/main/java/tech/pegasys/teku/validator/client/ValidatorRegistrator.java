/*
 * Copyright Consensys Software Inc., 2022
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszUtils;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class ValidatorRegistrator implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<BLSPublicKey, SignedValidatorRegistration> cachedValidatorRegistrations =
      Maps.newConcurrentMap();

  private final AtomicBoolean firstCallDone = new AtomicBoolean(false);
  private final AtomicBoolean registrationInProgress = new AtomicBoolean(false);
  private final AtomicReference<UInt64> currentEpoch = new AtomicReference<>();
  private final AtomicReference<UInt64> lastRunEpoch = new AtomicReference<>();

  private final Spec spec;
  private final OwnedValidators ownedValidators;
  private final ProposerConfigPropertiesProvider validatorRegistrationPropertiesProvider;
  private final ValidatorRegistrationSigningService validatorRegistrationSigningService;
  private final ValidatorApiChannel validatorApiChannel;
  private final int batchSize;

  public ValidatorRegistrator(
      final Spec spec,
      final OwnedValidators ownedValidators,
      final ProposerConfigPropertiesProvider validatorRegistrationPropertiesProvider,
      final ValidatorRegistrationSigningService validatorRegistrationSigningService,
      final ValidatorApiChannel validatorApiChannel,
      final int batchSize) {
    this.spec = spec;
    this.ownedValidators = ownedValidators;
    this.validatorRegistrationPropertiesProvider = validatorRegistrationPropertiesProvider;
    this.validatorRegistrationSigningService = validatorRegistrationSigningService;
    this.validatorApiChannel = validatorApiChannel;
    this.batchSize = batchSize;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    currentEpoch.set(epoch);
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {}

  @Override
  public void onPossibleMissedEvents() {}

  @Override
  public void onValidatorsAdded() {}

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {}

  public void onNewValidatorStatuses(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses) {
    if (!isReadyToRegister()) {
      return;
    }
    final List<Validator> activeAndPendingValidators =
        filterActiveAndPendingValidators(newValidatorStatuses);
    if (registrationNeedsToBeRun()) {
      registerValidators(activeAndPendingValidators, true);
    } else {
      final List<Validator> newValidators =
          activeAndPendingValidators.stream()
              .filter(
                  validator -> !cachedValidatorRegistrations.containsKey(validator.getPublicKey()))
              .toList();
      if (newValidators.isEmpty()) {
        return;
      }
      registerValidators(newValidators, false);
    }
  }

  public int getNumberOfCachedRegistrations() {
    return cachedValidatorRegistrations.size();
  }

  private boolean isReadyToRegister() {
    // Paranoid check
    if (currentEpoch.get() == null) {
      LOG.error("Current epoch is not yet set, skipping validator registrations");
      return false;
    }
    if (validatorRegistrationPropertiesProvider.isReadyToProvideProperties()) {
      return true;
    }
    LOG.debug("Not ready to register validator(s).");
    return false;
  }

  private boolean registrationNeedsToBeRun() {
    final boolean isFirstCall = firstCallDone.compareAndSet(false, true);
    if (isFirstCall) {
      return true;
    }

    return currentEpoch
        .get()
        .minus(lastRunEpoch.get())
        .isGreaterThanOrEqualTo(Constants.EPOCHS_PER_VALIDATOR_REGISTRATION_SUBMISSION);
  }

  private void registerValidators(
      final List<Validator> validators, final boolean updateLastRunEpoch) {
    if (validators.isEmpty()) {
      return;
    }
    if (!registrationInProgress.compareAndSet(false, true)) {
      LOG.warn(
          "Validator registration(s) is still in progress. Will skip sending registration(s).");
      return;
    }
    if (updateLastRunEpoch) {
      lastRunEpoch.set(currentEpoch.get());
    }

    validatorRegistrationPropertiesProvider
        .refresh()
        .thenCompose(__ -> processInBatches(validators))
        .handleException(VALIDATOR_LOGGER::registeringValidatorsFailed)
        .always(
            () -> {
              registrationInProgress.set(false);
              cleanupCache(ownedValidators.getActiveValidators());
            });
  }

  private SafeFuture<Void> processInBatches(final List<Validator> validators) {
    if (validators.isEmpty()) {
      LOG.info(
          "All owned validators are either exited or with unknown status. Skipping registration.");
      return SafeFuture.COMPLETE;
    }
    final List<List<Validator>> batchedValidators = Lists.partition(validators, batchSize);

    LOG.debug(
        "Going to prepare and send {} validator registration(s) to the Beacon Node in {} batch(es)",
        validators.size(),
        batchedValidators.size());

    final Iterator<List<Validator>> batchedValidatorsIterator = batchedValidators.iterator();

    final AtomicInteger batchCounter = new AtomicInteger(0);
    final AtomicInteger successfullySentRegistrations = new AtomicInteger(0);

    return SafeFuture.asyncDoWhile(
            () -> {
              if (!batchedValidatorsIterator.hasNext()) {
                return SafeFuture.completedFuture(false);
              }
              final List<Validator> batch = batchedValidatorsIterator.next();
              final int currentBatch = batchCounter.incrementAndGet();
              LOG.debug(
                  "Starting to process validators registration batch {}/{}",
                  currentBatch,
                  batchedValidators.size());
              return createValidatorRegistrations(batch)
                  .thenCompose(this::sendValidatorRegistrations)
                  .thenApply(
                      size -> {
                        successfullySentRegistrations.updateAndGet(count -> count + size);
                        LOG.debug(
                            "Batch {}/{}: {} validator(s) registrations were sent to the Beacon Node out of {} validators.",
                            currentBatch,
                            batchedValidators.size(),
                            size,
                            batch.size());
                        return true;
                      });
            })
        .alwaysRun(
            () ->
                VALIDATOR_LOGGER.validatorRegistrationsSentToTheBuilderNetwork(
                    successfullySentRegistrations.get(), validators.size()));
  }

  private List<Validator> filterActiveAndPendingValidators(
      final Map<BLSPublicKey, ValidatorStatus> statuses) {
    final Function<Validator, BLSPublicKey> getKey =
        validator ->
            validatorRegistrationPropertiesProvider
                .getBuilderRegistrationPublicKeyOverride(validator.getPublicKey())
                .orElse(validator.getPublicKey());
    return ownedValidators.getActiveValidators().stream()
        .filter(
            validator ->
                Optional.ofNullable(statuses.get(getKey.apply(validator)))
                    .map(status -> !status.hasExited())
                    .orElse(false))
        .toList();
  }

  private SafeFuture<List<SignedValidatorRegistration>> createValidatorRegistrations(
      final List<Validator> validators) {
    final Stream<SafeFuture<SignedValidatorRegistration>> validatorRegistrationsFutures =
        validators.stream()
            .map(
                validator -> {
                  final Optional<SafeFuture<SignedValidatorRegistration>>
                      maybeSignedValidatorRegistration =
                          validatorRegistrationSigningService.createSignedValidatorRegistration(
                              validator,
                              Optional.ofNullable(
                                  cachedValidatorRegistrations.get(validator.getPublicKey())),
                              throwable -> {
                                final String errorMessage =
                                    String.format(
                                        "Exception while creating a validator registration for %s. Creation will be attempted again next epoch.",
                                        validator.getPublicKey());
                                LOG.warn(errorMessage, throwable);
                              });
                  return Pair.of(validator.getPublicKey(), maybeSignedValidatorRegistration);
                })
            .filter(pair -> pair.getRight().isPresent())
            .map(
                pair -> {
                  final SafeFuture<SignedValidatorRegistration> registrationFuture =
                      pair.getRight().get();
                  return registrationFuture.thenPeek(
                      registration ->
                          cachedValidatorRegistrations.put(pair.getLeft(), registration));
                });
    return SafeFuture.collectAllSuccessful(validatorRegistrationsFutures);
  }

  private SafeFuture<Integer> sendValidatorRegistrations(
      final List<SignedValidatorRegistration> validatorRegistrations) {
    final SszList<SignedValidatorRegistration> sszValidatorRegistrations =
        SszUtils.toSszList(
            ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA, validatorRegistrations);
    return validatorApiChannel
        .registerValidators(sszValidatorRegistrations)
        .thenApply(__ -> validatorRegistrations.size());
  }

  private void cleanupCache(final List<Validator> managedValidators) {
    if (cachedValidatorRegistrations.isEmpty()
        || cachedValidatorRegistrations.size() == managedValidators.size()) {
      return;
    }

    final Set<BLSPublicKey> managedValidatorsPublicKeys =
        managedValidators.stream()
            .map(Validator::getPublicKey)
            .collect(Collectors.toCollection(HashSet::new));

    cachedValidatorRegistrations
        .keySet()
        .removeIf(
            cachedPublicKey -> {
              final boolean requiresRemoving =
                  !managedValidatorsPublicKeys.contains(cachedPublicKey);
              if (requiresRemoving) {
                LOG.debug(
                    "Removing cached registration for {} because validator is no longer owned.",
                    cachedPublicKey);
              }
              return requiresRemoving;
            });
  }
}
