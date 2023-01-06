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

package tech.pegasys.teku.validator.client.doppelganger;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

public class DoppelgangerDetector {

  private static final Logger LOG = LogManager.getLogger();
  private final StatusLogger statusLog;
  private final Duration checkDelay;
  private final int maxEpochs;
  private final Duration timeout;
  private final AsyncRunner asyncRunner;
  private final ValidatorApiChannel validatorApiChannel;
  private final Spec spec;
  private final TimeProvider timeProvider;
  private final GenesisDataProvider genesisDataProvider;

  public DoppelgangerDetector(
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final Spec spec,
      final TimeProvider timeProvider,
      final GenesisDataProvider genesisDataProvider,
      final Duration checkDelay,
      final Duration timeout,
      final int maxEpochs) {
    this(
        STATUS_LOG,
        asyncRunner,
        validatorApiChannel,
        spec,
        timeProvider,
        genesisDataProvider,
        checkDelay,
        timeout,
        maxEpochs);
  }

  public DoppelgangerDetector(
      final StatusLogger statusLog,
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final Spec spec,
      final TimeProvider timeProvider,
      final GenesisDataProvider genesisDataProvider,
      final Duration checkDelay,
      final Duration timeout,
      final int maxEpochs) {
    this.statusLog = statusLog;
    this.asyncRunner = asyncRunner;
    this.validatorApiChannel = validatorApiChannel;
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.genesisDataProvider = genesisDataProvider;
    this.checkDelay = checkDelay;
    this.timeout = timeout;
    this.maxEpochs = maxEpochs;
  }

  public SafeFuture<Map<UInt64, BLSPublicKey>> performDoppelgangerDetection(
      final Set<BLSPublicKey> pubKeys) {
    if (pubKeys.isEmpty()) {
      LOG.info("Skipping doppelganger detection: No public keys provided to check");
      return SafeFuture.completedFuture(new HashMap<>());
    }
    DoppelgangerDetectionTask doppelgangerDetectionTask =
        new DoppelgangerDetectionTask(timeProvider.getTimeInSeconds(), pubKeys);
    return doppelgangerDetectionTask.performDoppelgangerDetectionTask();
  }

  private class DoppelgangerDetectionTask {

    private volatile Cancellable doppelgangerDetectionTask;
    private Optional<SafeFuture<Map<UInt64, BLSPublicKey>>> doppelgangerCheckFinished =
        Optional.empty();
    private final UInt64 startTime;
    private final Set<BLSPublicKey> pubKeys;
    private Optional<UInt64> epochAtStart = Optional.empty();

    private AtomicBoolean firstCheck = new AtomicBoolean(true);

    public DoppelgangerDetectionTask(final UInt64 startTime, final Set<BLSPublicKey> pubKeys) {
      this.startTime = startTime;
      this.pubKeys = pubKeys;
    }

