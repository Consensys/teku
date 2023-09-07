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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class DefaultValidatorStatusProvider implements ValidatorStatusProvider {
  private static final Logger LOG = LogManager.getLogger();

  private static final Duration INITIAL_STATUS_CHECK_RETRY_PERIOD = Duration.ofSeconds(5);

  private final OwnedValidators validators;
  private final ValidatorApiChannel validatorApiChannel;
  private final ValidatorStatusesChannel validatorStatusesChannel;
  private final AtomicReference<Map<BLSPublicKey, ValidatorStatus>> latestValidatorStatuses =
      new AtomicReference<>();
  private final AsyncRunner asyncRunner;
  private final AtomicBoolean startupComplete = new AtomicBoolean(false);
  private final SettableLabelledGauge localValidatorCounts;

  public DefaultValidatorStatusProvider(
      final MetricsSystem metricsSystem,
      final OwnedValidators validators,
      final ValidatorApiChannel validatorApiChannel,
      final ValidatorStatusesChannel validatorStatusesChannel,
      final AsyncRunner asyncRunner) {
    this.validators = validators;
    this.validatorApiChannel = validatorApiChannel;
    this.asyncRunner = asyncRunner;
    this.validatorStatusesChannel = validatorStatusesChannel;
    this.localValidatorCounts =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR,
            "local_validator_counts",
            "Current number of validators running in this validator client labelled by current status",
            "status");
  }

  @Override
  public SafeFuture<Void> initValidatorStatuses() {
    if (validators.hasNoValidators()) {
      return SafeFuture.COMPLETE;
    }
    // All validators are set to `unknown` until explicitly updated otherwise
    localValidatorCounts.set(validators.getValidatorCount(), "unknown");
    return validatorApiChannel
        .getValidatorStatuses(validators.getPublicKeys())
        .thenCompose(
            maybeValidatorStatuses -> {
              if (maybeValidatorStatuses.isEmpty()) {
                return retryInitialValidatorStatusCheck();
              }

              final Map<BLSPublicKey, ValidatorStatus> validatorStatuses =
                  maybeValidatorStatuses.get();
              latestValidatorStatuses.set(validatorStatuses);
              updateValidatorCountMetrics(validatorStatuses);

              validatorStatusesChannel.onNewValidatorStatuses(validatorStatuses);
              startupComplete.set(true);
              return SafeFuture.COMPLETE;
            })
        .exceptionallyCompose((__) -> retryInitialValidatorStatusCheck());
  }

  private SafeFuture<Void> retryInitialValidatorStatusCheck() {
    return asyncRunner.runAfterDelay(
        this::initValidatorStatuses, INITIAL_STATUS_CHECK_RETRY_PERIOD);
  }

  @Override
  public void updateValidatorStatuses() {
    if (!startupComplete.get() || validators.hasNoValidators()) {
      return;
    }

    validatorApiChannel
        .getValidatorStatuses(validators.getPublicKeys())
        .thenAccept(
            maybeNewValidatorStatuses -> {
              if (maybeNewValidatorStatuses.isEmpty()) {
                STATUS_LOG.unableToRetrieveValidatorStatusesFromBeaconNode();
                return;
              }

              final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses =
                  maybeNewValidatorStatuses.get();

              final Map<BLSPublicKey, ValidatorStatus> oldValidatorStatuses =
                  latestValidatorStatuses.getAndSet(newValidatorStatuses);
              validatorStatusesChannel.onNewValidatorStatuses(newValidatorStatuses);
              if (oldValidatorStatuses == null) {
                return;
              }
              updateValidatorCountMetrics(newValidatorStatuses);
            })
        .finish(error -> LOG.error("Failed to update validator statuses", error));
  }

  @Override
  public Optional<Map<BLSPublicKey, ValidatorStatus>> getStatuses() {
    return Optional.ofNullable(latestValidatorStatuses.get());
  }

  private void updateValidatorCountMetrics(
      final Map<BLSPublicKey, ValidatorStatus> oldValidatorStatuses) {
    final Map<ValidatorStatus, Long> validatorCountByStatus =
        oldValidatorStatuses.values().stream()
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
        validators.getValidatorCount() - oldValidatorStatuses.size(), "unknown");
  }
}
