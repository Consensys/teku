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
import static tech.pegasys.teku.core.stategenerator.CheckpointStateTask.AsyncStateProvider.fromBlockAndState;

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.stategenerator.CheckpointStateTask;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;

class StoreTransaction implements UpdatableStore.StoreTransaction {
  private static final Logger LOG = LogManager.getLogger();

  private final Store store;
  private final ReadWriteLock lock;
  private final StorageUpdateChannel storageUpdateChannel;

  Optional<UInt64> time = Optional.empty();
  Optional<UInt64> genesis_time = Optional.empty();
  Optional<Checkpoint> justified_checkpoint = Optional.empty();
  Optional<Checkpoint> finalized_checkpoint = Optional.empty();
  Optional<Checkpoint> best_justified_checkpoint = Optional.empty();
  Map<Bytes32, SlotAndBlockRoot> stateRoots = new HashMap<>();
  Map<Bytes32, SignedBlockAndState> blockAndStates = new HashMap<>();
  Map<UInt64, VoteTracker> votes = new ConcurrentHashMap<>();
  private final UpdatableStore.StoreUpdateHandler updateHandler;

  StoreTransaction(
      final Store store,
      final ReadWriteLock lock,
      final StorageUpdateChannel storageUpdateChannel,
      final UpdatableStore.StoreUpdateHandler updateHandler) {
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
        blockAndState.getState().hash_tree_root(),
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
  public void setGenesis_time(UInt64 genesis_time) {
    this.genesis_time = Optional.of(genesis_time);
  }

  @Override
  public void setJustifiedCheckpoint(Checkpoint justified_checkpoint) {
    this.justified_checkpoint = Optional.of(justified_checkpoint);
  }

  @Override
  public void setFinalizedCheckpoint(Checkpoint finalized_checkpoint) {
    this.finalized_checkpoint = Optional.of(finalized_checkpoint);
  }

  @Override
  public void setBestJustifiedCheckpoint(Checkpoint best_justified_checkpoint) {
    this.best_justified_checkpoint = Optional.of(best_justified_checkpoint);
  }

  @Override
  public VoteTracker getVote(UInt64 validatorIndex) {
    VoteTracker vote = votes.get(validatorIndex);
    if (vote == null) {
      vote = store.getVote(validatorIndex);
      if (vote == null) {
        vote = VoteTracker.Default();
      } else {
        vote = vote.copy();
      }
      votes.put(validatorIndex, vote);
    }
    return vote;
  }

  @CheckReturnValue
  @Override
  public SafeFuture<Void> commit() {
    return retrieveBlockAndState(getFinalizedCheckpoint().getRoot())
        .thenCompose(
            maybeLatestFinalized -> {
              final SignedBlockAndState latestFinalized =
                  maybeLatestFinalized.orElseThrow(
                      () -> new IllegalStateException("Missing latest finalized block and state"));
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
                        finalized_checkpoint.ifPresent(updateHandler::onNewFinalizedCheckpoint);
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
    return genesis_time.orElseGet(store::getGenesisTime);
  }

  @Override
  public Optional<Checkpoint> getInitialCheckpoint() {
    return store.getInitialCheckpoint();
  }

  @Override
  public Checkpoint getJustifiedCheckpoint() {
    return justified_checkpoint.orElseGet(store::getJustifiedCheckpoint);
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    return finalized_checkpoint.orElseGet(store::getFinalizedCheckpoint);
  }

  @Override
  public SignedBlockAndState getLatestFinalizedBlockAndState() {
    if (finalized_checkpoint.isPresent()) {
      // Ideally we wouldn't join here - but seems not worth making this API async since we're
      // unlikely to call this on tx objects
      return retrieveBlockAndState(finalized_checkpoint.get().getRoot()).join().orElseThrow();
    }
    return store.getLatestFinalizedBlockAndState();
  }

  @Override
  public UInt64 getLatestFinalizedBlockSlot() {
    return getLatestFinalizedBlockAndState().getSlot();
  }

  @Override
  public Checkpoint getBestJustifiedCheckpoint() {
    return best_justified_checkpoint.orElseGet(store::getBestJustifiedCheckpoint);
  }

  @Override
  public boolean containsBlock(final Bytes32 blockRoot) {
    return blockAndStates.containsKey(blockRoot) || store.containsBlock(blockRoot);
  }

  @Override
  public Set<Bytes32> getBlockRoots() {
    return Sets.union(blockAndStates.keySet(), store.getBlockRoots());
  }

  @Override
  public List<Bytes32> getOrderedBlockRoots() {
    if (this.blockAndStates.isEmpty()) {
      return store.getOrderedBlockRoots();
    }

    lock.readLock().lock();
    try {
      final HashTree.Builder treeBuilder = store.blockTree.getHashTree().updater();
      this.blockAndStates.values().stream()
          .map(SignedBlockAndState::getBlock)
          .forEach(treeBuilder::block);
      return treeBuilder.build().breadthFirstStream().collect(Collectors.toList());
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
      return new CheckpointStateTask(checkpoint, fromBlockAndState(inMemoryCheckpointBlockState))
          .performTask();
    }
    return store.retrieveCheckpointState(checkpoint);
  }

  @Override
  public SafeFuture<CheckpointState> retrieveFinalizedCheckpointAndState() {
    if (this.finalized_checkpoint.isEmpty()) {
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
                    new CheckpointState(finalizedCheckpoint, block.get(), state.get()));
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

  @Override
  public Set<UInt64> getVotedValidatorIndices() {
    return Sets.union(votes.keySet(), store.getVotedValidatorIndices());
  }
}
