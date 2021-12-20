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

package tech.pegasys.teku.storage.store;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.dataproviders.generators.StateAtSlotTask.AsyncStateProvider.fromBlockAndState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.dataproviders.generators.StateAtSlotTask;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;

class StoreTransaction implements UpdatableStore.StoreTransaction {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Store store;
  private final ReadWriteLock lock;
  private final StorageUpdateChannel storageUpdateChannel;

  Optional<UInt64> time = Optional.empty();
  Optional<UInt64> genesisTime = Optional.empty();
  Optional<Checkpoint> justifiedCheckpoint = Optional.empty();
  Optional<Checkpoint> finalizedCheckpoint = Optional.empty();
  Optional<Checkpoint> bestJustifiedCheckpoint = Optional.empty();
  Optional<Bytes32> proposerBoostRoot = Optional.empty();
  boolean proposerBoostRootSet = false;
  Optional<UInt64> latestValidFinalizedSlot = Optional.empty();
  Map<Bytes32, SlotAndBlockRoot> stateRoots = new HashMap<>();
  Map<Bytes32, SignedBlockAndState> blockAndStates = new HashMap<>();
  private final UpdatableStore.StoreUpdateHandler updateHandler;

  StoreTransaction(
      final Spec spec,
      final Store store,
      final ReadWriteLock lock,
      final StorageUpdateChannel storageUpdateChannel,
      final UpdatableStore.StoreUpdateHandler updateHandler) {
    this.spec = spec;
    this.store = store;
    this.lock = lock;
    this.storageUpdateChannel = storageUpdateChannel;
    this.updateHandler = updateHandler;
  }

  @Override
  public void putBlockAndState(final SignedBeaconBlock block, final BeaconState state) {
    putBlockAndState(new SignedBlockAndState(block, state));
  }

  @Override
  public void putBlockAndState(final SignedBlockAndState blockAndState) {
    blockAndStates.put(blockAndState.getRoot(), blockAndState);
    putStateRoot(
        blockAndState.getState().hashTreeRoot(),
        new SlotAndBlockRoot(blockAndState.getSlot(), blockAndState.getRoot()));
  }

  @Override
  public void putStateRoot(final Bytes32 stateRoot, final SlotAndBlockRoot slotAndBlockRoot) {
    stateRoots.put(stateRoot, slotAndBlockRoot);
  }

  @Override
  public void setTime(UInt64 time) {
    final UInt64 storeTime = store.getTime();
    checkArgument(
        time.isGreaterThanOrEqualTo(storeTime),
        "Cannot revert time from %s to %s",
        storeTime,
        time);
    this.time = Optional.of(time);
  }

  @Override
  public void setGenesisTime(UInt64 genesisTime) {
    this.genesisTime = Optional.of(genesisTime);
  }

  @Override
  public void setJustifiedCheckpoint(Checkpoint justified_checkpoint) {
    this.justifiedCheckpoint = Optional.of(justified_checkpoint);
  }

  @Override
  public void setFinalizedCheckpoint(Checkpoint finalized_checkpoint) {
    this.finalizedCheckpoint = Optional.of(finalized_checkpoint);
  }

  @Override
  public void setBestJustifiedCheckpoint(Checkpoint best_justified_checkpoint) {
    this.bestJustifiedCheckpoint = Optional.of(best_justified_checkpoint);
  }

  @Override
  public void setProposerBoostRoot(final Bytes32 boostedBlockRoot) {
    proposerBoostRoot = Optional.of(boostedBlockRoot);
    proposerBoostRootSet = true;
  }

  @Override
  public void removeProposerBoostRoot() {
    proposerBoostRoot = Optional.empty();
    proposerBoostRootSet = true;
  }

  @Override
  public void setLatestValidFinalizedSlot(UInt64 slot) {
    this.latestValidFinalizedSlot = Optional.of(slot);
  }

