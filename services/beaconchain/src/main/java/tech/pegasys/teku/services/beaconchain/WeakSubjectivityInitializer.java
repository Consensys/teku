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

package tech.pegasys.teku.services.beaconchain;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.INITIAL_STATE_URL_PATH;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class WeakSubjectivityInitializer {

  private static final Logger LOG = LogManager.getLogger();

  public Optional<AnchorPoint> loadInitialAnchorPoint(
      final Spec spec, final Optional<String> initialStateResource) {
    return initialStateResource.map(
        stateResource -> {
          final String sanitizedResource = UrlSanitizer.sanitizePotentialUrl(stateResource);
          try {
            return getAnchorPoint(spec, stateResource, sanitizedResource);
          } catch (IOException | SszDeserializeException e) {
            LOG.error(
                String.format("Failed to load initial state from %s : ", sanitizedResource), e);
            if (!UrlSanitizer.urlContainsNonEmptyPath(stateResource)) {
              String stateResourceWithPath =
                  stateResource.endsWith("/")
                      ? stateResource + INITIAL_STATE_URL_PATH
                      : stateResource + "/" + INITIAL_STATE_URL_PATH;
              String sanitizedResourceWithPath =
                  UrlSanitizer.sanitizePotentialUrl(stateResourceWithPath);
              LOG.info(
                  String.format(
                      "Trying to load initial state from %s instead", sanitizedResourceWithPath));
              try {
                return getAnchorPoint(spec, stateResourceWithPath, sanitizedResourceWithPath);
              } catch (IOException | SszDeserializeException ex) {
                throw new InvalidConfigurationException(
                    String.format(
                        "Failed to load initial state from both %s and %s : %s",
                        sanitizedResource, sanitizedResourceWithPath, e.getMessage()));
              }
            } else {
              throw new InvalidConfigurationException(
                  String.format(
                      "Failed to load initial state from %s : %s",
                      sanitizedResource, e.getMessage()));
            }
          }
        });
  }

  @NotNull
  private AnchorPoint getAnchorPoint(Spec spec, String stateResource, String sanitizedResource)
      throws IOException {
    STATUS_LOG.loadingInitialStateResource(sanitizedResource);
    final BeaconState state = ChainDataLoader.loadState(spec, stateResource);
    final AnchorPoint anchor = AnchorPoint.fromInitialState(spec, state);
    STATUS_LOG.loadedInitialStateResource(
        state.hashTreeRoot(),
        anchor.getRoot(),
        state.getSlot(),
        anchor.getEpoch(),
        anchor.getEpochStartSlot());
    return anchor;
  }

  public SafeFuture<WeakSubjectivityConfig> finalizeAndStoreConfig(
      final WeakSubjectivityConfig config,
      StorageQueryChannel storageQueryChannel,
      StorageUpdateChannel storageUpdateChannel) {
    return storageQueryChannel
        .getWeakSubjectivityState()
        .thenCompose(
            storedState -> {
              WeakSubjectivityConfig updatedConfig = config;

              final Optional<Checkpoint> storedWsCheckpoint = storedState.getCheckpoint();
              Optional<Checkpoint> newWsCheckpoint = updatedConfig.getWeakSubjectivityCheckpoint();

              // Reconcile supplied config with stored configuration
              Optional<WeakSubjectivityConfig> configToPersist = Optional.empty();
              if (newWsCheckpoint.isPresent()
                  && !Objects.equals(storedWsCheckpoint, newWsCheckpoint)) {
                // We have a new ws checkpoint, so we need to persist it
                configToPersist = Optional.of(updatedConfig);
              } else if (storedState.getCheckpoint().isPresent()) {
                // We haven't supplied a new ws checkpoint, so use the stored value
                updatedConfig =
                    updatedConfig.updated(
                        b -> b.weakSubjectivityCheckpoint(storedState.getCheckpoint()));
              }

              final WeakSubjectivityConfig finalizedConfig = updatedConfig;

              // Persist changes as necessary
              if (configToPersist.isPresent()) {
                final Checkpoint updatedCheckpoint =
                    configToPersist.get().getWeakSubjectivityCheckpoint().orElseThrow();

                // Persist changes
                LOG.info("Update stored weak subjectivity checkpoint to: {}", updatedCheckpoint);
                WeakSubjectivityUpdate update =
                    WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(updatedCheckpoint);
                return storageUpdateChannel
                    .onWeakSubjectivityUpdate(update)
                    .thenApply(__ -> finalizedConfig);
              }

              return SafeFuture.completedFuture(finalizedConfig);
            });
  }

  public void validateInitialAnchor(
      final AnchorPoint initialAnchor, final UInt64 currentSlot, final Spec spec) {
    if (initialAnchor.isGenesis()) {
      // Skip extra validations for genesis state
      return;
    }

    final UInt64 slotsBetweenBlockAndEpochStart =
        initialAnchor.getEpochStartSlot().minus(initialAnchor.getBlockSlot());
    final UInt64 anchorEpoch = initialAnchor.getEpoch();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);

    if (initialAnchor.getBlockSlot().isGreaterThanOrEqualTo(currentSlot)) {
      throw new IllegalStateException(
          String.format(
              "The provided initial state appears to be from a future slot (%s). Please check that the initial state corresponds to a finalized checkpoint on the target chain.",
              initialAnchor.getBlockSlot()));
    } else if (anchorEpoch.plus(2).isGreaterThan(currentEpoch)) {
      throw new IllegalStateException(
          "The provided initial state is too recent. Please check that the initial state corresponds to a finalized checkpoint.");
    }

    Fork expectedFork = spec.getForkSchedule().getFork(initialAnchor.getEpoch());
    Fork loadedFork = initialAnchor.getState().getFork();
    if (!expectedFork.equals(loadedFork)) {
      throw new InvalidConfigurationException(
          "The fork in loaded state does not match fork at the epoch from ForkSchedule. Please check that network in configuration matches the loaded state.");
    }

    if (slotsBetweenBlockAndEpochStart.isGreaterThan(UInt64.ZERO)) {
      Level level = slotsBetweenBlockAndEpochStart.isGreaterThan(2) ? Level.WARN : Level.INFO;
      STATUS_LOG.warnOnInitialStateWithSkippedSlots(
          level,
          initialAnchor.getSlot(),
          initialAnchor.getEpoch(),
          initialAnchor.getEpochStartSlot());
    }
  }
}
