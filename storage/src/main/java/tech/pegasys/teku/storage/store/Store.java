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

import static tech.pegasys.teku.core.lookup.BlockProvider.fromDynamicMap;
import static tech.pegasys.teku.core.lookup.BlockProvider.fromMap;

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockProvider;
import tech.pegasys.teku.core.stategenerator.CachingTaskQueue;
import tech.pegasys.teku.core.stategenerator.CheckpointStateTask;
import tech.pegasys.teku.core.stategenerator.StateGenerationTask;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.metrics.SettableGauge;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.util.collections.LimitedMap;

class Store implements UpdatableStore {
  private static final Logger LOG = LogManager.getLogger();

  private final int hotStatePersistenceFrequencyInEpochs;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final MetricsSystem metricsSystem;
  private final CachingTaskQueue<Checkpoint, BeaconState> checkpointStateTaskQueue;
  private final StateAndBlockProvider stateAndBlockProvider;

  private Optional<SettableGauge> blockCountGauge = Optional.empty();

  private final BlockProvider blockProvider;

  BlockTree blockTree;
  UInt64 time;
  UInt64 genesis_time;
  Checkpoint justified_checkpoint;
  Checkpoint finalized_checkpoint;
  Checkpoint best_justified_checkpoint;
  final CachingTaskQueue<Bytes32, SignedBlockAndState> states;
  final Map<Bytes32, SignedBeaconBlock> blocks;
  final Map<UInt64, VoteTracker> votes;
  SignedBlockAndState finalizedBlockAndState;

  private Store(
      final MetricsSystem metricsSystem,
      int hotStatePersistenceFrequencyInEpochs,
      final BlockProvider blockProvider,
      final StateAndBlockProvider stateAndBlockProvider,
      final CachingTaskQueue<Bytes32, SignedBlockAndState> states,
      final UInt64 time,
      final UInt64 genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final BlockTree blockTree,
      final SignedBlockAndState finalizedBlockAndState,
      final Map<UInt64, VoteTracker> votes,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final CachingTaskQueue<Checkpoint, BeaconState> checkpointStateTaskQueue) {
    this.stateAndBlockProvider = stateAndBlockProvider;
    LOG.trace(
        "Create store with hot state persistence configured to {}",
        hotStatePersistenceFrequencyInEpochs);

    // Set up metrics
    this.metricsSystem = metricsSystem;
    this.states = states;
    this.checkpointStateTaskQueue = checkpointStateTaskQueue;

    // Store instance variables
    this.hotStatePersistenceFrequencyInEpochs = hotStatePersistenceFrequencyInEpochs;
    this.time = time;
    this.genesis_time = genesis_time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.best_justified_checkpoint = best_justified_checkpoint;
    this.blocks = blocks;
    this.votes = new HashMap<>(votes);
    this.blockTree = blockTree;

    // Track latest finalized block
    this.finalizedBlockAndState = finalizedBlockAndState;
    states.cache(finalizedBlockAndState.getRoot(), finalizedBlockAndState);

    // Set up block provider to draw from in-memory blocks
    this.blockProvider =
        BlockProvider.combined(
            fromDynamicMap(
                () -> {
                  SignedBlockAndState finalized = this.getLatestFinalizedBlockAndState();
                  return Map.of(finalized.getRoot(), finalized.getBlock());
                }),
            fromMap(this.blocks),
            blockProvider);
  }

