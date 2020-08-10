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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.stategenerator.StateGenerator;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.metrics.SettableGauge;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.util.collections.ConcurrentLimitedMap;
import tech.pegasys.teku.util.collections.LimitStrategy;
import tech.pegasys.teku.util.collections.LimitedMap;

class Store implements UpdatableStore {
  private static final Logger LOG = LogManager.getLogger();

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Counter stateRequestCachedCounter;
  private final Counter stateRequestRegenerateCounter;
  private final Counter stateRequestMissCounter;
  private final Counter checkpointStateRequestCachedCounter;
  private final Counter checkpointStateRequestRegenerateCounter;
  private final Counter checkpointStateRequestMissCounter;
  private final MetricsSystem metricsSystem;
  private Optional<SettableGauge> stateCountGauge = Optional.empty();
  private Optional<SettableGauge> blockCountGauge = Optional.empty();
  private Optional<SettableGauge> checkpointCountGauge = Optional.empty();

  private final BlockProvider blockProvider;

  HashTree blockTree;
  UInt64 time;
  UInt64 genesis_time;
  Checkpoint justified_checkpoint;
  Checkpoint finalized_checkpoint;
  Checkpoint best_justified_checkpoint;
  Map<Bytes32, SignedBeaconBlock> blocks;
  Map<Bytes32, BeaconState> block_states;
  Map<Checkpoint, BeaconState> checkpoint_states;
  Map<UInt64, VoteTracker> votes;
  SignedBlockAndState finalizedBlockAndState;

