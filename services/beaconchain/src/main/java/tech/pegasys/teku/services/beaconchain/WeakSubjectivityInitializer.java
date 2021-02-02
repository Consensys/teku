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

package tech.pegasys.teku.services.beaconchain;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

class WeakSubjectivityInitializer {

  private static final Logger LOG = LogManager.getLogger();

  public Optional<AnchorPoint> loadInitialAnchorPoint(final WeakSubjectivityConfig config) {
    return config
        .getWeakSubjectivityStateResource()
        .map(
            wsStateResource -> {
              try {
                STATUS_LOG.loadingInitialStateResource(wsStateResource);
                final BeaconState state = ChainDataLoader.loadState(wsStateResource);
                final AnchorPoint anchor = AnchorPoint.fromInitialState(state);
                STATUS_LOG.loadedInitialStateResource(
                    state.hashTreeRoot(),
                    anchor.getRoot(),
                    state.getSlot(),
                    anchor.getEpoch(),
                    anchor.getEpochStartSlot());
                return anchor;
              } catch (IOException e) {
                LOG.error("Failed to load initial state", e);
                throw new InvalidConfigurationException(
                    "Failed to load initial state from " + wsStateResource + ": " + e.getMessage());
              }
            });
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

  public void validateInitialAnchor(final AnchorPoint initialAnchor, final UInt64 currentSlot) {
    if (initialAnchor.isGenesis()) {
      // Skip extra validations for genesis state
      return;
    }

    final UInt64 slotsBetweenBlockAndEpochStart =
        initialAnchor.getEpochStartSlot().minus(initialAnchor.getBlockSlot());
    final UInt64 anchorEpoch = initialAnchor.getEpoch();
    final UInt64 currentEpoch = compute_epoch_at_slot(currentSlot);

    if (initialAnchor.getBlockSlot().isGreaterThanOrEqualTo(currentSlot)) {
      throw new IllegalStateException(
          String.format(
              "The provided initial state appears to be from a future slot (%s). Please check that the initial state corresponds to a finalized checkpoint on the target chain.",
              initialAnchor.getBlockSlot()));
    } else if (anchorEpoch.plus(2).isGreaterThan(currentEpoch)) {
      throw new IllegalStateException(
          "The provided initial state is too recent. Please check that the initial state corresponds to a finalized checkpoint.");
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
