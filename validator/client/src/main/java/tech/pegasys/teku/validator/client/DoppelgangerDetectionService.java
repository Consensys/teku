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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

public class DoppelgangerDetectionService extends Service {

  private static final Logger LOGGER = LogManager.getLogger();
  private final Duration checkDelay;
  private final Duration timeout;
  private final AsyncRunner asyncRunner;
  private final ValidatorApiChannel validatorApiChannel;
  private final ValidatorIndexProvider validatorIndexProvider;
  private final Spec spec;
  private final TimeProvider timeProvider;
  private final GenesisDataProvider genesisDataProvider;
  private Optional<UInt64> epochAtStart = Optional.empty();
  private volatile Cancellable doppelgangerDetectionTask;
  private final AtomicBoolean doppelgangerCheckFinished;
  private final Consumer<Void> doppelgangerDetectionAction;
  private long startTime;

  public DoppelgangerDetectionService(
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final ValidatorIndexProvider validatorIndexProvider,
      final Spec spec,
      final TimeProvider timeProvider,
      final GenesisDataProvider genesisDataProvider,
      final Duration checkDelay,
      final Duration timeout,
      final Consumer<Void> doppelgangerDetectionAction) {
    this.asyncRunner = asyncRunner;
    this.validatorApiChannel = validatorApiChannel;
    this.validatorIndexProvider = validatorIndexProvider;
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.genesisDataProvider = genesisDataProvider;
    this.doppelgangerCheckFinished = new AtomicBoolean(false);
    this.checkDelay = checkDelay;
    this.timeout = timeout;
    this.doppelgangerDetectionAction = doppelgangerDetectionAction;
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOGGER.info("Starting doppelganger detection service...");
    startTime = System.nanoTime();
    doppelgangerDetectionTask =
        asyncRunner.runWithFixedDelay(
            this::checkValidatorsDoppelganger,
            checkDelay,
            checkDelay,
            throwable ->
                LOGGER.error(
                    "Error while checking validators doppelganger. The check could not be performed correctly.",
                    throwable));
    while (true) {
      if (doppelgangerCheckFinished.get()) {
        return SafeFuture.COMPLETE;
      }
    }
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.fromRunnable(
        () -> Optional.ofNullable(doppelgangerDetectionTask).ifPresent(Cancellable::cancel));
  }

  private SafeFuture<?> checkValidatorsDoppelganger() {
    Duration duration = Duration.of(System.nanoTime() - startTime, ChronoUnit.NANOS);
    if (duration.compareTo(timeout) > 0) {
      LOGGER.info(
          "Validators Doppelganger Detection timeout reached, stopping the service. Some technical issues prevented the validators doppelganger detection from running correctly. Please check the logs and consider performing a new validators doppelganger check.");
      return stop().thenRun(() -> doppelgangerCheckFinished.set(true));
    }

    return genesisDataProvider
        .getGenesisTime()
        .thenCompose(
            genesisTime -> {
              final UInt64 currentSlot =
                  spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime);
              final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);

              if (epochAtStart.isEmpty()) {
                epochAtStart = Optional.of(currentEpoch);
                LOGGER.info(
                    "Validators doppelganger check started at epoch {}", epochAtStart.get());
              }

              if (currentEpoch.minus(epochAtStart.get()).isGreaterThanOrEqualTo(2)) {
                LOGGER.info(
                    "No validators doppelganger detected after 2 epochs. Stopping doppelganger detection service.");
                return stop().thenRun(() -> doppelgangerCheckFinished.set(true));
              }

              LOGGER.info(
                  "Performing a validators doppelganger check for epoch {}, slot {}.",
                  currentEpoch,
                  currentSlot);

              return validatorIndexProvider
                  .getValidatorIndices()
                  .thenCompose(
                      validatorIndices ->
                          validatorApiChannel
                              .checkValidatorsDoppelganger(
                                  validatorIndices
                                      .intStream()
                                      .mapToObj(UInt64::valueOf)
                                      .collect(Collectors.toList()),
                                  currentEpoch)
                              .thenApply(
                                  validatorLivenessAtEpoches ->
                                      doppelgangerDetected(
                                          validatorLivenessAtEpoches,
                                          validatorIndices
                                              .intStream()
                                              .mapToObj(UInt64::valueOf)
                                              .collect(Collectors.toList()),
                                          currentEpoch,
                                          currentSlot))
                              .exceptionally(
                                  throwable -> {
                                    LOGGER.error(
                                        "Unable to check validators doppelganger. Unable to get validators liveness: {}",
                                        throwable.getMessage());
                                    return false;
                                  })
                              .thenApply(doppelgangerDetected -> null))
                  .exceptionally(
                      throwable -> {
                        LOGGER.error(
                            "Unable to check validators doppelganger. Unable to get validators indices: {}",
                            throwable.getMessage());
                        return null;
                      })
                  .thenApply(o -> null);
            })
        .exceptionally(
            throwable -> {
              LOGGER.error(
                  "Unable to check validators doppelganger. Unable to get genesis time to calculate the current epoch: {}",
                  throwable.getMessage());
              return null;
            });
  }

  private boolean doppelgangerDetected(
      final Optional<List<ValidatorLivenessAtEpoch>> validatorLivenessAtEpoches,
      final List<UInt64> validatorIndices,
      final UInt64 epoch,
      final UInt64 slot) {
    if (validatorLivenessAtEpoches.isPresent()) {
      List<ValidatorLivenessAtEpoch> doppelgangers =
          validatorLivenessAtEpoches.get().stream()
              .filter(
                  validatorLivenessAtEpoch ->
                      validatorIndices.contains(validatorLivenessAtEpoch.getIndex())
                          && validatorLivenessAtEpoch.isLive())
              .collect(Collectors.toList());
      if (!doppelgangers.isEmpty()) {
        LOGGER.fatal("Doppelganger detected. Shutting down Validator Client.");
        this.validatorIndexProvider
            .getValidatorIndicesByPublicKey()
            .thenApply(
                blsPublicKeyIntegerMap -> {
                  Map<Integer, String> doppelgangerPubKeys =
                      blsPublicKeyIntegerMap.entrySet().stream()
                          .filter(
                              pubKeyIndexEntry ->
                                  doppelgangers.stream()
                                      .anyMatch(
                                          validatorLivenessAtEpoch ->
                                              validatorLivenessAtEpoch
                                                  .getIndex()
                                                  .equals(
                                                      UInt64.valueOf(pubKeyIndexEntry.getValue()))))
                          .collect(
                              Collectors.toMap(Map.Entry::getValue, e -> e.getKey().toString()));
                  STATUS_LOG.validatorsDoppelgangerDetected(doppelgangerPubKeys);
                  return null;
                })
            .exceptionally(
                error -> {
                  LOGGER.error(
                      "Unable to get doppelgangers public keys. Only indices are available: {}",
                      error.getMessage());
                  STATUS_LOG.validatorsDoppelgangerDetected(
                      doppelgangers.stream()
                          .collect(Collectors.toMap(e -> e.getIndex().intValue(), e -> "")));
                  return null;
                })
            .ifExceptionGetsHereRaiseABug();
        doppelgangerDetectionAction.accept(null);
        stop().thenRun(() -> doppelgangerCheckFinished.set(true)).ifExceptionGetsHereRaiseABug();
      } else {
        LOGGER.info("No validators doppelganger detected for epoch {}, slot {}", epoch, slot);
      }
    }
    return false;
  }
}