    protected synchronized SafeFuture<Map<UInt64, BLSPublicKey>>
        performDoppelgangerDetectionTask() {
      statusLog.doppelgangerDetectionStart(
          mapToAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
      doppelgangerCheckFinished = Optional.of(new SafeFuture<>());
      epochAtStart = Optional.empty();
      doppelgangerDetectionTask =
          asyncRunner.runWithFixedDelay(
              () -> performDoppelgangerCheck(pubKeys),
              checkDelay,
              checkDelay,
              throwable ->
                  LOG.error(
                      "Error while checking validators doppelgangers for keys {}. The check could not be performed correctly.",
                      mapToAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")),
                      throwable));
      return doppelgangerCheckFinished.get();
    }

    private Stream<String> mapToAbbreviatedKeys(final Set<BLSPublicKey> pubKeys) {
      return pubKeys.stream().map(BLSPublicKey::toAbbreviatedString);
    }

    private synchronized SafeFuture<Void> stopDoppelgangerDetectorTask(
        final Map<UInt64, BLSPublicKey> detectedDoppelgangers) {
      if (doppelgangerCheckFinished.isEmpty()) {
        throw new RuntimeException("Doppelganger Detection is already stopped");
      }
      epochAtStart = Optional.empty();
      doppelgangerCheckFinished.get().complete(detectedDoppelgangers);
      doppelgangerCheckFinished = Optional.empty();
      return SafeFuture.fromRunnable(
          () -> Optional.ofNullable(doppelgangerDetectionTask).ifPresent(Cancellable::cancel));
    }

    private SafeFuture<Void> performDoppelgangerCheck(final Set<BLSPublicKey> pubKeys) {
      if (timeProvider
          .getTimeInSeconds()
          .minus(startTime)
          .isGreaterThanOrEqualTo(timeout.toSeconds())) {
        statusLog.doppelgangerDetectionTimeout(
            mapToAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
        return stopDoppelgangerDetectorTask(new HashMap<>());
      }

      return genesisDataProvider
          .getGenesisTime()
          .thenCompose(
              genesisTime -> {
                final UInt64 currentSlot =
                    spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime);
                final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);

                captureEpochAtstart(currentEpoch);

                if (maxEpochsReached(currentEpoch)) {
                  statusLog.doppelgangerDetectionEnd(
                      mapToAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
                  return stopDoppelgangerDetectorTask(new HashMap<>());
                } else {
                  if (firstCheck.compareAndSet(true, false)
                      && currentEpoch.isGreaterThan(UInt64.ZERO)) {
                    return checkDoppelgangersAtEpoch(pubKeys, currentEpoch.minus(UInt64.ONE))
                        .thenCompose(
                            __ -> {
                              if (doppelgangerCheckFinished.isPresent()) {
                                return checkDoppelgangersAtEpoch(pubKeys, currentEpoch);
                              }
                              return SafeFuture.COMPLETE;
                            });
                  } else {
                    return checkDoppelgangersAtEpoch(pubKeys, currentEpoch);
                  }
                }
              })
          .orTimeout(checkDelay)
          .exceptionally(
              throwable -> {
                LOG.error(
                    "Unable to check validators doppelgangers for keys {}. Unable to get genesis time to calculate the current epoch: {}",
                    mapToAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")),
                    extractErrorMessage(throwable));
                return null;
              });
    }

    private SafeFuture<Void> checkDoppelgangersAtEpoch(
        final Set<BLSPublicKey> pubKeys, final UInt64 epoch) {
      statusLog.doppelgangerCheck(
          epoch.longValue(), mapToAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));

      return validatorApiChannel
          .getValidatorIndices(pubKeys)
          .thenCompose(
              validatorIndicesByPubKeys ->
                  checkValidatorsLivenessAtEpoch(
                      epoch,
                      pubKeys,
                      validatorIndicesByPubKeys.entrySet().stream()
                          .collect(
                              Collectors.toMap(
                                  Map.Entry::getKey, e -> UInt64.valueOf(e.getValue())))))
          .orTimeout(checkDelay)
          .exceptionally(
              throwable -> {
                LOG.error(
                    "Unable to check validators doppelgangers for keys {}. Unable to get validators indices: {}",
                    mapToAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")),
                    extractErrorMessage(throwable));
                return null;
              })
          .toVoid();
    }

    private void captureEpochAtstart(final UInt64 currentEpoch) {
      if (epochAtStart.isEmpty()) {
        epochAtStart = Optional.of(currentEpoch);
      }
    }

    private boolean maxEpochsReached(final UInt64 currentEpoch) {
      return currentEpoch.minus(epochAtStart.get()).isGreaterThanOrEqualTo(maxEpochs);
    }

