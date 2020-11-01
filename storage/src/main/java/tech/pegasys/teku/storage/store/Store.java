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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.HashSet;
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
import tech.pegasys.teku.core.stategenerator.StateRegenerationBaseSelector;
import tech.pegasys.teku.datastructures.ForkChoiceStrategy;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BlockRootAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ProtoArrayForkChoiceStrategy;
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

  private final Optional<Checkpoint> anchor;
  final ProtoArrayForkChoiceStrategy forkChoiceStrategy;
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
      final Optional<Checkpoint> anchor,
      final UInt64 time,
      final UInt64 genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final ProtoArrayForkChoiceStrategy forkChoiceStrategy,
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
    this.anchor = anchor;
    this.hotStatePersistenceFrequencyInEpochs = hotStatePersistenceFrequencyInEpochs;
    this.time = time;
    this.genesis_time = genesis_time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.best_justified_checkpoint = best_justified_checkpoint;
    this.blocks = blocks;
    this.votes = new HashMap<>(votes);
    this.forkChoiceStrategy = forkChoiceStrategy;

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

  public static UpdatableStore create(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final BlockProvider blockProvider,
      final StateAndBlockProvider stateAndBlockProvider,
      final Optional<Checkpoint> anchor,
      final UInt64 time,
      final UInt64 genesisTime,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint bestJustifiedCheckpoint,
      final ProtoArrayForkChoiceStrategy protoArrayForkChoiceStrategy,
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

    return new Store(
        metricsSystem,
        config.getHotStatePersistenceFrequencyInEpochs(),
        blockProvider,
        stateAndBlockProvider,
        stateTaskQueue,
        anchor,
        time,
        genesisTime,
        justifiedCheckpoint,
        finalizedCheckpoint,
        bestJustifiedCheckpoint,
        protoArrayForkChoiceStrategy,
        finalizedBlockAndState,
        votes,
        blocks,
        checkpointStateTaskQueue);
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
  public Optional<Checkpoint> getAnchor() {
    return anchor;
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
  public ForkChoiceStrategy getForkChoiceStrategy() {
    return forkChoiceStrategy;
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
    return forkChoiceStrategy.contains(blockRoot);
  }

  @VisibleForTesting
  @Override
  public Set<Bytes32> getBlockRoots() {
    return forkChoiceStrategy.getBlockRoots();
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

    // Create a hash tree from the finalized root to the target state
    // Capture the latest epoch boundary root along the way
    final HashTree.Builder treeBuilder = HashTree.builder();
    final AtomicReference<SlotAndBlockRoot> latestEpochBoundary = new AtomicReference<>();
    readLock.lock();
    try {
      forkChoiceStrategy.processHashesInChain(
          blockRoot,
          (root, slot, parent) -> {
            treeBuilder.childAndParentRoots(root, parent);
            if (shouldPersistState(slot, parent)) {
              latestEpochBoundary.compareAndExchange(null, new SlotAndBlockRoot(slot, root));
            }
          });
      treeBuilder.rootHash(finalizedBlockAndState.getRoot());
    } finally {
      readLock.unlock();
    }

    return SafeFuture.completedFuture(
        Optional.of(
            new StateGenerationTask(
                blockRoot,
                treeBuilder.build(),
                blockProvider,
                new StateRegenerationBaseSelector(
                    Optional.ofNullable(latestEpochBoundary.get()),
                    () -> getClosestAvailableBlockRootAndState(blockRoot),
                    stateAndBlockProvider,
                    blockProvider,
                    Optional.empty(),
                    hotStatePersistenceFrequencyInEpochs))));
  }

  private Optional<BlockRootAndState> getClosestAvailableBlockRootAndState(
      final Bytes32 blockRoot) {
    if (!containsBlock(blockRoot)) {
      // If we don't have the corresponding block, we can't possibly regenerate the state
      return Optional.empty();
    }

    // Accumulate blocks hashes until we find our base state to build from
    final HashTree.Builder treeBuilder = HashTree.builder();
    final AtomicReference<Bytes32> baseBlockRoot = new AtomicReference<>();
    final AtomicReference<BeaconState> baseState = new AtomicReference<>();
    readLock.lock();
    try {
      forkChoiceStrategy.processHashesInChainWhile(
          blockRoot,
          (root, slot, parent) -> {
            treeBuilder.childAndParentRoots(root, parent);
            final Optional<BeaconState> blockState = getBlockStateIfAvailable(root);
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

    if (baseBlockRoot.get() == null) {
      // If we haven't found a base state yet, we must have walked back to the latest finalized
      // block, check here for the base state
      final SignedBlockAndState finalized = getLatestFinalizedBlockAndState();
      if (!treeBuilder.contains(finalized.getRoot())) {
        // We must have finalized a new block while processing and moved past our target root
        return Optional.empty();
      }
      baseBlockRoot.set(finalized.getRoot());
      baseState.set(finalized.getState());
      treeBuilder.rootHash(finalized.getRoot());
    }

    return Optional.of(new BlockRootAndState(baseBlockRoot.get(), baseState.get()));
  }

  boolean shouldPersistState(final UInt64 blockSlot, final Bytes32 parentRoot) {
    return hotStatePersistenceFrequencyInEpochs > 0
        && isRootAtNthEpochBoundary(blockSlot, parentRoot, hotStatePersistenceFrequencyInEpochs);
  }

  private boolean isRootAtNthEpochBoundary(
      final UInt64 blockSlot, final Bytes32 parentRoot, final int n) {
    return forkChoiceStrategy
        .blockSlot(parentRoot)
        .map(
            parentSlot -> {
              final UInt64 blockEpoch = compute_epoch_at_slot(blockSlot);
              final UInt64 parentEpoch = compute_epoch_at_slot(parentSlot);
              return blockEpoch.dividedBy(n).isGreaterThan(parentEpoch.dividedBy(n));
            })
        .orElse(false);
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