  private Store(
      final MetricsSystem metricsSystem,
      final BlockProvider blockProvider,
      final UInt64 time,
      final UInt64 genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final HashTree blockTree,
      final SignedBlockAndState finalizedBlockAndState,
      final Map<UInt64, VoteTracker> votes,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Bytes32, BeaconState> block_states,
      final Map<Checkpoint, BeaconState> checkpoint_states) {

    // Set up metrics
    this.metricsSystem = metricsSystem;
    final LabelledMetric<Counter> stateRequestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.STORAGE,
            "memory_state_requests",
            "Number of requests for BeaconState from the in-memory store",
            "result");
    stateRequestCachedCounter = stateRequestCounter.labels("cached");
    stateRequestRegenerateCounter = stateRequestCounter.labels("regenerate");
    stateRequestMissCounter = stateRequestCounter.labels("miss");
    final LabelledMetric<Counter> checkpointStateRequestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.STORAGE,
            "memory_checkpoint_state_requests",
            "Number of requests for checkpoint states from the in-memory store",
            "result");
    checkpointStateRequestCachedCounter = checkpointStateRequestCounter.labels("cached");
    checkpointStateRequestRegenerateCounter = checkpointStateRequestCounter.labels("regenerate");
    checkpointStateRequestMissCounter = checkpointStateRequestCounter.labels("miss");

    // Store instance variables
    this.time = time;
    this.genesis_time = genesis_time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.best_justified_checkpoint = best_justified_checkpoint;
    this.blocks = blocks;
    this.block_states = block_states;
    this.checkpoint_states = checkpoint_states;
    this.votes = new ConcurrentHashMap<>(votes);
    this.blockTree = blockTree;

    // Track latest finalized block
    this.finalizedBlockAndState = finalizedBlockAndState;
    putBlockState(finalizedBlockAndState.getRoot(), finalizedBlockAndState.getState());

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
      final UInt64 time,
      final UInt64 genesisTime,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint bestJustifiedCheckpoint,
      final Map<Bytes32, Bytes32> childToParentRoot,
      final SignedBlockAndState finalizedBlockAndState,
      final Map<UInt64, VoteTracker> votes,
      final StorePruningOptions pruningOptions) {

    // Create limited collections for non-final data
    final Map<Bytes32, SignedBeaconBlock> blocks =
        ConcurrentLimitedMap.create(
            pruningOptions.getBlockCacheSize(), LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
    final Map<Bytes32, BeaconState> blockStates =
        LimitedMap.create(
            pruningOptions.getStateCacheSize(), LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
    final Map<Checkpoint, BeaconState> checkpointStates =
        LimitedMap.create(
            pruningOptions.getCheckpointStateCacheSize(),
            LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);

    // Build block tree structure
    HashTree.Builder treeBuilder = HashTree.builder().rootHash(finalizedBlockAndState.getRoot());
    childToParentRoot.forEach(treeBuilder::childAndParentRoots);
    final HashTree blockTree = treeBuilder.build();
    if (blockTree.size() < childToParentRoot.size()) {
      // This should be an error, but keeping this as a warning now for backwards-compatibility
      // reasons.  Some existing databases may have unpruned fork blocks, and could become
      // unusable
      // if we throw here.  In the future, we should convert this to an error.
      LOG.warn("Ignoring {} non-canonical blocks", childToParentRoot.size() - blockTree.size());
    }

    // Create state generator
    final BlockProvider blockProviderWithBlocks =
        BlockProvider.combined(
            fromMap(Map.of(finalizedBlockAndState.getRoot(), finalizedBlockAndState.getBlock())),
            fromMap(blocks),
            blockProvider);
    final StateGenerator stateGenerator =
        StateGenerator.create(blockTree, finalizedBlockAndState, blockProviderWithBlocks);

    // Process blocks
    LOG.info("Process {} block(s) to regenerate state", blockTree.size());
    final AtomicInteger processedBlocks = new AtomicInteger(0);
    return stateGenerator
        .regenerateAllStates(
            (block, state) -> {
              final int processed = processedBlocks.incrementAndGet();
              if (processed % 100 == 0) {
                LOG.info("Processed {} blocks", processed);
              }
              blocks.put(block.getRoot(), block);
              blockStates.put(block.getRoot(), state);
            })
        .thenApply(
            __ -> {
              LOG.info("Finished processing {} block(s)", blockTree.size());

              return new Store(
                  metricsSystem,
                  blockProvider,
                  time,
                  genesisTime,
                  justifiedCheckpoint,
                  finalizedCheckpoint,
                  bestJustifiedCheckpoint,
                  blockTree,
                  finalizedBlockAndState,
                  votes,
                  blocks,
                  blockStates,
                  checkpointStates);
            });
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
      stateCountGauge =
          Optional.of(
              SettableGauge.create(
                  metricsSystem,
                  TekuMetricCategory.STORAGE,
                  "memory_state_count",
                  "Number of beacon states held in the in-memory store"));
      blockCountGauge =
          Optional.of(
              SettableGauge.create(
                  metricsSystem,
                  TekuMetricCategory.STORAGE,
                  "memory_block_count",
                  "Number of beacon blocks held in the in-memory store"));
      checkpointCountGauge =
          Optional.of(
              SettableGauge.create(
                  metricsSystem,
                  TekuMetricCategory.STORAGE,
                  "memory_checkpoint_state_count",
                  "Number of checkpoint states held in the in-memory store"));
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
      return blockTree.breadthFirstStream().collect(Collectors.toList());
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Optional<BeaconState> getBlockStateIfAvailable(final Bytes32 blockRoot) {
    readLock.lock();
    try {
      final Optional<BeaconState> state = Optional.ofNullable(block_states.get(blockRoot));
      state.ifPresentOrElse(s -> stateRequestCachedCounter.inc(), stateRequestMissCounter::inc);
      return state;
    } finally {
      readLock.unlock();
    }
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
    Optional<BeaconState> inMemoryCheckpoint = getCheckpointStateIfAvailable(checkpoint);
    if (inMemoryCheckpoint.isPresent()) {
      return SafeFuture.completedFuture(inMemoryCheckpoint);
    }
    return retrieveBlockState(checkpoint.getRoot())
        .thenApply(
            state ->
                state.map(
                    baseState -> {
                      final BeaconState checkpointState =
                          regenerateCheckpointState(checkpoint, baseState);
                      putCheckpointState(checkpoint, checkpointState);
                      return checkpointState;
                    }));
  }

  private Optional<BeaconState> getCheckpointStateIfAvailable(final Checkpoint checkpoint) {
    readLock.lock();
    try {
      final BeaconState state = checkpoint_states.get(checkpoint);
      if (state != null) {
        checkpointStateRequestCachedCounter.inc();
        return Optional.of(state);
      } else {
        checkpointStateRequestMissCounter.inc();
        return Optional.empty();
      }
    } finally {
      readLock.unlock();
    }
  }

  private BeaconState regenerateCheckpointState(
      final Checkpoint checkpoint, BeaconState baseState) {
    try {
      if (baseState.getSlot().equals(checkpoint.getEpochStartSlot())) {
        return baseState;
      }

      checkpointStateRequestRegenerateCounter.inc();
      return new StateTransition().process_slots(baseState, checkpoint.getEpochStartSlot());
    } catch (SlotProcessingException | EpochProcessingException | IllegalArgumentException e) {
      throw new InvalidCheckpointException(e);
    }
  }

  @Override
  public Set<UInt64> getVotedValidatorIndices() {
    readLock.lock();
    try {
      return votes.keySet();
    } finally {
      readLock.unlock();
    }
  }

  private SafeFuture<Optional<BeaconState>> getAndCacheBlockState(final Bytes32 blockRoot) {
    Optional<BeaconState> inMemoryState = getBlockStateIfAvailable(blockRoot);
    if (inMemoryState.isPresent()) {
      return SafeFuture.completedFuture(inMemoryState);
    }
    return regenerateState(blockRoot, this::cacheState)
        .thenApply(res -> res.map(SignedBlockAndState::getState));
  }

  private SafeFuture<Optional<SignedBlockAndState>> getAndCacheBlockAndState(
      final Bytes32 blockRoot) {
    return regenerateState(blockRoot, this::cacheBlockAndState);
  }

  private SafeFuture<Optional<SignedBlockAndState>> regenerateState(
      final Bytes32 blockRoot, final Consumer<SignedBlockAndState> cacheHandler) {
    if (!containsBlock(blockRoot)) {
      // If we don't have the corresponding block, we can't possibly regenerate the state
      return EmptyStoreResults.EMPTY_BLOCK_AND_STATE_FUTURE;
    }

    // Accumulate blocks hashes until we find our base state to build from
    final HashTree.Builder treeBuilder = HashTree.builder();
    final AtomicReference<Bytes32> baseBlockRoot = new AtomicReference<>();
    final AtomicReference<BeaconState> baseState = new AtomicReference<>();
    readLock.lock();
    try {
      this.blockTree.processHashesInChainWhile(
          blockRoot,
          (root, parent) -> {
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

    final SafeFuture<Optional<SignedBeaconBlock>> baseBlock;
    if (baseBlockRoot.get() == null) {
      // If we haven't found a base state yet, we must have walked back to the latest finalized
      // block, check here for the base state
      final SignedBlockAndState finalized = getLatestFinalizedBlockAndState();
      if (!treeBuilder.contains(finalized.getRoot())) {
        // We must have finalized a new block while processing and moved past our target root
        return EmptyStoreResults.EMPTY_BLOCK_AND_STATE_FUTURE;
      }
      baseBlockRoot.set(finalized.getRoot());
      baseState.set(finalized.getState());
      baseBlock = SafeFuture.completedFuture(Optional.of(finalized.getBlock()));
      treeBuilder.rootHash(finalized.getRoot());
    } else {
      baseBlock = retrieveSignedBlock(baseBlockRoot.get());
    }

    // Regenerate state
    return baseBlock.thenCompose(
        maybeBlock ->
            maybeBlock
                .map(
                    block -> {
                      final SignedBlockAndState baseBlockAndState =
                          new SignedBlockAndState(block, baseState.get());
                      final HashTree tree = treeBuilder.build();
                      final StateGenerator stateGenerator =
                          StateGenerator.create(tree, baseBlockAndState, blockProvider);
                      return stateGenerator
                          .regenerateStateForBlock(blockRoot)
                          .thenApply(
                              result -> {
                                stateRequestRegenerateCounter.inc();
                                cacheHandler.accept(result);
                                return Optional.of(result);
                              });
                    })
                .orElse(EmptyStoreResults.EMPTY_BLOCK_AND_STATE_FUTURE));
  }

  private void cacheBlockAndState(final SignedBlockAndState blockAndState) {
    putBlockState(blockAndState.getRoot(), blockAndState.getState());
    putBlock(blockAndState.getBlock());
  }

  private void cacheState(final SignedBlockAndState blockAndState) {
    putBlockState(blockAndState.getRoot(), blockAndState.getState());
  }

  private void putCheckpointState(final Checkpoint checkpoint, final BeaconState state) {
    lock.writeLock().lock();
    try {
      checkpoint_states.put(checkpoint, state);
      checkpointCountGauge.ifPresent(gauge -> gauge.set(checkpoint_states.size()));
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void putBlockState(final Bytes32 blockRoot, final BeaconState state) {
    final Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      if (containsBlock(blockRoot)) {
        block_states.put(blockRoot, state);
        stateCountGauge.ifPresent(gauge -> gauge.set(block_states.size()));
      }
    } finally {
      writeLock.unlock();
    }
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
    Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BeaconState> block_states = new HashMap<>();
    Map<UInt64, VoteTracker> votes = new ConcurrentHashMap<>();
    private final StoreUpdateHandler updateHandler;

    Transaction(
        final StorageUpdateChannel storageUpdateChannel, final StoreUpdateHandler updateHandler) {
      this.storageUpdateChannel = storageUpdateChannel;
      this.updateHandler = updateHandler;
    }

    @Override
    public void putBlockAndState(SignedBeaconBlock block, BeaconState state) {
      blocks.put(block.getRoot(), block);
      block_states.put(block.getRoot(), state);
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
        updates = StoreTransactionUpdatesFactory.create(Store.this, this).join();
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
      return blocks.containsKey(blockRoot) || Store.this.containsBlock(blockRoot);
    }

    @Override
    public Set<Bytes32> getBlockRoots() {
      return Sets.union(blocks.keySet(), Store.this.getBlockRoots());
    }

    @Override
    public List<Bytes32> getOrderedBlockRoots() {
      if (this.blocks.isEmpty()) {
        return Store.this.getOrderedBlockRoots();
      }

      Store.this.lock.readLock().lock();
      try {
        final HashTree.Builder treeBuilder = Store.this.blockTree.updater();
        this.blocks.values().forEach(treeBuilder::block);
        return treeBuilder.build().breadthFirstStream().collect(Collectors.toList());
      } finally {
        Store.this.lock.readLock().unlock();
      }
    }

    @Override
    public Optional<BeaconState> getBlockStateIfAvailable(final Bytes32 blockRoot) {
      return Optional.ofNullable(block_states.get(blockRoot))
          .or(() -> Store.this.getBlockStateIfAvailable(blockRoot));
    }

    @Override
    public SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(Bytes32 blockRoot) {
      if (blocks.containsKey(blockRoot)) {
        return SafeFuture.completedFuture(Optional.of(blocks.get(blockRoot)));
      }
      return Store.this.retrieveSignedBlock(blockRoot);
    }

    @Override
    public SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(Bytes32 blockRoot) {
      if (blocks.containsKey(blockRoot)) {
        final SignedBlockAndState result =
            new SignedBlockAndState(blocks.get(blockRoot), block_states.get(blockRoot));
        return SafeFuture.completedFuture(Optional.of(result));
      }
      return Store.this.retrieveBlockAndState(blockRoot);
    }

    @Override
    public SafeFuture<Optional<BeaconState>> retrieveBlockState(Bytes32 blockRoot) {
      if (block_states.containsKey(blockRoot)) {
        return SafeFuture.completedFuture(Optional.of(block_states.get(blockRoot)));
      }
      return Store.this.retrieveBlockState(blockRoot);
    }

    @Override
    public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(Checkpoint checkpoint) {
      BeaconState inMemoryCheckpointBlockState = block_states.get(checkpoint.getRoot());
      if (inMemoryCheckpointBlockState != null) {
        return SafeFuture.completedFuture(
            Optional.of(regenerateCheckpointState(checkpoint, inMemoryCheckpointBlockState)));
      }
      return Store.this.retrieveCheckpointState(checkpoint);
    }

    @Override
    public Optional<SignedBeaconBlock> getBlockIfAvailable(final Bytes32 blockRoot) {
      return Optional.ofNullable(blocks.get(blockRoot))
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
