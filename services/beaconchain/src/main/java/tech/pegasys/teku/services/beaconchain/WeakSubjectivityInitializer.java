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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
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
                throw new IllegalStateException("Failed to load initial state", e);
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
                      "Ignoring weak subjectivity checkpoint which is prior to configured initial state");
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

  /**
   * @param client The local chain data
   * @param anchor The configured anchor
   * @param storageQueryChannel The storage query channel
   * @return A future that completes successfully if the anchor is consitent with the stored chain
   *     data, otherwise returns an exceptional future
   */
  public SafeFuture<Void> assertInitialAnchorIsConsistentWithExistingData(
      final RecentChainData client,
      final AnchorPoint anchor,
      final StorageQueryChannel storageQueryChannel) {
    return SafeFuture.of(
        () -> {
          if (client.isPreGenesis()) {
            // Nothing to do
            return SafeFuture.COMPLETE;
          }
          final Checkpoint finalizedCheckpoint = client.getFinalizedCheckpoint().orElseThrow();

          // For now, disallow fast-forwarding an existing chain with a new anchor
          if (anchor.getEpoch().isGreaterThan(finalizedCheckpoint.getEpoch())) {
            throw new IllegalStateException(
                "Cannot set future initial state for an existing database.");
          }

          // Validate state is consistent with stored data
          if (anchor.getEpoch().equals(finalizedCheckpoint.getEpoch())) {
            if (!anchor.getRoot().equals(finalizedCheckpoint.getRoot())) {
              throw new IllegalStateException(
                  "Configured initial state is incompatible with stored latest finalized checkpoint.");
            }
          } else {
            // Look up historical chain data to check for consistency
            return storageQueryChannel
                .getLatestFinalizedBlockAtSlot(anchor.getEpochStartSlot())
                .thenCompose(
                    maybeBlock -> {
                      final SafeFuture<Optional<BeaconBlockSummary>> summary;
                      if (maybeBlock.isPresent()) {
                        summary = SafeFuture.completedFuture(maybeBlock.map(a -> a));
                      } else {
                        // If block is unavailable, try looking up the corresponding state
                        summary =
                            storageQueryChannel
                                .getLatestFinalizedStateAtSlot(anchor.getEpochStartSlot())
                                .thenApply(
                                    state -> state.map(BeaconBlockHeader::fromState).map(a -> a));
                      }
                      return summary;
                    })
                .thenApply(
                    blockSummaryAtAnchor -> {
                      if (blockSummaryAtAnchor.isEmpty()) {
                        // We must have moved passed the anchor point and not saved its state, just
                        // log a warning
                        LOG.warn(
                            "Ignoring configured initial state. Local database is already initialized and cannot be validated against the configured state (slot={}, blockRoot={}, stateRoot={}).",
                            anchor.getBlockSlot(),
                            anchor.getRoot(),
                            anchor.getStateRoot());
                        return null;
                      }
                      final Optional<Bytes32> storedBlockRoot =
                          blockSummaryAtAnchor.map(BeaconBlockSummary::getRoot);
                      final boolean storedBlockMatchesAnchor =
                          storedBlockRoot.map(r -> r.equals(anchor.getRoot())).orElse(false);
                      if (!storedBlockMatchesAnchor) {
                        throw new IllegalStateException(
                            "Configured initial state does not match stored block at epoch "
                                + anchor.getEpoch()
                                + ": "
                                + storedBlockRoot.map(Object::toString).orElse("(empty)"));
                      }
                      return null;
                    });
          }

          return SafeFuture.COMPLETE;
        });
  }
}
