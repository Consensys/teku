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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.core.lookup.BlockProvider.fromDynamicMap;
import static tech.pegasys.teku.core.lookup.BlockProvider.fromMap;
import static tech.pegasys.teku.core.stategenerator.CheckpointStateTask.AsyncStateProvider.fromBlockAndState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;

class Store implements UpdatableStore {
  private static final Logger LOG = LogManager.getLogger();

  private final int hotStatePersistenceFrequencyInEpochs;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final MetricsSystem metricsSystem;
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
  private final CachingTaskQueue<Checkpoint, BeaconState> checkpointStates;
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
      final CachingTaskQueue<Checkpoint, BeaconState> checkpointStates) {
    checkArgument(
        time.isGreaterThanOrEqualTo(genesis_time),
        "Time must be greater than or equal to genesisTime");
    this.stateAndBlockProvider = stateAndBlockProvider;
    LOG.trace(
        "Create store with hot state persistence configured to {}",
        hotStatePersistenceFrequencyInEpochs);

    // Set up metrics
    this.metricsSystem = metricsSystem;
    this.states = states;
    this.checkpointStates = checkpointStates;

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
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final BlockProvider blockProvider,
      final StateAndBlockProvider stateAndBlockProvider,
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
            asyncRunner,
            metricsSystem,
            "memory_checkpoint_states",
            config.getCheckpointStateCacheSize());
    final CachingTaskQueue<Bytes32, SignedBlockAndState> stateTaskQueue =
        CachingTaskQueue.create(
            asyncRunner, metricsSystem, "memory_states", config.getStateCacheSize());

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
      checkpointStates.startMetrics();
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
    return new tech.pegasys.teku.storage.store.StoreTransaction(
        this, lock, storageUpdateChannel, updateHandler);
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
    return checkpointStates.perform(new CheckpointStateTask(checkpoint, this::retrieveBlockState));
  }

  @Override
  public SafeFuture<CheckpointState> retrieveFinalizedCheckpointAndState() {
    final Checkpoint finalizedCheckpoint;
    final SignedBlockAndState finalizedBlockAndState;

    readLock.lock();
    try {
      finalizedCheckpoint = this.finalized_checkpoint;
      finalizedBlockAndState = this.finalizedBlockAndState;
    } finally {
      readLock.unlock();
    }

    return checkpointStates
        .perform(
            new CheckpointStateTask(finalizedCheckpoint, fromBlockAndState(finalizedBlockAndState)))
        .thenApply(
            maybeState ->
                new CheckpointState(
                    finalizedCheckpoint,
                    finalizedBlockAndState.getBlock(),
                    maybeState.orElseThrow()));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
      Checkpoint checkpoint, final BeaconState latestStateAtEpoch) {
    return checkpointStates.perform(
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

  VoteTracker getVote(UInt64 validatorIndex) {
    readLock.lock();
    try {
      return votes.get(validatorIndex);
    } finally {
      readLock.unlock();
    }
  }

  private SafeFuture<Optional<BeaconState>> getAndCacheBlockState(final Bytes32 blockRoot) {
    return getOrRegenerateBlockAndState(blockRoot)
        .thenApply(res -> res.map(SignedBlockAndState::getState));
  }

  private SafeFuture<Optional<SignedBlockAndState>> getAndCacheBlockAndState(
      final Bytes32 blockRoot) {
    return getOrRegenerateBlockAndState(blockRoot)
        .thenPeek(result -> result.map(SignedBlockAndState::getBlock).ifPresent(this::putBlock));
  }

  private SafeFuture<Optional<SignedBlockAndState>> getOrRegenerateBlockAndState(
      final Bytes32 blockRoot) {
    // Avoid generating the hash tree to rebuild if the state is already available.
    final Optional<SignedBlockAndState> cachedResult = states.getIfAvailable(blockRoot);
    if (cachedResult.isPresent()) {
      return SafeFuture.completedFuture(cachedResult);
    }
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
      blockTree
          .getHashTree()
          .processHashesInChainWhile(
              blockRoot,
              (root, parent) -> {
                treeBuilder.childAndParentRoots(root, parent);
                final Optional<BeaconState> blockState = getBlockStateIfAvailable(root);
                if (blockState.isEmpty() && shouldPersistState(blockTree, root)) {
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

  boolean shouldPersistState(final BlockTree blockTree, final Bytes32 root) {
    return hotStatePersistenceFrequencyInEpochs > 0
        && blockTree.isRootAtNthEpochBoundary(root, hotStatePersistenceFrequencyInEpochs);
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
}