  public static SafeFuture<UpdatableStore> create(
      final MetricsSystem metricsSystem,
      final BlockProvider blockProvider,
      final StateAndBlockProvider stateAndBlockProvider,
      final CachingTaskQueue<Bytes32, SignedBlockAndState> stateTaskQueue,
      final UInt64 time,
      final UInt64 genesisTime,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint bestJustifiedCheckpoint,
      final Map<Bytes32, Bytes32> childToParentRoot,
      final Map<Bytes32, UInt64> rootToSlotMap,
      final SignedBlockAndState finalizedBlockAndState,
      final Map<UInt64, VoteTracker> votes,
      final StoreConfig config) {

    // Create limited collections for non-final data
    final Map<Bytes32, SignedBeaconBlock> blocks = LimitedMap.create(config.getBlockCacheSize());
    final CachingTaskQueue<Checkpoint, BeaconState> checkpointStateTaskQueue =
        CachingTaskQueue.create(
            metricsSystem, "memory_checkpoint_states", config.getCheckpointStateCacheSize());

    // Build block tree structure
    HashTree.Builder treeBuilder = HashTree.builder().rootHash(finalizedBlockAndState.getRoot());
    childToParentRoot.forEach(treeBuilder::childAndParentRoots);
    final BlockTree blockTree = BlockTree.create(treeBuilder.build(), rootToSlotMap);
    if (blockTree.size() < childToParentRoot.size()) {
      // This should be an error, but keeping this as a warning now for backwards-compatibility
      // reasons.  Some existing databases may have unpruned fork blocks, and could become
      // unusable
      // if we throw here.  In the future, we should convert this to an error.
      LOG.warn("Ignoring {} non-canonical blocks", childToParentRoot.size() - blockTree.size());
    }

    return SafeFuture.completedFuture(
        new Store(
            metricsSystem,
            config.getHotStatePersistenceFrequencyInEpochs(),
            blockProvider,
            stateAndBlockProvider,
            stateTaskQueue,
            time,
            genesisTime,
            justifiedCheckpoint,
            finalizedCheckpoint,
            bestJustifiedCheckpoint,
            blockTree,
            finalizedBlockAndState,
            votes,
            blocks,
            checkpointStateTaskQueue));
  }

