/*
 * Copyright 2019 ConsenSys AG.
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

import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.StateGenerator;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointAndBlock;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.collections.ConcurrentLimitedMap;
import tech.pegasys.teku.util.collections.LimitStrategy;

class Store implements UpdatableStore {
  private static final Logger LOG = LogManager.getLogger();
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  UnsignedLong time;
  UnsignedLong genesis_time;
  Checkpoint justified_checkpoint;
  Checkpoint finalized_checkpoint;
  Checkpoint best_justified_checkpoint;
  Map<Bytes32, SignedBeaconBlock> blocks;
  Map<Bytes32, BeaconState> block_states;
  Map<Checkpoint, BeaconState> checkpoint_states;
  Map<UnsignedLong, VoteTracker> votes;
  SignedBlockAndState finalizedBlockAndState;

  final NavigableMap<UnsignedLong, Set<Bytes32>> rootsBySlotLookup = new TreeMap<>();

  Store(
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Bytes32, BeaconState> block_states,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final Map<UnsignedLong, VoteTracker> votes,
      final int stateCacheSize) {
    this.time = time;
    this.genesis_time = genesis_time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.best_justified_checkpoint = best_justified_checkpoint;
    this.blocks = new ConcurrentHashMap<>(blocks);
    this.block_states =
        ConcurrentLimitedMap.create(stateCacheSize, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
    this.block_states.putAll(block_states);
    this.checkpoint_states = new ConcurrentHashMap<>(checkpoint_states);
    this.votes = new ConcurrentHashMap<>(votes);

    final SignedBeaconBlock finalizedBlock = blocks.get(finalized_checkpoint.getRoot());
    final BeaconState finalizedBlockState = block_states.get(finalized_checkpoint.getRoot());
    this.finalizedBlockAndState = new SignedBlockAndState(finalizedBlock, finalizedBlockState);

    // Setup slot to root mappings
    indexBlockRootsBySlot(rootsBySlotLookup, this.blocks.values());
  }

  static void indexBlockRootsBySlot(
      final Map<UnsignedLong, Set<Bytes32>> index, final Collection<SignedBeaconBlock> blocks) {
    blocks.forEach(b -> indexBlockRootBySlot(index, b));
  }

  static void indexBlockRootBySlot(
      final Map<UnsignedLong, Set<Bytes32>> index, SignedBeaconBlock block) {
    final Bytes32 root = block.getRoot();
    final UnsignedLong slot = block.getSlot();
    index.computeIfAbsent(slot, key -> new HashSet<>()).add(root);
  }

  static void removeBlockRootFromSlotIndex(
      final Map<UnsignedLong, Set<Bytes32>> index, final UnsignedLong slot, final Bytes32 root) {
    index.computeIfPresent(
        slot,
        (s, roots) -> {
          roots.remove(root);
          return roots.isEmpty() ? null : roots;
        });
  }

  static void removeBlockRootFromSlotIndex(
      final Map<UnsignedLong, Set<Bytes32>> index, SignedBeaconBlock block) {
    removeBlockRootFromSlotIndex(index, block.getSlot(), block.getRoot());
  }

  @Override
  public StoreTransaction startTransaction(final StorageUpdateChannel storageUpdateChannel) {
    return startTransaction(storageUpdateChannel, StoreUpdateHandler.NOOP);
  }

  @Override
  public StoreTransaction startTransaction(
      final StorageUpdateChannel storageUpdateChannel, final StoreUpdateHandler updateHandler) {
    return new Transaction(storageUpdateChannel, updateHandler);
  }

  @Override
  public UnsignedLong getTime() {
    readLock.lock();
    try {
      return time;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public UnsignedLong getGenesisTime() {
    readLock.lock();
    try {
      return genesis_time;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Checkpoint getJustifiedCheckpoint() {
    readLock.lock();
    try {
      return justified_checkpoint;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    readLock.lock();
    try {
      return finalized_checkpoint;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public CheckpointAndBlock getFinalizedCheckpointAndBlock() {
    readLock.lock();
    try {
      final Checkpoint checkpoint = finalized_checkpoint;
      final SignedBeaconBlock block = getSignedBlock(checkpoint.getRoot());
      return new CheckpointAndBlock(checkpoint, block);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public SignedBlockAndState getLatestFinalizedBlockAndState() {
    readLock.lock();
    try {
      return finalizedBlockAndState;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public UnsignedLong getLatestFinalizedBlockSlot() {
    readLock.lock();
    try {
      return blocks.get(finalized_checkpoint.getRoot()).getSlot();
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Checkpoint getBestJustifiedCheckpoint() {
    readLock.lock();
    try {
      return best_justified_checkpoint;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public BeaconBlock getBlock(Bytes32 blockRoot) {
    final SignedBeaconBlock signedBlock = getSignedBlock(blockRoot);
    return signedBlock != null ? signedBlock.getMessage() : null;
  }

  @Override
  public SignedBeaconBlock getSignedBlock(Bytes32 blockRoot) {
    readLock.lock();
    try {
      return blocks.get(blockRoot);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Optional<SignedBlockAndState> getBlockAndState(final Bytes32 blockRoot) {
    final SignedBeaconBlock block = getSignedBlock(blockRoot);
    final Optional<BeaconState> state = getOrGenerateBlockState(blockRoot);
    if (block == null || state.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new SignedBlockAndState(block, state.get()));
  }

  @Override
  public boolean containsBlock(Bytes32 blockRoot) {
    readLock.lock();
    try {
      return blocks.containsKey(blockRoot);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Set<Bytes32> getBlockRoots() {
    readLock.lock();
    try {
      return Collections.unmodifiableSet(blocks.keySet());
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public BeaconState getBlockState(Bytes32 blockRoot) {
    return getOrGenerateBlockState(blockRoot).orElse(null);
  }

  @Override
  public BeaconState getCheckpointState(Checkpoint checkpoint) {
    readLock.lock();
    try {
      return checkpoint_states.get(checkpoint);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean containsCheckpointState(Checkpoint checkpoint) {
    readLock.lock();
    try {
      return checkpoint_states.containsKey(checkpoint);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Set<UnsignedLong> getVotedValidatorIndices() {
    readLock.lock();
    try {
      return votes.keySet();
    } finally {
      readLock.unlock();
    }
  }

  private Optional<BeaconState> getOrGenerateBlockState(final Bytes32 blockRoot) {
    Optional<BeaconState> state = getState(blockRoot);
    if (state.isPresent()) {
      return state;
    }
    final SignedBeaconBlock blockForState = getSignedBlock(blockRoot);
    if (blockForState == null) {
      // If we don't have the corresponding block, we can't possibly regenerate the state
      return Optional.empty();
    }

    // Accumulate blocks until we find our base state to build from
    SignedBlockAndState baseBlock = null;
    final Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    SignedBeaconBlock block = blockForState;
    while (block != null) {
      final Optional<BeaconState> blockState = getState(block.getRoot());
      if (blockState.isPresent()) {
        // We found a base state
        baseBlock = new SignedBlockAndState(block, blockState.get());
        break;
      }
      blocks.put(block.getRoot(), block);
      block = getSignedBlock(block.getParent_root());
    }

    if (baseBlock == null) {
      // If we haven't found a base state yet, we must have walked back to the latest finalized
      // block, check here for the base state
      final SignedBlockAndState finalizedBlock = getLatestFinalizedBlockAndState();
      if (!blocks.keySet().contains(finalizedBlock.getRoot())) {
        // We must have finalized a new block while processing and moved past our target root
        return Optional.empty();
      }
      baseBlock = finalizedBlock;
    }

    // Regenerate state
    final StateGenerator stateGenerator = new StateGenerator();
    final Map<Bytes32, BeaconState> regeneratedStates =
        stateGenerator.produceStatesForBlocks(
            baseBlock.getRoot(), baseBlock.getState(), blocks.values());

    // Save regenerated state
    final BeaconState regeneratedState = regeneratedStates.get(blockRoot);
    if (regeneratedState == null) {
      throw new IllegalStateException("Unable to generate state for block " + blockRoot);
    }
    putState(blockRoot, regeneratedState);

    return Optional.of(regeneratedState);
  }

  private Optional<BeaconState> getState(final Bytes32 blockRoot) {
    readLock.lock();
    try {
      return Optional.ofNullable(block_states.get(blockRoot));
    } finally {
      readLock.unlock();
    }
  }

  private void putState(final Bytes32 blockRoot, final BeaconState state) {
    final Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      if (containsBlock(blockRoot)) {
        block_states.put(blockRoot, state);
      }
    } finally {
      writeLock.unlock();
    }
  }

  class Transaction implements StoreTransaction {

    private final StorageUpdateChannel storageUpdateChannel;
    Optional<UnsignedLong> time = Optional.empty();
    Optional<UnsignedLong> genesis_time = Optional.empty();
    Optional<Checkpoint> justified_checkpoint = Optional.empty();
    Optional<Checkpoint> finalized_checkpoint = Optional.empty();
    Optional<Checkpoint> best_justified_checkpoint = Optional.empty();
    Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BeaconState> block_states = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    Map<UnsignedLong, VoteTracker> votes = new ConcurrentHashMap<>();
    private final StoreUpdateHandler updateHandler;

    Transaction(
        final StorageUpdateChannel storageUpdateChannel, final StoreUpdateHandler updateHandler) {
      this.storageUpdateChannel = storageUpdateChannel;
      this.updateHandler = updateHandler;
    }

    @Override
    public void putCheckpointState(Checkpoint checkpoint, BeaconState state) {
      checkpoint_states.put(checkpoint, state);
    }

    @Override
    public void putBlockAndState(SignedBeaconBlock block, BeaconState state) {
      blocks.put(block.getRoot(), block);
      block_states.put(block.getRoot(), state);
    }

    @Override
    public void setTime(UnsignedLong time) {
      this.time = Optional.of(time);
    }

    @Override
    public void setGenesis_time(UnsignedLong genesis_time) {
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
    public VoteTracker getVote(UnsignedLong validatorIndex) {
      VoteTracker vote = votes.get(validatorIndex);
      if (vote == null) {
        vote = Store.this.votes.get(validatorIndex);
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
      final StoreTransactionUpdates updates;
      // Lock so that we have a consistent view while calculating our updates
      final Lock writeLock = Store.this.lock.writeLock();
      writeLock.lock();
      try {
        updates = StoreTransactionUpdates.calculate(Store.this, this);
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
                  updates.applyToStore(Store.this);
                } finally {
                  writeLock.unlock();
                }

                // Signal back changes to the handler
                finalized_checkpoint.ifPresent(updateHandler::onNewFinalizedCheckpoint);
              });
    }

    @Override
    public void commit(final Runnable onSuccess, final String errorMessage) {
      commit(onSuccess, err -> LOG.error(errorMessage, err));
    }

    @Override
    public UnsignedLong getTime() {
      return time.orElseGet(Store.this::getTime);
    }

    @Override
    public UnsignedLong getGenesisTime() {
      return genesis_time.orElseGet(Store.this::getGenesisTime);
    }

    @Override
    public Checkpoint getJustifiedCheckpoint() {
      return justified_checkpoint.orElseGet(Store.this::getJustifiedCheckpoint);
    }

    @Override
    public Checkpoint getFinalizedCheckpoint() {
      return finalized_checkpoint.orElseGet(Store.this::getFinalizedCheckpoint);
    }

    @Override
    public CheckpointAndBlock getFinalizedCheckpointAndBlock() {
      return finalized_checkpoint
          .flatMap(
              (checkpoint) ->
                  Optional.ofNullable(getSignedBlock(checkpoint.getRoot()))
                      .map(block -> new CheckpointAndBlock(checkpoint, block)))
          .orElse(Store.this.getFinalizedCheckpointAndBlock());
    }

    @Override
    public SignedBlockAndState getLatestFinalizedBlockAndState() {
      return finalized_checkpoint
          .map(Checkpoint::getRoot)
          .flatMap(this::getBlockAndState)
          .orElse(Store.this.getLatestFinalizedBlockAndState());
    }

    @Override
    public UnsignedLong getLatestFinalizedBlockSlot() {
      return getBlock(getFinalizedCheckpoint().getRoot()).getSlot();
    }

    @Override
    public Checkpoint getBestJustifiedCheckpoint() {
      return best_justified_checkpoint.orElseGet(Store.this::getBestJustifiedCheckpoint);
    }

    @Override
    public BeaconBlock getBlock(final Bytes32 blockRoot) {
      final SignedBeaconBlock signedBlock = getSignedBlock(blockRoot);
      return signedBlock != null ? signedBlock.getMessage() : null;
    }

    @Override
    public SignedBeaconBlock getSignedBlock(final Bytes32 blockRoot) {
      return either(blockRoot, blocks::get, Store.this::getSignedBlock);
    }

    @Override
    public Optional<SignedBlockAndState> getBlockAndState(final Bytes32 blockRoot) {
      final SignedBeaconBlock block = getSignedBlock(blockRoot);
      final BeaconState state = getBlockState(blockRoot);
      if (block == null || state == null) {
        return Optional.empty();
      }
      return Optional.of(new SignedBlockAndState(block, state));
    }

    @Override
    public boolean containsBlock(final Bytes32 blockRoot) {
      return blocks.containsKey(blockRoot) || Store.this.containsBlock(blockRoot);
    }

    @Override
    public Set<Bytes32> getBlockRoots() {
      return Sets.union(blocks.keySet(), Store.this.getBlockRoots());
    }

    @Override
    public BeaconState getBlockState(final Bytes32 blockRoot) {
      return either(blockRoot, block_states::get, Store.this::getBlockState);
    }

    @Override
    public Set<UnsignedLong> getVotedValidatorIndices() {
      return Sets.union(votes.keySet(), Store.this.getVotedValidatorIndices());
    }

    private <I, O> O either(I input, Function<I, O> primary, Function<I, O> secondary) {
      final O primaryValue = primary.apply(input);
      return primaryValue != null ? primaryValue : secondary.apply(input);
    }

    @Override
    public BeaconState getCheckpointState(final Checkpoint checkpoint) {
      return either(checkpoint, checkpoint_states::get, Store.this::getCheckpointState);
    }

    @Override
    public boolean containsCheckpointState(final Checkpoint checkpoint) {
      return checkpoint_states.containsKey(checkpoint)
          || Store.this.containsCheckpointState(checkpoint);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Store)) {
      return false;
    }
    final Store store = (Store) o;
    return Objects.equals(time, store.time)
        && Objects.equals(genesis_time, store.genesis_time)
        && Objects.equals(justified_checkpoint, store.justified_checkpoint)
        && Objects.equals(finalized_checkpoint, store.finalized_checkpoint)
        && Objects.equals(best_justified_checkpoint, store.best_justified_checkpoint)
        && Objects.equals(blocks, store.blocks)
        && Objects.equals(checkpoint_states, store.checkpoint_states);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        time,
        genesis_time,
        justified_checkpoint,
        finalized_checkpoint,
        best_justified_checkpoint,
        blocks,
        checkpoint_states);
  }
}