    private SafeFuture<Void> checkValidatorsLivenessAtEpoch(
        final UInt64 epoch,
        final Set<BLSPublicKey> pubKeys,
        final Map<BLSPublicKey, UInt64> validatorIndicesByPubKey) {

      if (validatorIndicesByPubKey.isEmpty()) {
        LOG.info(
            "Skipping validators doppelgangers check for public keys {}. No associated indices found. Public keys are inactive",
            mapToAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")));
        return SafeFuture.COMPLETE;
      }

      logMissingIndices(pubKeys, validatorIndicesByPubKey);

      return validatorApiChannel
          .getValidatorsLiveness(new ArrayList<>(validatorIndicesByPubKey.values()), epoch)
          .thenAccept(
              validatorLivenessAtEpoches ->
                  checkValidatorDoppelgangers(validatorLivenessAtEpoches, validatorIndicesByPubKey))
          .orTimeout(checkDelay)
          .exceptionally(
              throwable -> {
                LOG.error(
                    "Unable to check validators doppelgangers for keys {}. Unable to get validators liveness: {}",
                    mapToAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")),
                    extractErrorMessage(throwable));
                return null;
              })
          .thenApply(doppelgangerDetected -> null);
    }

    private void logMissingIndices(
        Set<BLSPublicKey> pubKeys, Map<BLSPublicKey, UInt64> validatorIndicesByPubKey) {
      Set<BLSPublicKey> publicKeysWithoutIndices =
          pubKeys.stream()
              .filter(publicKey -> !validatorIndicesByPubKey.containsKey(publicKey))
              .collect(Collectors.toSet());

      if (!publicKeysWithoutIndices.isEmpty()) {
        LOG.info(
            "Skipping doppelganger check for public keys {}. No associated indices found. Public keys are inactive",
            publicKeysWithoutIndices.stream()
                .map(BLSPublicKey::toAbbreviatedString)
                .collect(Collectors.joining(", ")));
      }
    }

    private void checkValidatorDoppelgangers(
        final Optional<List<ValidatorLivenessAtEpoch>> validatorLivenessAtEpoches,
        final Map<BLSPublicKey, UInt64> validatorsIndicesByPubKey) {
      final List<Pair<BLSPublicKey, ValidatorLivenessAtEpoch>> doppelgangers =
          filterLiveValidators(validatorLivenessAtEpoches, validatorsIndicesByPubKey);
      if (!doppelgangers.isEmpty()) {
        LOG.fatal("Validator doppelganger detected...");
        statusLog.validatorsDoppelgangersDetected(
            mapLivenessAtEpochToIndicesByPubKeyStrings(doppelgangers));
        stopDoppelgangerDetectorTask(mapLivenessAtEpochToIndicesByPubKey(doppelgangers))
            .ifExceptionGetsHereRaiseABug();
      }
    }

    private Map<UInt64, String> mapLivenessAtEpochToIndicesByPubKeyStrings(
        final List<Pair<BLSPublicKey, ValidatorLivenessAtEpoch>> doppelgangers) {
      return doppelgangers.stream()
          .collect(Collectors.toMap(e -> e.getRight().getIndex(), e -> e.getLeft().toString()));
    }

    private Map<UInt64, BLSPublicKey> mapLivenessAtEpochToIndicesByPubKey(
        final List<Pair<BLSPublicKey, ValidatorLivenessAtEpoch>> doppelgangers) {
      return doppelgangers.stream()
          .collect(Collectors.toMap(e -> e.getRight().getIndex(), Pair::getLeft));
    }

    private List<Pair<BLSPublicKey, ValidatorLivenessAtEpoch>> filterLiveValidators(
        final Optional<List<ValidatorLivenessAtEpoch>> validatorLivenessAtEpoches,
        final Map<BLSPublicKey, UInt64> validatorPubKeysByIndices) {
      return validatorLivenessAtEpoches
          .map(
              validatorLiveness ->
                  validatorLiveness.stream()
                      .filter(
                          validatorLivenessAtEpoch ->
                              validatorPubKeysByIndices.containsValue(
                                      validatorLivenessAtEpoch.getIndex())
                                  && validatorLivenessAtEpoch.isLive())
                      .map(
                          validatorLivenessAtEpoch ->
                              Pair.of(
                                  validatorPubKeysByIndices.entrySet().stream()
                                      .filter(
                                          e ->
                                              e.getValue()
                                                  .equals(validatorLivenessAtEpoch.getIndex()))
                                      .findFirst()
                                      .get()
                                      .getKey(),
                                  validatorLivenessAtEpoch))
                      .collect(Collectors.toList()))
          .orElse(new ArrayList<>());
    }

    private String extractErrorMessage(final Throwable throwable) {
      return ExceptionUtil.hasCause(throwable, TimeoutException.class)
          ? "Request timeout"
          : throwable.getMessage();
    }
  }
}