  @CheckReturnValue
  @Override
  public SafeFuture<Void> commit() {
    return retrieveLatestFinalized()
        .thenCompose(
            latestFinalized -> {
              final StoreTransactionUpdates updates;
              // Lock so that we have a consistent view while calculating our updates
              final Lock writeLock = lock.writeLock();
              writeLock.lock();
              try {
                updates = StoreTransactionUpdatesFactory.create(store, this, latestFinalized);
              } finally {
                writeLock.unlock();
              }

              return storageUpdateChannel
                  .onStorageUpdate(updates.createStorageUpdate())
                  .thenAccept(
                      __ -> {
                        // Propagate changes to Store
                        writeLock.lock();
                        try {
                          // Add new data
                          updates.applyToStore(store);
                        } finally {
                          writeLock.unlock();
                        }

                        // Signal back changes to the handler
                        finalizedCheckpoint.ifPresent(updateHandler::onNewFinalizedCheckpoint);
                      });
            });
  }

  @Override
  public void commit(final Runnable onSuccess, final String errorMessage) {
    commit(onSuccess, err -> LOG.error(errorMessage, err));
  }

  @Override
  public UInt64 getTime() {
    return time.orElseGet(store::getTime);
  }

  @Override
  public UInt64 getGenesisTime() {
    return genesisTime.orElseGet(store::getGenesisTime);
  }

  @Override
  public Optional<Checkpoint> getInitialCheckpoint() {
    return store.getInitialCheckpoint();
  }

  @Override
  public UInt64 getLatestValidFinalizedSlot() {
    return latestValidFinalizedSlot.orElseGet(store::getLatestValidFinalizedSlot);
  }

  @Override
  public Checkpoint getJustifiedCheckpoint() {
    return justifiedCheckpoint.orElseGet(store::getJustifiedCheckpoint);
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint.orElseGet(store::getFinalizedCheckpoint);
  }

  @Override
  public AnchorPoint getLatestFinalized() {
    if (finalizedCheckpoint.isPresent()) {
      // Ideally we wouldn't join here - but seems not worth making this API async since we're
      // unlikely to call this on tx objects
      final SignedBlockAndState finalizedBlockAndState =
          retrieveBlockAndState(finalizedCheckpoint.get().getRoot()).join().orElseThrow();
      return AnchorPoint.create(spec, finalizedCheckpoint.get(), finalizedBlockAndState);
    }
    return store.getLatestFinalized();
  }

