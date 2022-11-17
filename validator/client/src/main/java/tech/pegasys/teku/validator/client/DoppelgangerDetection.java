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

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

public class DoppelgangerDetection {

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
  private Optional<SafeFuture<Map<Integer, BLSPublicKey>>> doppelgangerCheckFinished =
      Optional.empty();
  private long startTime;

  public DoppelgangerDetection(
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final ValidatorIndexProvider validatorIndexProvider,
      final Spec spec,
      final TimeProvider timeProvider,
      final GenesisDataProvider genesisDataProvider,
      final Duration checkDelay,
      final Duration timeout) {
    this.asyncRunner = asyncRunner;
    this.validatorApiChannel = validatorApiChannel;
    this.validatorIndexProvider = validatorIndexProvider;
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.genesisDataProvider = genesisDataProvider;
    this.checkDelay = checkDelay;
    this.timeout = timeout;
  }

  protected synchronized SafeFuture<Map<Integer, BLSPublicKey>> performDoppelgangerDetection() {
    LOGGER.info("Starting doppelganger detection service...");
    doppelgangerCheckFinished = Optional.of(new SafeFuture<>());
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
    return doppelgangerCheckFinished.get();
  }

  private synchronized SafeFuture<?> stopDoppelgangerDetection(
      Map<Integer, BLSPublicKey> detectedDoppelgangers) {
    if (doppelgangerCheckFinished.isEmpty()) {
      throw new RuntimeException("Doppelganger Detection is already stopped.");
    }
    doppelgangerCheckFinished.get().complete(detectedDoppelgangers);
    doppelgangerCheckFinished = Optional.empty();
    return SafeFuture.fromRunnable(
        () -> Optional.ofNullable(doppelgangerDetectionTask).ifPresent(Cancellable::cancel));
  }

