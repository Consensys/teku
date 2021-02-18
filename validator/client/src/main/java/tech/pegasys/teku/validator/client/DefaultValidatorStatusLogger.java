/*
 * Copyright 2020 ConsenSys AG.
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class DefaultValidatorStatusLogger implements ValidatorStatusLogger {

  private static final int VALIDATOR_KEYS_PRINT_LIMIT = 20;
  private static final Duration INITIAL_STATUS_CHECK_RETRY_PERIOD = Duration.ofSeconds(5);

  final OwnedValidators validators;
  final ValidatorApiChannel validatorApiChannel;
  final AtomicReference<Map<BLSPublicKey, ValidatorStatus>> latestValidatorStatuses =
      new AtomicReference<>();
  final AsyncRunner asyncRunner;
  final AtomicBoolean startupComplete = new AtomicBoolean(false);

  public DefaultValidatorStatusLogger(
      OwnedValidators validators,
      ValidatorApiChannel validatorApiChannel,
      AsyncRunner asyncRunner) {
    this.validators = validators;
    this.validatorApiChannel = validatorApiChannel;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public SafeFuture<Void> printInitialValidatorStatuses() {
    return validatorApiChannel
        .getValidatorStatuses(validators.getPublicKeys())
        .thenCompose(
            maybeValidatorStatuses -> {
              if (maybeValidatorStatuses.isEmpty()) {
                return retryInitialValidatorStatusCheck();
              }

              Map<BLSPublicKey, ValidatorStatus> validatorStatuses = maybeValidatorStatuses.get();
              latestValidatorStatuses.set(validatorStatuses);
              if (validators.size() < VALIDATOR_KEYS_PRINT_LIMIT) {
                printValidatorStatusesOneByOne(validatorStatuses);
              } else {
                printValidatorStatusSummary(validatorStatuses);
              }

              startupComplete.set(true);
              return SafeFuture.completedFuture(null);
            })
        .exceptionallyCompose((__) -> retryInitialValidatorStatusCheck());
  }

  private SafeFuture<Void> retryInitialValidatorStatusCheck() {
    return asyncRunner.runAfterDelay(
        this::printInitialValidatorStatuses, INITIAL_STATUS_CHECK_RETRY_PERIOD);
  }

  private void printValidatorStatusesOneByOne(
      Map<BLSPublicKey, ValidatorStatus> validatorStatuses) {
    for (BLSPublicKey publicKey : validators.getPublicKeys()) {
      Optional<ValidatorStatus> maybeValidatorStatus =
          Optional.ofNullable(validatorStatuses.get(publicKey));
      maybeValidatorStatus.ifPresentOrElse(
          validatorStatus ->
              STATUS_LOG.validatorStatus(publicKey.toAbbreviatedString(), validatorStatus.name()),
          () -> STATUS_LOG.unableToRetrieveValidatorStatus(publicKey.toAbbreviatedString()));
    }
  }

  private void printValidatorStatusSummary(Map<BLSPublicKey, ValidatorStatus> validatorStatuses) {
    Map<ValidatorStatus, AtomicInteger> validatorStatusCount = new HashMap<>();
    final AtomicInteger unknownValidatorCountReference = new AtomicInteger(0);
    for (BLSPublicKey publicKey : validators.getPublicKeys()) {
      Optional<ValidatorStatus> maybeValidatorStatus =
          Optional.ofNullable(validatorStatuses.get(publicKey));
      maybeValidatorStatus.ifPresentOrElse(
          status -> {
            AtomicInteger count =
                validatorStatusCount.computeIfAbsent(status, __ -> new AtomicInteger(0));
            count.incrementAndGet();
          },
          unknownValidatorCountReference::incrementAndGet);
    }

    for (Map.Entry<ValidatorStatus, AtomicInteger> statusCount : validatorStatusCount.entrySet()) {
      STATUS_LOG.validatorStatusSummary(statusCount.getValue().get(), statusCount.getKey().name());
    }

    final int unknownValidatorCount = unknownValidatorCountReference.get();
    if (unknownValidatorCount > 0) {
      STATUS_LOG.unableToRetrieveValidatorStatusSummary(unknownValidatorCountReference.get());
    }
  }

  @Override
  public void checkValidatorStatusChanges() {
    if (!startupComplete.get()) {
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

              Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses =
                  maybeNewValidatorStatuses.get();

              Map<BLSPublicKey, ValidatorStatus> oldValidatorStatuses =
                  latestValidatorStatuses.getAndSet(newValidatorStatuses);
              if (oldValidatorStatuses == null) {
                return;
              }

              for (BLSPublicKey key : oldValidatorStatuses.keySet()) {
                ValidatorStatus oldStatus = oldValidatorStatuses.get(key);
                ValidatorStatus newStatus = newValidatorStatuses.get(key);
                if (oldStatus.equals(newStatus)) {
                  continue;
                }

                STATUS_LOG.validatorStatusChange(
                    oldStatus.name(), newStatus.name(), key.toAbbreviatedString());
              }
            })
        .reportExceptions();
  }
}
