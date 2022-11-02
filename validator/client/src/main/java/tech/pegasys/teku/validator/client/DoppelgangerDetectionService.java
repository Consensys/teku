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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private final Duration doppelgangerCheckDelay = Duration.ofSeconds(12);
  private final AsyncRunner asyncRunner;
  private final ValidatorApiChannel validatorApiChannel;
  private final ValidatorIndexProvider validatorIndexProvider;
  private final Spec spec;
  private final TimeProvider timeProvider;
  private final GenesisDataProvider genesisDataProvider;

  private Optional<UInt64> epochAtStart = Optional.empty();
  private volatile Cancellable doppelgangerDetectionTask;

  private final AtomicBoolean doppelgangerCheckFinished;

  public DoppelgangerDetectionService(
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final ValidatorIndexProvider validatorIndexProvider,
      final Spec spec,
      final TimeProvider timeProvider,
      final GenesisDataProvider genesisDataProvider) {
    this.asyncRunner = asyncRunner;
    this.validatorApiChannel = validatorApiChannel;
    this.validatorIndexProvider = validatorIndexProvider;
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.genesisDataProvider = genesisDataProvider;
    this.doppelgangerCheckFinished = new AtomicBoolean(false);
  }

  @Override
  protected SafeFuture<?> doStart() {
    doppelgangerDetectionTask =
        asyncRunner.runWithFixedDelay(
            this::checkValidatorsDoppelganger,
            doppelgangerCheckDelay,
            doppelgangerCheckDelay,
            throwable -> LOGGER.error("Error while checking validators doppelgangers.", throwable));
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
    return genesisDataProvider
        .getGenesisTime()
        .thenCompose(
            genesisTime -> {
              final UInt64 currentEpoch =
                  spec.computeEpochAtSlot(
                      spec.getCurrentSlot(timeProvider.getTimeInMillis(), genesisTime));
              if (epochAtStart.isEmpty()) {
                epochAtStart = Optional.of(currentEpoch);
              }

              if (currentEpoch.minus(epochAtStart.get()).isGreaterThan(2)) {
                return stop().thenRun(() -> doppelgangerCheckFinished.set(true));
              }

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
                                              .collect(Collectors.toList())))
                              .exceptionally(
                                  throwable -> {
                                    LOGGER.error(
                                        "Unable to check validators doppelganger.", throwable);
                                    return false;
                                  })
                              .thenApply(
                                  doppelgangerDetected -> {
                                    if (doppelgangerDetected) {
                                      LOGGER.fatal(
                                          "Doppelganger detected. Shutting down Validator Client.");
                                      System.exit(1);
                                    }
                                    return null;
                                  }));
            });
  }

  private boolean doppelgangerDetected(
      final Optional<List<ValidatorLivenessAtEpoch>> validatorLivenessAtEpoches,
      final List<UInt64> validatorIndices) {
    if (validatorLivenessAtEpoches.isPresent()) {
      List<ValidatorLivenessAtEpoch> doppelgangers =
          validatorLivenessAtEpoches.get().stream()
              .filter(
                  validatorLivenessAtEpoch ->
                      validatorIndices.contains(validatorLivenessAtEpoch.getIndex()))
              .collect(Collectors.toList());
      if (!doppelgangers.isEmpty()) {
        LOGGER.fatal(
            "Doppelganger detected. Validators indices: {}",
            doppelgangers.stream()
                .map(validatorLivenessAtEpoch -> validatorLivenessAtEpoch.getIndex().toString())
                .collect(Collectors.joining(",")));
        return true;
      }
    }
    return false;
  }
}