  /**
   * Start reporting gauge values to metrics.
   *
   * <p>Gauges can only be created once so we delay initializing these metrics until we know that
   * this instance is the canonical store.
   */
  @Override
  public void startMetrics() {
    lock.writeLock().lock();
    try {
      blockCountGauge =
          Optional.of(
              SettableGauge.create(
                  metricsSystem,
                  TekuMetricCategory.STORAGE,
                  "memory_block_count",
                  "Number of beacon blocks held in the in-memory store"));
      states.startMetrics();
      checkpointStateTaskQueue.startMetrics();
    } finally {
      lock.writeLock().unlock();
    }
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
  public UInt64 getTime() {
    readLock.lock();
    try {
      return time;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public UInt64 getGenesisTime() {
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
  public SignedBlockAndState getLatestFinalizedBlockAndState() {
    readLock.lock();
    try {
      return finalizedBlockAndState;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public UInt64 getLatestFinalizedBlockSlot() {
    readLock.lock();
    try {
      return finalizedBlockAndState.getSlot();
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
  public boolean containsBlock(Bytes32 blockRoot) {
    readLock.lock();
    try {
      return blockTree.contains(blockRoot);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Set<Bytes32> getBlockRoots() {
    readLock.lock();
    try {
      return blockTree.getAllRoots();
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public List<Bytes32> getOrderedBlockRoots() {
    readLock.lock();
    try {
      return blockTree.getOrderedBlockRoots();
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Optional<BeaconState> getBlockStateIfAvailable(final Bytes32 blockRoot) {
    return states.getIfAvailable(blockRoot).map(SignedBlockAndState::getState);
  }

  @Override
  public Optional<SignedBeaconBlock> getBlockIfAvailable(final Bytes32 blockRoot) {
    readLock.lock();
    try {
      return Optional.ofNullable(blocks.get(blockRoot));
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(final Bytes32 blockRoot) {
    if (!containsBlock(blockRoot)) {
      return EmptyStoreResults.EMPTY_SIGNED_BLOCK_FUTURE;
    }
    final Optional<SignedBeaconBlock> inMemoryBlock = getBlockIfAvailable(blockRoot);
    if (inMemoryBlock.isPresent()) {
      return SafeFuture.completedFuture(inMemoryBlock);
    }

    // Retrieve and cache block
    return blockProvider
        .getBlock(blockRoot)
        .thenApply(
            block -> {
              block.ifPresent(this::putBlock);
              return block;
            });
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(Bytes32 blockRoot) {
    return getAndCacheBlockAndState(blockRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveBlockState(Bytes32 blockRoot) {
    return getAndCacheBlockState(blockRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(Checkpoint checkpoint) {
    return checkpointStateTaskQueue.perform(
        new CheckpointStateTask(checkpoint, this::retrieveBlockState));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
      Checkpoint checkpoint, final BeaconState latestStateAtEpoch) {
    return checkpointStateTaskQueue.perform(
        new CheckpointStateTask(
            checkpoint, blockRoot -> SafeFuture.completedFuture(Optional.of(latestStateAtEpoch))));
  }

  @Override
  public Set<UInt64> getVotedValidatorIndices() {
    readLock.lock();
    try {
      return new HashSet<>(votes.keySet());
    } finally {
      readLock.unlock();
    }
  }

  private VoteTracker getVote(UInt64 validatorIndex) {
    readLock.lock();
    try {
      return votes.get(validatorIndex);
    } finally {
      readLock.unlock();
    }
  }

  private SafeFuture<Optional<BeaconState>> getAndCacheBlockState(final Bytes32 blockRoot) {
    Optional<BeaconState> inMemoryState = getBlockStateIfAvailable(blockRoot);
    if (inMemoryState.isPresent()) {
      return SafeFuture.completedFuture(inMemoryState);
    }
    return regenerateState(blockRoot).thenApply(res -> res.map(SignedBlockAndState::getState));
  }

  private SafeFuture<Optional<SignedBlockAndState>> getAndCacheBlockAndState(
      final Bytes32 blockRoot) {
    Optional<BeaconState> inMemoryState = getBlockStateIfAvailable(blockRoot);
    Optional<SignedBeaconBlock> inMemoryBlock = getBlockIfAvailable(blockRoot);
    if (inMemoryState.isPresent() && inMemoryBlock.isPresent()) {
      return SafeFuture.completedFuture(
          Optional.of(new SignedBlockAndState(inMemoryBlock.get(), inMemoryState.get())));
    }
    return regenerateState(blockRoot)
        .thenPeek(result -> result.map(SignedBlockAndState::getBlock).ifPresent(this::putBlock));
  }

  private SafeFuture<Optional<SignedBlockAndState>> regenerateState(final Bytes32 blockRoot) {
    return createStateGenerationTask(blockRoot)
        .thenCompose(
            maybeTask ->
                maybeTask.isPresent()
                    ? states.perform(maybeTask.get())
                    : EmptyStoreResults.EMPTY_BLOCK_AND_STATE_FUTURE);
  }

  private SafeFuture<Optional<StateGenerationTask>> createStateGenerationTask(
      final Bytes32 blockRoot) {
    if (!containsBlock(blockRoot)) {
      // If we don't have the corresponding block, we can't possibly regenerate the state
      return EmptyStoreResults.EMPTY_STATE_GENERATION_TASK;
    }

    // Accumulate blocks hashes until we find our base state to build from
    final HashTree.Builder treeBuilder = HashTree.builder();
    final AtomicReference<Bytes32> baseBlockRoot = new AtomicReference<>();
    final AtomicReference<BeaconState> baseState = new AtomicReference<>();
    final AtomicReference<Bytes32> latestEpochBoundary = new AtomicReference<>();
    readLock.lock();
    try {
      this.blockTree
          .getHashTree()
          .processHashesInChainWhile(
              blockRoot,
              (root, parent) -> {
                treeBuilder.childAndParentRoots(root, parent);
                final Optional<BeaconState> blockState = getBlockStateIfAvailable(root);
                if (blockState.isEmpty()
                    && blockTree.isRootAtEpochBoundary(root)
                    && shouldPersistStateAtEpoch(blockTree.getEpoch(root))) {
                  latestEpochBoundary.compareAndExchange(null, root);
                }
                blockState.ifPresent(
                    (state) -> {
                      // We found a base state
                      treeBuilder.rootHash(root);
                      baseBlockRoot.set(root);
                      baseState.set(state);
                    });
                return blockState.isEmpty();
              });
    } finally {
      readLock.unlock();
    }

    final SafeFuture<Optional<SignedBeaconBlock>> baseBlock;
    if (baseBlockRoot.get() == null) {
      // If we haven't found a base state yet, we must have walked back to the latest finalized
      // block, check here for the base state
      final SignedBlockAndState finalized = getLatestFinalizedBlockAndState();
      if (!treeBuilder.contains(finalized.getRoot())) {
        // We must have finalized a new block while processing and moved past our target root
        return EmptyStoreResults.EMPTY_STATE_GENERATION_TASK;
      }
      baseBlockRoot.set(finalized.getRoot());
      baseState.set(finalized.getState());
      baseBlock = SafeFuture.completedFuture(Optional.of(finalized.getBlock()));
      treeBuilder.rootHash(finalized.getRoot());
    } else {
      baseBlock = retrieveSignedBlock(baseBlockRoot.get());
    }

    // Regenerate state
    return baseBlock.thenApply(
        maybeBlock ->
            maybeBlock.map(
                block -> {
                  final SignedBlockAndState baseBlockAndState =
                      new SignedBlockAndState(block, baseState.get());
                  final HashTree tree = treeBuilder.build();
                  return new StateGenerationTask(
                      blockRoot,
                      tree,
                      baseBlockAndState,
                      Optional.ofNullable(latestEpochBoundary.get()),
                      blockProvider,
                      stateAndBlockProvider);
                }));
  }

  boolean shouldPersistStateAtEpoch(final UInt64 epoch) {
    return hotStatePersistenceFrequencyInEpochs > 0
        && epoch.mod(hotStatePersistenceFrequencyInEpochs).equals(UInt64.ZERO);
  }

  private void putBlock(final SignedBeaconBlock block) {
    final Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      if (containsBlock(block.getRoot())) {
        blocks.put(block.getRoot(), block);
        blockCountGauge.ifPresent(gauge -> gauge.set(blocks.size()));
      }
    } finally {
      writeLock.unlock();
    }
  }

  class Transaction implements StoreTransaction {

    private final StorageUpdateChannel storageUpdateChannel;
    Optional<UInt64> time = Optional.empty();
    Optional<UInt64> genesis_time = Optional.empty();
    Optional<Checkpoint> justified_checkpoint = Optional.empty();
    Optional<Checkpoint> finalized_checkpoint = Optional.empty();
    Optional<Checkpoint> best_justified_checkpoint = Optional.empty();
    Map<Bytes32, SlotAndBlockRoot> stateRoots = new HashMap<>();
    Map<Bytes32, SignedBlockAndState> blockAndStates = new HashMap<>();
    Map<UInt64, VoteTracker> votes = new ConcurrentHashMap<>();
    private final StoreUpdateHandler updateHandler;

    Transaction(
        final StorageUpdateChannel storageUpdateChannel, final StoreUpdateHandler updateHandler) {
      this.storageUpdateChannel = storageUpdateChannel;
      this.updateHandler = updateHandler;
    }

    @Override
    public void putBlockAndState(SignedBeaconBlock block, BeaconState state) {
      blockAndStates.put(block.getRoot(), new SignedBlockAndState(block, state));
      putStateRoot(
          state.hash_tree_root(),
          new SlotAndBlockRoot(block.getSlot(), block.getMessage().hash_tree_root()));
    }

    @Override
    public void putStateRoot(final Bytes32 stateRoot, final SlotAndBlockRoot slotAndBlockRoot) {
      stateRoots.put(stateRoot, slotAndBlockRoot);
    }

    @Override
    public void setTime(UInt64 time) {
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
        vote = Store.this.getVote(validatorIndex);
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
                        () ->
                            new IllegalStateException("Missing latest finalized block and state"));
                final StoreTransactionUpdates updates;
                // Lock so that we have a consistent view while calculating our updates
                final Lock writeLock = Store.this.lock.writeLock();
                writeLock.lock();
                try {
                  updates =
                      StoreTransactionUpdatesFactory.create(Store.this, this, latestFinalized);
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
              });
    }

    @Override
    public void commit(final Runnable onSuccess, final String errorMessage) {
      commit(onSuccess, err -> LOG.error(errorMessage, err));
    }

    @Override
    public UInt64 getTime() {
      return time.orElseGet(Store.this::getTime);
    }

    @Override
    public UInt64 getGenesisTime() {
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
    public SignedBlockAndState getLatestFinalizedBlockAndState() {
      if (finalized_checkpoint.isPresent()) {
        // Ideally we wouldn't join here - but seems not worth making this API async since we're
        // unlikely to call this on tx objects
        return retrieveBlockAndState(finalized_checkpoint.get().getRoot()).join().orElseThrow();
      }
      return Store.this.getLatestFinalizedBlockAndState();
    }

    @Override
    public UInt64 getLatestFinalizedBlockSlot() {
      return getLatestFinalizedBlockAndState().getSlot();
    }

    @Override
    public Checkpoint getBestJustifiedCheckpoint() {
      return best_justified_checkpoint.orElseGet(Store.this::getBestJustifiedCheckpoint);
    }

    @Override
    public boolean containsBlock(final Bytes32 blockRoot) {
      return blockAndStates.containsKey(blockRoot) || Store.this.containsBlock(blockRoot);
    }

    @Override
    public Set<Bytes32> getBlockRoots() {
      return Sets.union(blockAndStates.keySet(), Store.this.getBlockRoots());
    }

    @Override
    public List<Bytes32> getOrderedBlockRoots() {
      if (this.blockAndStates.isEmpty()) {
        return Store.this.getOrderedBlockRoots();
      }

      Store.this.lock.readLock().lock();
      try {
        final HashTree.Builder treeBuilder = Store.this.blockTree.getHashTree().updater();
        this.blockAndStates.values().stream()
            .map(SignedBlockAndState::getBlock)
            .forEach(treeBuilder::block);
        return treeBuilder.build().breadthFirstStream().collect(Collectors.toList());
      } finally {
        Store.this.lock.readLock().unlock();
      }
    }

    @Override
    public Optional<BeaconState> getBlockStateIfAvailable(final Bytes32 blockRoot) {
      return Optional.ofNullable(blockAndStates.get(blockRoot))
          .map(SignedBlockAndState::getState)
          .or(() -> Store.this.getBlockStateIfAvailable(blockRoot));
    }

    @Override
    public SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(Bytes32 blockRoot) {
      if (blockAndStates.containsKey(blockRoot)) {
        return SafeFuture.completedFuture(
            Optional.of(blockAndStates.get(blockRoot)).map(SignedBlockAndState::getBlock));
      }
      return Store.this.retrieveSignedBlock(blockRoot);
    }

    @Override
    public SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(Bytes32 blockRoot) {
      if (blockAndStates.containsKey(blockRoot)) {
        final SignedBlockAndState result = blockAndStates.get(blockRoot);
        return SafeFuture.completedFuture(Optional.of(result));
      }
      return Store.this.retrieveBlockAndState(blockRoot);
    }

    @Override
    public SafeFuture<Optional<BeaconState>> retrieveBlockState(Bytes32 blockRoot) {
      if (blockAndStates.containsKey(blockRoot)) {
        return SafeFuture.completedFuture(
            Optional.of(blockAndStates.get(blockRoot)).map(SignedBlockAndState::getState));
      }
      return Store.this.retrieveBlockState(blockRoot);
    }

    @Override
    public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(Checkpoint checkpoint) {
      SignedBlockAndState inMemoryCheckpointBlockState = blockAndStates.get(checkpoint.getRoot());
      if (inMemoryCheckpointBlockState != null) {
        // Not executing the task via the task queue to avoid caching the result before the tx is
        // committed
        return new CheckpointStateTask(
                checkpoint,
                root ->
                    SafeFuture.completedFuture(
                        Optional.of(inMemoryCheckpointBlockState.getState())))
            .performTask();
      }
      return Store.this.retrieveCheckpointState(checkpoint);
    }

    @Override
    public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
        final Checkpoint checkpoint, final BeaconState latestStateAtEpoch) {
      return Store.this.retrieveCheckpointState(checkpoint, latestStateAtEpoch);
    }

    @Override
    public Optional<SignedBeaconBlock> getBlockIfAvailable(final Bytes32 blockRoot) {
      return Optional.ofNullable(blockAndStates.get(blockRoot))
          .map(SignedBlockAndState::getBlock)
          .or(() -> Store.this.getBlockIfAvailable(blockRoot));
    }

    @Override
    public Set<UInt64> getVotedValidatorIndices() {
      return Sets.union(votes.keySet(), Store.this.getVotedValidatorIndices());
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
        && Objects.equals(
            hotStatePersistenceFrequencyInEpochs, store.hotStatePersistenceFrequencyInEpochs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        time,
        genesis_time,
        justified_checkpoint,
        finalized_checkpoint,
        best_justified_checkpoint,
        blocks);
  }
}
