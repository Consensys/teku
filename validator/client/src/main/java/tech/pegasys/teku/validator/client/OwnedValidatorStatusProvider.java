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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class OwnedValidatorStatusProvider implements ValidatorStatusProvider {
  private static final Logger LOG = LogManager.getLogger();

  private static final Duration INITIAL_STATUS_CHECK_RETRY_PERIOD = Duration.ofSeconds(5);

  private final OwnedValidators validators;
  private final ValidatorApiChannel validatorApiChannel;
  private final AtomicReference<Map<BLSPublicKey, ValidatorStatus>> latestValidatorStatuses =
      new AtomicReference<>();
  private final AsyncRunner asyncRunner;
  private final AtomicBoolean startupComplete = new AtomicBoolean(false);
  private final AtomicBoolean lookupInProgress = new AtomicBoolean(false);
  private final SettableLabelledGauge localValidatorCounts;
  private final AtomicReference<UInt64> lastRunEpoch = new AtomicReference<>();
  private final AtomicReference<UInt64> currentEpoch = new AtomicReference<>();
  private final Spec spec;

  private final Subscribers<ValidatorStatusSubscriber> validatorStatusSubscribers =
      Subscribers.create(true);

  public OwnedValidatorStatusProvider(
      final MetricsSystem metricsSystem,
      final OwnedValidators validators,
      final ValidatorApiChannel validatorApiChannel,
      final Spec spec,
      final AsyncRunner asyncRunner) {
    this.validators = validators;
    this.validatorApiChannel = validatorApiChannel;
    this.asyncRunner = asyncRunner;
    this.spec = spec;
    this.localValidatorCounts =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR,
            "local_validator_counts",
            "Current number of validators running in this validator client labelled by current status",
            "status");
  }

  @Override
  public SafeFuture<Void> start() {
    return initValidatorStatuses();
  }

  @Override
  public void onSlot(UInt64 slot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    currentEpoch.set(epoch);
    final UInt64 firstSlotOfEpoch = spec.computeStartSlotAtEpoch(epoch);
    if (slot.equals(firstSlotOfEpoch.plus(1))) {
      updateValidatorStatuses();
    }
  }

  @Override
  public void onHeadUpdate(
      UInt64 slot,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      Bytes32 headBlockRoot) {}

  @Override
  public void onPossibleMissedEvents() {
    updateValidatorStatuses();
  }

  @Override
  public void onValidatorsAdded() {
    updateValidatorStatuses();
  }

  @Override
  public void onBlockProductionDue(UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(UInt64 slot) {}

  @Override
  public void subscribeNewValidatorStatuses(final ValidatorStatusSubscriber subscriber) {
    validatorStatusSubscribers.subscribe(subscriber);
  }

  private SafeFuture<Void> initValidatorStatuses() {
    if (validators.hasNoValidators()) {
      return SafeFuture.COMPLETE;
    }
    if (currentEpoch.get() == null) {
      LOG.debug("Delaying Validator status checking, currentEpoch not initialized yet");
      return retryInitialValidatorStatusCheck();
    }

    if (!lookupInProgress.compareAndSet(false, true)) {
      LOG.warn("Validator status lookup is still in progress. Will skip retrying.");
      return retryInitialValidatorStatusCheck();
    }
    // All validators are set to `unknown` until explicitly updated otherwise
    localValidatorCounts.set(validators.getValidatorCount(), "unknown");
    return validatorApiChannel
        .getValidatorStatuses(validators.getPublicKeys())
        .thenCompose(
            maybeValidatorStatuses -> {
              if (maybeValidatorStatuses.isEmpty()) {
                lookupInProgress.set(false);
                return retryInitialValidatorStatusCheck();
              }
              onNewValidatorStatuses(maybeValidatorStatuses.get(), true);
              startupComplete.set(true);
              lookupInProgress.set(false);
              return SafeFuture.COMPLETE;
            })
        .exceptionallyCompose(
            (__) -> {
              lookupInProgress.set(false);
              return retryInitialValidatorStatusCheck();
            });
  }

  private SafeFuture<Void> retryInitialValidatorStatusCheck() {
    return asyncRunner.runAfterDelay(
        this::initValidatorStatuses, INITIAL_STATUS_CHECK_RETRY_PERIOD);
  }

  public void updateValidatorStatuses() {
    if (!startupComplete.get() || validators.hasNoValidators()) {
      return;
    }
    if (!lookupInProgress.compareAndSet(false, true)) {
      LOG.warn("Validator status lookup is still in progress. Skipping update.");
      return;
    }

    if (needToUpdateAllStatuses()) {
      validatorApiChannel
          .getValidatorStatuses(validators.getPublicKeys())
          .thenAccept(
              maybeNewValidatorStatuses -> {
                if (maybeNewValidatorStatuses.isEmpty()) {
                  STATUS_LOG.unableToRetrieveValidatorStatusesFromBeaconNode();
                  return;
                }
                onNewValidatorStatuses(maybeNewValidatorStatuses.get(), true);
              })
          .alwaysRun(() -> lookupInProgress.set(false))
          .finish(error -> LOG.error("Failed to update validator statuses", error));
    } else {
      final Set<BLSPublicKey> keysToUpdate =
          validators.getPublicKeys().stream()
              .filter(key -> !latestValidatorStatuses.get().containsKey(key))
              .collect(Collectors.toSet());
      if (keysToUpdate.isEmpty()) {
        return;
      }
      validatorApiChannel
          .getValidatorStatuses(keysToUpdate)
          .thenAccept(
              maybeNewValidatorStatuses -> {
                if (maybeNewValidatorStatuses.isEmpty()) {
                  return;
                }
                final Map<BLSPublicKey, ValidatorStatus> newStatuses =
                    new HashMap<>(maybeNewValidatorStatuses.get());
                final Map<BLSPublicKey, ValidatorStatus> oldStatuses =
                    Optional.ofNullable(latestValidatorStatuses.get()).orElse(Map.of());
                newStatuses.putAll(oldStatuses);
                onNewValidatorStatuses(newStatuses, false);
              })
          .alwaysRun(() -> lookupInProgress.set(false))
          .finish(error -> LOG.error("Failed to update validator statuses", error));
    }
  }

  private void onNewValidatorStatuses(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses,
      final boolean updateLastRunEpoch) {
    latestValidatorStatuses.getAndSet(newValidatorStatuses);
    validatorStatusSubscribers.forEach(s -> s.onValidatorStatuses(newValidatorStatuses));
    if (updateLastRunEpoch) {
      lastRunEpoch.set(currentEpoch.get());
    }
    updateValidatorCountMetrics(newValidatorStatuses);
  }

  private boolean needToUpdateAllStatuses() {
    if (lastRunEpoch.get() == null) {
      return true;
    }
    return currentEpoch.get().isGreaterThan(lastRunEpoch.get());
  }

  private void updateValidatorCountMetrics(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses) {
    final Map<ValidatorStatus, Long> validatorCountByStatus =
        newValidatorStatuses.values().stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    Stream.of(ValidatorStatus.values())
        .forEach(
            status -> {
              final long validatorCount = validatorCountByStatus.getOrDefault(status, 0L);
              localValidatorCounts.set(validatorCount, status.name());
            });
    // Validator statuses are read from chainProvider, so `oldValidatorStatuses` contains only the
    // subset of validators already seen on chain (with status pending*, active_*, exited_* and
    // withdrawal_*). Unknown validators are calculated by subtraction.
    localValidatorCounts.set(
        validators.getValidatorCount() - newValidatorStatuses.size(), "unknown");
  }
}
