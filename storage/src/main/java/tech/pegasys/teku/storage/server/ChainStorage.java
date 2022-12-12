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

package tech.pegasys.teku.storage.server;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
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

  private final Database database;
  private final FinalizedStateCache finalizedStateCache;

  @SuppressWarnings("unused")
  private final Optional<ExecutionLayerChannel> executionLayerChannel;

  private Optional<OnDiskStoreData> cachedStoreData = Optional.empty();

  private final Spec spec;

  private ChainStorage(
      final Database database,
      final FinalizedStateCache finalizedStateCache,
      final Optional<ExecutionLayerChannel> executionLayerChannel,
      final Spec spec) {
    this.database = database;
    this.finalizedStateCache = finalizedStateCache;
    this.spec = spec;
    this.executionLayerChannel = executionLayerChannel;
  }

  public static ChainStorage create(
      final Database database,
      final Optional<ExecutionLayerChannel> executionLayerChannel,
      final Spec spec) {
    final int finalizedStateCacheSize = spec.getSlotsPerEpoch(SpecConfig.GENESIS_EPOCH) * 3;
    return new ChainStorage(
        database,
        new FinalizedStateCache(spec, database, finalizedStateCacheSize, true),
        executionLayerChannel,
        spec);
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
  public SafeFuture<Void> onFinalizedBlocks(final Collection<SignedBeaconBlock> finalizedBlocks) {
    return SafeFuture.fromRunnable(() -> database.storeFinalizedBlocks(finalizedBlocks));
  }

  @Override
  public SafeFuture<Void> onFinalizedState(BeaconState finalizedState, Bytes32 blockRoot) {
    return SafeFuture.fromRunnable(() -> database.storeFinalizedState(finalizedState, blockRoot));
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
    return SafeFuture.of(() -> database.getFinalizedBlockAtSlot(slot))
        .thenCompose(this::unblindBlock);
  }

  @Override
  public SafeFuture<Void> onBlobsSidecar(BlobsSidecar blobsSidecar) {
    return SafeFuture.of(
        () -> {
          database.storeUnconfirmedBlobsSidecar(blobsSidecar);
          return null;
        });
  }

  @Override
  public SafeFuture<Void> onBlobsSidecarRemoval(SlotAndBlockRoot blobsSidecar) {
    return SafeFuture.of(
        () -> {
          database.removeBlobsSidecar(blobsSidecar);
          return null;
        });
  }

  @Override
  public SafeFuture<Void> onBlobsSidecarPruning(UInt64 endSlot, int pruneLimit) {
    return SafeFuture.of(
        () -> {
          database.pruneOldestBlobsSidecar(endSlot, pruneLimit);
          return null;
        });
  }

  @Override
  public SafeFuture<Void> onUnconfirmedBlobsSidecarPruning(UInt64 endSlot, int pruneLimit) {
    return SafeFuture.of(
        () -> {
          database.pruneOldestUnconfirmedBlobsSidecar(endSlot, pruneLimit);
          return null;
        });
  }

  private SafeFuture<Optional<SignedBeaconBlock>> unblindBlock(
      final Optional<SignedBeaconBlock> maybeBlock) {
    if (maybeBlock.isEmpty()) {
      return SafeFuture.completedFuture(maybeBlock);
    }
    return unblindBlock(maybeBlock.get()).thenApply(Optional::of);
  }

  private SafeFuture<SignedBeaconBlock> unblindBlock(final SignedBeaconBlock block) {
    if (!block.isBlinded()) {
      return SafeFuture.completedFuture(block);
    }

    final Optional<ExecutionPayloadSummary> maybeSummary =
        block.getMessage().getBody().getOptionalExecutionPayloadSummary();
    if (maybeSummary.isPresent() && maybeSummary.get().isDefaultPayload()) {
      // can return without having to fetch anything
      final SignedBeaconBlock unblinded =
          block.unblind(
              spec.atSlot(block.getSlot()).getSchemaDefinitions(),
              SchemaDefinitionsBellatrix.required(
                      spec.atSlot(block.getSlot()).getSchemaDefinitions())
                  .getExecutionPayloadSchema()
                  .getDefault());
      return SafeFuture.completedFuture(unblinded);
    }

    // attempt to fetch payload from storage
    final Optional<ExecutionPayload> maybePayload =
        database.getExecutionPayload(block.getRoot(), block.getSlot());
    if (maybePayload.isPresent()) {
      return SafeFuture.completedFuture(
          block.unblind(spec.atSlot(block.getSlot()).getSchemaDefinitions(), maybePayload.get()));
    }

    throw new StorageException(
        String.format(
            "Needed a payload, but not able to retrieve from storage for block %s (%s)",
            block.getRoot(), block.getSlot()));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return SafeFuture.of(() -> database.getLatestFinalizedBlockAtSlot(slot))
        .thenCompose(this::unblindBlock);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(() -> database.getSignedBlock(blockRoot)).thenCompose(this::unblindBlock);
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
  public SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(
      final Bytes32 stateRoot) {
    return SafeFuture.of(() -> database.getSlotAndBlockRootFromStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UInt64 slot) {
    return SafeFuture.of(() -> getLatestFinalizedStateAtSlotSync(slot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestAvailableFinalizedState(UInt64 slot) {
    return SafeFuture.of(() -> getLatestAvailableFinalizedStateSync(slot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(
        () ->
            database
                .getSlotForFinalizedBlockRoot(blockRoot)
                .flatMap(this::getLatestFinalizedStateAtSlotSync));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(final Bytes32 stateRoot) {
    return SafeFuture.of(() -> database.getSlotForFinalizedStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(final UInt64 slot) {
    final List<SignedBeaconBlock> blocks = database.getNonCanonicalBlocksAtSlot(slot);
    return SafeFuture.collectAll(blocks.stream().map(this::unblindBlock));
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
  public SafeFuture<Optional<BlobsSidecar>> getBlobsSidecar(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.of(() -> database.getBlobsSidecar(slotAndBlockRoot));
  }
}
