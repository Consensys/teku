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

package tech.pegasys.teku.storage.server;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.api.ChainStorageFacade;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.server.state.FinalizedStateCache;

public class ChainStorage
    implements StorageUpdateChannel, StorageQueryChannel, VoteUpdateChannel, ChainStorageFacade {
  private static final Logger LOG = LogManager.getLogger();
  private final Database database;
  private final FinalizedStateCache finalizedStateCache;

  private final StateStorageMode dataStorageMode;

  private Optional<OnDiskStoreData> cachedStoreData = Optional.empty();

  private ChainStorage(
      final Database database,
      final FinalizedStateCache finalizedStateCache,
      final StateStorageMode dataStorageMode) {
    this.database = database;
    this.finalizedStateCache = finalizedStateCache;
    this.dataStorageMode = dataStorageMode;
  }

  public static ChainStorage create(
      final Database database,
      final Spec spec,
      final StateStorageMode dataStorageMode,
      int stateRebuildTimeoutSeconds) {
    final int finalizedStateCacheSize = spec.getSlotsPerEpoch(SpecConfig.GENESIS_EPOCH) * 3;
    return new ChainStorage(
        database,
        new FinalizedStateCache(
            spec, database, finalizedStateCacheSize, true, stateRebuildTimeoutSeconds),
        dataStorageMode);
  }

  private synchronized Optional<OnDiskStoreData> getStore() {
    if (cachedStoreData.isEmpty()) {
      // Create store from database
      cachedStoreData = database.createMemoryStore();
    }

    return cachedStoreData;
  }

  private synchronized void handleStoreUpdate() {
    cachedStoreData = Optional.empty();
  }

  @Override
  public SafeFuture<Optional<OnDiskStoreData>> onStoreRequest() {
    if (database == null) {
      return SafeFuture.failedFuture(new IllegalStateException("Database not initialized yet"));
    }

    return SafeFuture.completedFuture(getStore());
  }

  @Override
  public SafeFuture<WeakSubjectivityState> getWeakSubjectivityState() {
    return SafeFuture.of(database::getWeakSubjectivityState);
  }

  @Override
  public SafeFuture<UpdateResult> onStorageUpdate(final StorageUpdate event) {
    return SafeFuture.of(
        () -> {
          final UpdateResult updateResult = database.update(event);
          handleStoreUpdate();
          return updateResult;
        });
  }

  @Override
  public SafeFuture<Void> onFinalizedBlocks(
      final Collection<SignedBeaconBlock> finalizedBlocks,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlot,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot) {
    return SafeFuture.fromRunnable(
        () ->
            database.storeFinalizedBlocks(
                finalizedBlocks, blobSidecarsBySlot, maybeEarliestBlobSidecarSlot));
  }

  @Override
  public SafeFuture<Void> onReconstructedFinalizedState(
      BeaconState finalizedState, Bytes32 blockRoot) {
    return SafeFuture.fromRunnable(
        () -> database.storeReconstructedFinalizedState(finalizedState, blockRoot));
  }

  @Override
  public void onChainInitialized(final AnchorPoint initialAnchor) {
    database.storeInitialAnchor(initialAnchor);
  }

  @Override
  public SafeFuture<Void> onWeakSubjectivityUpdate(WeakSubjectivityUpdate weakSubjectivityUpdate) {
    return SafeFuture.fromRunnable(
        () -> database.updateWeakSubjectivityState(weakSubjectivityUpdate));
  }

  @Override
  public SafeFuture<Void> onFinalizedDepositSnapshot(
      final DepositTreeSnapshot depositTreeSnapshot) {
    return SafeFuture.fromRunnable(() -> database.setFinalizedDepositSnapshot(depositTreeSnapshot));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot() {
    return SafeFuture.of(database::getEarliestAvailableBlockSlot);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getEarliestAvailableBlock() {
    return SafeFuture.of(database::getEarliestAvailableBlock);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(final UInt64 slot) {
    return SafeFuture.of(() -> database.getFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return SafeFuture.of(() -> database.getLatestFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(() -> database.getSignedBlock(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> getHotBlockAndStateByBlockRoot(
      final Bytes32 blockRoot) {
    return SafeFuture.of(
        () ->
            database
                .getHotState(blockRoot)
                .flatMap(
                    s -> database.getHotBlock(blockRoot).map(b -> new SignedBlockAndState(b, s))));
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> getHotStateAndBlockSummaryByBlockRoot(
      final Bytes32 blockRoot) {
    return SafeFuture.of(
        () ->
            database
                .getHotState(blockRoot)
                .map(
                    state -> {
                      final BeaconBlockSummary block =
                          database
                              .getHotBlock(blockRoot)
                              .map(b -> (BeaconBlockSummary) b)
                              .orElseGet(() -> BeaconBlockHeader.fromState(state));
                      return StateAndBlockSummary.create(block, state);
                    }));
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(
      final Set<Bytes32> blockRoots) {
    return SafeFuture.of(() -> database.getHotBlocks(blockRoots));
  }

  @Override
  public SafeFuture<List<BlobSidecar>> getBlobSidecarsBySlotAndBlockRoot(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.of(
        () -> {
          try (final Stream<BlobSidecar> blobSidecarStream =
              database.streamBlobSidecars(slotAndBlockRoot)) {
            return blobSidecarStream.toList();
          }
        });
  }

  @Override
  public SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(
      final Bytes32 stateRoot) {
    return SafeFuture.of(() -> database.getSlotAndBlockRootFromStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UInt64 slot) {
    if (dataStorageMode.storesFinalizedStates()) {
      return SafeFuture.of(() -> getLatestFinalizedStateAtSlotSync(slot));
    }
    LOG.debug("Not storing archive states - not fetching finalized state at slot {}", slot);
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestAvailableFinalizedState(UInt64 slot) {
    if (dataStorageMode.storesFinalizedStates()) {
      return SafeFuture.of(() -> getLatestAvailableFinalizedStateSync(slot));
    }
    LOG.debug(
        "Not storing archive states - not fetching latest available finalized state at slot {}",
        slot);
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(() -> database.getSlotForFinalizedBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    if (dataStorageMode.storesFinalizedStates()) {
      return getFinalizedSlotByBlockRoot(blockRoot)
          .thenApply(slot -> slot.flatMap(this::getLatestFinalizedStateAtSlotSync));
    }
    LOG.debug("Not storing archive states - not fetching finalized state at root {}", blockRoot);
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(final Bytes32 stateRoot) {
    return SafeFuture.of(() -> database.getSlotForFinalizedStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(final UInt64 slot) {
    return SafeFuture.of(() -> database.getNonCanonicalBlocksAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<Checkpoint>> getAnchor() {
    return SafeFuture.of(database::getAnchor);
  }

  private Optional<BeaconState> getLatestFinalizedStateAtSlotSync(final UInt64 slot) {
    return finalizedStateCache.getFinalizedState(slot);
  }

  private Optional<BeaconState> getLatestAvailableFinalizedStateSync(final UInt64 slot) {
    return database.getLatestAvailableFinalizedState(slot);
  }

  @Override
  public void onVotesUpdated(final Map<UInt64, VoteTracker> votes) {
    database.storeVotes(votes);
  }

  @Override
  public SafeFuture<Optional<DepositTreeSnapshot>> getFinalizedDepositSnapshot() {
    return SafeFuture.of(database::getFinalizedDepositSnapshot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlobSidecarSlot() {
    return SafeFuture.of(database::getEarliestBlobSidecarSlot);
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> getBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    return SafeFuture.of(() -> database.getBlobSidecar(key));
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> getNonCanonicalBlobSidecar(
      final SlotAndBlockRootAndBlobIndex key) {
    return SafeFuture.of(() -> database.getNonCanonicalBlobSidecar(key));
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          try (final Stream<SlotAndBlockRootAndBlobIndex> blobSidecars =
              database.streamBlobSidecarKeys(slot)) {
            return blobSidecars.toList();
          }
        });
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getAllBlobSidecarKeys(final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          try (final Stream<SlotAndBlockRootAndBlobIndex> canonicalBlobSidecars =
                  database.streamBlobSidecarKeys(slot);
              final Stream<SlotAndBlockRootAndBlobIndex> nonCanonicalBlobSidecars =
                  database.streamNonCanonicalBlobSidecarKeys(slot)) {
            return Stream.concat(canonicalBlobSidecars, nonCanonicalBlobSidecars).toList();
          }
        });
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot, final long limit) {
    return SafeFuture.of(
        () -> {
          try (final Stream<SlotAndBlockRootAndBlobIndex> blobSidecars =
              database.streamBlobSidecarKeys(startSlot, endSlot)) {
            return blobSidecars.limit(limit).toList();
          }
        });
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.of(() -> database.getBlobSidecarKeys(slotAndBlockRoot));
  }
}
