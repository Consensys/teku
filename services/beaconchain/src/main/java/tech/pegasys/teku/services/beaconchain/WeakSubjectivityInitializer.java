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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

class WeakSubjectivityInitializer {
  private static final Logger LOG = LogManager.getLogger();

  public Optional<AnchorPoint> loadAnchorPoint(final WeakSubjectivityConfig config) {
    return config
        .getWeakSubjectivityStateResource()
        .map(
            wsStateResource -> {
              try {
                final String wsBlockResource =
                    config
                        .getWeakSubjectivityBlockResource()
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    "Weak subjectivity block must be supplied with state"));
                STATUS_LOG.loadingWeakSubjectivityStateResources(wsStateResource, wsBlockResource);
                final BeaconState state = ChainDataLoader.loadState(wsStateResource);
                final SignedBeaconBlock block = ChainDataLoader.loadBlock(wsBlockResource);
                STATUS_LOG.loadedWeakSubjectivityStateResources(
                    state.hashTreeRoot(), block.getRoot(), state.getSlot());
                return AnchorPoint.fromInitialBlockAndState(block, state);
              } catch (IOException e) {
                throw new IllegalStateException(
                    "Failed to load weak subjectivity initial state data", e);
              }
            });
  }

  public SafeFuture<WeakSubjectivityConfig> finalizeAndStoreConfig(
      final WeakSubjectivityConfig config,
      Optional<AnchorPoint> wsAnchor,
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

              // Reconcile ws checkpoint with ws state
              boolean shouldClearStoredState = false;
              final Optional<UInt64> wsAnchorEpoch = wsAnchor.map(AnchorPoint::getEpoch);
              final Optional<UInt64> wsCheckpointEpoch =
                  updatedConfig.getWeakSubjectivityCheckpoint().map(Checkpoint::getEpoch);
              if (wsAnchorEpoch.isPresent()
                  && wsCheckpointEpoch.isPresent()
                  && wsAnchorEpoch.get().isGreaterThanOrEqualTo(wsCheckpointEpoch.get())) {
                // The ws checkpoint is prior to our new anchor, so clear it out
                updatedConfig =
                    updatedConfig.updated(b -> b.weakSubjectivityCheckpoint(Optional.empty()));
                configToPersist = Optional.empty();
                if (newWsCheckpoint.isPresent()) {
                  LOG.info(
                      "Ignoring configured weak subjectivity checkpoint which is prior to supplied weak subjectivity state");
                }
                if (storedWsCheckpoint.isPresent()) {
                  shouldClearStoredState = true;
                }
              }

              final WeakSubjectivityConfig finalizedConfig = updatedConfig;

              // Persist changes as necessary
              if (shouldClearStoredState) {
                // Clear out stored checkpoint
                LOG.info("Clearing stored weak subjectivity checkpoint");
                WeakSubjectivityUpdate update =
                    WeakSubjectivityUpdate.clearWeakSubjectivityCheckpoint();
                return storageUpdateChannel
                    .onWeakSubjectivityUpdate(update)
                    .thenApply(__ -> finalizedConfig);
              } else if (configToPersist.isPresent()) {
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
}