  private SafeFuture<?> checkValidatorsDoppelganger() {
    Duration duration = Duration.of(System.nanoTime() - startTime, ChronoUnit.NANOS);
    if (duration.compareTo(timeout) > 0) {
      LOGGER.info(
          "Validators Doppelganger Detection timeout reached, stopping the service. Some technical issues prevented the validators doppelganger detection from running correctly. Please check the logs and consider performing a new validators doppelganger check.");
      return stopDoppelgangerDetection(new HashMap<>());
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
                return stopDoppelgangerDetection(new HashMap<>());
              }

              LOGGER.info(
                  "Performing a validators doppelganger check for epoch {}, slot {}",
                  currentEpoch,
                  currentSlot);

              return validatorIndexProvider
                  .getValidatorIndices()
                  .thenCompose(
                      validatorIndices ->
                          checkIndicesLiveness(currentSlot, currentEpoch, validatorIndices))
                  .orTimeout(checkDelay)
                  .exceptionally(
                      throwable -> {
                        LOGGER.error(
                            "Unable to check validators doppelganger. Unable to get validators indices: {}",
                            extractErrorMessage(throwable));
                        return null;
                      })
                  .thenApply(o -> null);
            })
        .orTimeout(checkDelay)
        .exceptionally(
            throwable -> {
              LOGGER.error(
                  "Unable to check validators doppelganger. Unable to get genesis time to calculate the current epoch: {}",
                  extractErrorMessage(throwable));
              return null;
            });
  }

  private SafeFuture<Void> checkIndicesLiveness(
      UInt64 currentSlot, UInt64 currentEpoch, IntCollection validatorIndices) {
    return validatorApiChannel
        .checkValidatorsDoppelganger(mapToUIntIndices(validatorIndices), currentEpoch)
        .thenAccept(
            validatorLivenessAtEpoches ->
                doppelgangerDetected(
                    validatorLivenessAtEpoches,
                    mapToUIntIndices(validatorIndices),
                    currentEpoch,
                    currentSlot))
        .orTimeout(checkDelay)
        .exceptionally(
            throwable -> {
              LOGGER.error(
                  "Unable to check validators doppelganger. Unable to get validators liveness: {}",
                  extractErrorMessage(throwable));
              return null;
            })
        .thenApply(doppelgangerDetected -> null);
  }

  private void doppelgangerDetected(
      final Optional<List<ValidatorLivenessAtEpoch>> validatorLivenessAtEpoches,
      final List<UInt64> validatorIndices,
      final UInt64 epoch,
      final UInt64 slot) {
    List<ValidatorLivenessAtEpoch> liveValidators =
        filterLiveValidators(validatorLivenessAtEpoches, validatorIndices);
    if (!liveValidators.isEmpty()) {
      LOGGER.fatal("Doppelganger detected. Shutting down Validator Client.");
      this.validatorIndexProvider
          .getValidatorIndicesByPublicKey()
          .thenApply(
              blsPublicKeyIntegerMap -> {
                STATUS_LOG.validatorsDoppelgangerDetected(
                    mapLivenessAtEpochToIndicesPubKeysStrings(
                        liveValidators, blsPublicKeyIntegerMap));
                stopDoppelgangerDetection(
                        mapLivenessAtEpochToIndicesPubKeys(
                            liveValidators, Optional.of(blsPublicKeyIntegerMap)))
                    .ifExceptionGetsHereRaiseABug();
                return null;
              })
          .exceptionally(
              error -> {
                LOGGER.error(
                    "Unable to get doppelgangers public keys. Only indices are available: {}",
                    error.getMessage());
                STATUS_LOG.validatorsDoppelgangerDetected(
                    liveValidators.stream()
                        .collect(Collectors.toMap(e -> e.getIndex().intValue(), e -> "")));
                stopDoppelgangerDetection(
                        mapLivenessAtEpochToIndicesPubKeys(liveValidators, Optional.empty()))
                    .ifExceptionGetsHereRaiseABug();
                return null;
              })
          .ifExceptionGetsHereRaiseABug();
    } else {
      LOGGER.info("No validators doppelganger detected for epoch {}, slot {}", epoch, slot);
    }
  }

  private Map<Integer, String> mapLivenessAtEpochToIndicesPubKeysStrings(
      List<ValidatorLivenessAtEpoch> liveValidators,
      Map<BLSPublicKey, Integer> blsPublicKeyIntegerMap) {
    return blsPublicKeyIntegerMap.entrySet().stream()
        .filter(
            pubKeyIndexEntry ->
                liveValidators.stream()
                    .anyMatch(
                        validatorLivenessAtEpoch ->
                            validatorLivenessAtEpoch
                                .getIndex()
                                .equals(UInt64.valueOf(pubKeyIndexEntry.getValue()))))
        .collect(Collectors.toMap(Map.Entry::getValue, e -> e.getKey().toString()));
  }

  private Map<Integer, BLSPublicKey> mapLivenessAtEpochToIndicesPubKeys(
      List<ValidatorLivenessAtEpoch> liveValidators,
      Optional<Map<BLSPublicKey, Integer>> blsPublicKeyIntegerMap) {
    return blsPublicKeyIntegerMap
        .map(
            publicKeyIntegerMap ->
                publicKeyIntegerMap.entrySet().stream()
                    .filter(
                        pubKeyIndexEntry ->
                            liveValidators.stream()
                                .anyMatch(
                                    validatorLivenessAtEpoch ->
                                        validatorLivenessAtEpoch
                                            .getIndex()
                                            .equals(UInt64.valueOf(pubKeyIndexEntry.getValue()))))
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)))
        .orElse(
            liveValidators.stream()
                .collect(
                    Collectors.toMap(e -> e.getIndex().intValue(), e -> BLSPublicKey.empty())));
  }

  private List<ValidatorLivenessAtEpoch> filterLiveValidators(
      Optional<List<ValidatorLivenessAtEpoch>> validatorLivenessAtEpoches,
      List<UInt64> validatorIndices) {
    return validatorLivenessAtEpoches
        .map(
            validatorLiveness ->
                validatorLiveness.stream()
                    .filter(
                        validatorLivenessAtEpoch ->
                            validatorIndices.contains(validatorLivenessAtEpoch.getIndex())
                                && validatorLivenessAtEpoch.isLive())
                    .collect(Collectors.toList()))
        .orElse(new ArrayList<>());
  }

  private String extractErrorMessage(Throwable throwable) {
    return ExceptionUtil.hasCause(throwable, TimeoutException.class)
        ? "Request timeout"
        : throwable.getMessage();
  }

  private List<UInt64> mapToUIntIndices(IntCollection indexes) {
    return indexes.intStream().mapToObj(UInt64::valueOf).collect(Collectors.toList());
  }
}