  private SafeFuture<AnchorPoint> retrieveLatestFinalized() {
    if (finalizedCheckpoint.isPresent()) {
      final Checkpoint finalizedCheckpoint = this.finalizedCheckpoint.get();
      return retrieveBlockAndState(this.finalizedCheckpoint.get().getRoot())
          .thenApply(
              blockAndState ->
                  AnchorPoint.create(
                      spec,
                      finalizedCheckpoint,
                      blockAndState.orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "Missing latest finalized block and state"))));
    }
    return SafeFuture.completedFuture(store.getLatestFinalized());
  }

  @Override
  public UInt64 getLatestFinalizedBlockSlot() {
    return getLatestFinalized().getBlockSlot();
  }

  @Override
  public Checkpoint getBestJustifiedCheckpoint() {
    return bestJustifiedCheckpoint.orElseGet(store::getBestJustifiedCheckpoint);
  }

  @Override
  public Optional<Bytes32> getProposerBoostRoot() {
    return proposerBoostRootSet ? proposerBoostRoot : store.getProposerBoostRoot();
  }

  @Override
  public ReadOnlyForkChoiceStrategy getForkChoiceStrategy() {
    return store.getForkChoiceStrategy();
  }

  @Override
  public boolean containsBlock(final Bytes32 blockRoot) {
    return blockAndStates.containsKey(blockRoot) || store.containsBlock(blockRoot);
  }

  @Override
  public Collection<Bytes32> getOrderedBlockRoots() {
    if (this.blockAndStates.isEmpty()) {
      return store.getOrderedBlockRoots();
    }

    lock.readLock().lock();
    try {
      final NavigableMap<UInt64, Bytes32> blockRootsBySlot = new TreeMap<>();
      store.forkChoiceStrategy.processAllInOrder(
          (root, slot, parent) -> blockRootsBySlot.put(slot, root));
      this.blockAndStates
          .values()
          .forEach(
              blockAndState ->
                  blockRootsBySlot.put(blockAndState.getSlot(), blockAndState.getRoot()));
      return blockRootsBySlot.values();
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public Optional<BeaconState> getBlockStateIfAvailable(final Bytes32 blockRoot) {
    return Optional.ofNullable(blockAndStates.get(blockRoot))
        .map(SignedBlockAndState::getState)
        .or(() -> store.getBlockStateIfAvailable(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(Bytes32 blockRoot) {
    if (blockAndStates.containsKey(blockRoot)) {
      return SafeFuture.completedFuture(
          Optional.of(blockAndStates.get(blockRoot)).map(SignedBlockAndState::getBlock));
    }
    return store.retrieveSignedBlock(blockRoot);
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(Bytes32 blockRoot) {
    if (blockAndStates.containsKey(blockRoot)) {
      final SignedBlockAndState result = blockAndStates.get(blockRoot);
      return SafeFuture.completedFuture(Optional.of(result));
    }
    return store.retrieveBlockAndState(blockRoot);
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> retrieveStateAndBlockSummary(
      final Bytes32 blockRoot) {
    if (blockAndStates.containsKey(blockRoot)) {
      final StateAndBlockSummary result = blockAndStates.get(blockRoot);
      return SafeFuture.completedFuture(Optional.of(result));
    }
    return store.retrieveStateAndBlockSummary(blockRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveBlockState(Bytes32 blockRoot) {
    if (blockAndStates.containsKey(blockRoot)) {
      return SafeFuture.completedFuture(
          Optional.of(blockAndStates.get(blockRoot)).map(SignedBlockAndState::getState));
    }
    return store.retrieveBlockState(blockRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(Checkpoint checkpoint) {
    SignedBlockAndState inMemoryCheckpointBlockState = blockAndStates.get(checkpoint.getRoot());
    if (inMemoryCheckpointBlockState != null) {
      // Not executing the task via the task queue to avoid caching the result before the tx is
      // committed
      return new StateAtSlotTask(
              spec,
              checkpoint.toSlotAndBlockRoot(spec),
              fromBlockAndState(inMemoryCheckpointBlockState))
          .performTask();
    }
    return store.retrieveCheckpointState(checkpoint);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveStateAtSlot(SlotAndBlockRoot slotAndBlockRoot) {
    SignedBlockAndState inMemoryCheckpointBlockState =
        blockAndStates.get(slotAndBlockRoot.getBlockRoot());
    if (inMemoryCheckpointBlockState != null) {
      // Not executing the task via the task queue to avoid caching the result before the tx is
      // committed
      return new StateAtSlotTask(
              spec, slotAndBlockRoot, fromBlockAndState(inMemoryCheckpointBlockState))
          .performTask();
    }
    return store.retrieveStateAtSlot(slotAndBlockRoot);
  }

  @Override
  public SafeFuture<CheckpointState> retrieveFinalizedCheckpointAndState() {
    if (this.finalizedCheckpoint.isEmpty()) {
      return store.retrieveFinalizedCheckpointAndState();
    }

    final Checkpoint finalizedCheckpoint = getFinalizedCheckpoint();
    final SafeFuture<Optional<BeaconState>> stateFuture =
        retrieveCheckpointState(finalizedCheckpoint);
    final SafeFuture<Optional<SignedBeaconBlock>> blockFuture =
        retrieveSignedBlock(finalizedCheckpoint.getRoot());

    return SafeFuture.allOf(stateFuture, blockFuture)
        .thenCompose(
            (__) -> {
              final Optional<BeaconState> state = stateFuture.join();
              final Optional<SignedBeaconBlock> block = blockFuture.join();
              if (state.isEmpty() || block.isEmpty()) {
                return store.retrieveFinalizedCheckpointAndState();
              } else {
                return SafeFuture.completedFuture(
                    CheckpointState.create(spec, finalizedCheckpoint, block.get(), state.get()));
              }
            });
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
      final Checkpoint checkpoint, final BeaconState latestStateAtEpoch) {
    return store.retrieveCheckpointState(checkpoint, latestStateAtEpoch);
  }

  @Override
  public Optional<SignedBeaconBlock> getBlockIfAvailable(final Bytes32 blockRoot) {
    return Optional.ofNullable(blockAndStates.get(blockRoot))
        .map(SignedBlockAndState::getBlock)
        .or(() -> store.getBlockIfAvailable(blockRoot));
  }
}
