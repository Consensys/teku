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
import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
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
import java.util.function.Function;
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
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointAndBlock;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.collections.ConcurrentLimitedMap;
import tech.pegasys.teku.util.collections.LimitStrategy;
import tech.pegasys.teku.util.collections.LimitedMap;

class Store implements UpdatableStore {
  private static final Logger LOG = LogManager.getLogger();

  private static final SafeFuture<Optional<BeaconState>> EMPTY_STATE_FUTURE =
      SafeFuture.completedFuture(Optional.empty());
  private static final SafeFuture<Optional<SignedBeaconBlock>> EMPTY_BLOCK_FUTURE =
      SafeFuture.completedFuture(Optional.empty());

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Counter stateRequestCachedCounter;
  private final Counter stateRequestRegenerateCounter;
  private final Counter stateRequestMissCounter;
  private final Counter checkpointStateRequestCachedCounter;
  private final Counter checkpointStateRequestRegenerateCounter;
  private final Counter checkpointStateRequestMissCounter;
  private final MetricsSystem metricsSystem;

  private final BlockProvider blockProvider;

  HashTree blockTree;
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

  Store(
      final MetricsSystem metricsSystem,
      final BlockProvider blockProvider,
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, Bytes32> childToParentRoot,
      final SignedBlockAndState finalizedBlockAndState,
      final Map<UnsignedLong, VoteTracker> votes,
      final StorePruningOptions pruningOptions) {
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

    this.time = time;
    this.genesis_time = genesis_time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.best_justified_checkpoint = best_justified_checkpoint;
    this.blocks =
        ConcurrentLimitedMap.create(
            pruningOptions.getBlockCacheSize(), LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
    this.block_states =
        LimitedMap.create(
            pruningOptions.getStateCacheSize(), LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
    this.checkpoint_states =
        LimitedMap.create(
            pruningOptions.getCheckpointStateCacheSize(),
            LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
    this.votes = new ConcurrentHashMap<>(votes);

    // Build block tree structure
    HashTree.Builder treeBuilder = HashTree.builder().rootHash(finalizedBlockAndState.getRoot());
    childToParentRoot.forEach(treeBuilder::childAndParentRoots);
    this.blockTree = treeBuilder.build();

    // Track latest finalized block
    this.finalizedBlockAndState = finalizedBlockAndState;
    putBlockState(finalizedBlockAndState.getRoot(), finalizedBlockAndState.getState());

    this.blockProvider =
        BlockProvider.combined(
            fromDynamicMap(
                () -> {
                  SignedBlockAndState finalized = this.getLatestFinalizedBlockAndState();
                  return Map.of(finalized.getRoot(), finalized.getBlock());
                }),
            fromMap(this.blocks),
            blockProvider);

    // Create state generator
    final StateGenerator stateGenerator =
        StateGenerator.create(blockTree, finalizedBlockAndState, this.blockProvider);
    if (blockTree.size() < childToParentRoot.size()) {
      // This should be an error, but keeping this as a warning now for backwards-compatibility
      // reasons.  Some existing databases may have unpruned fork blocks, and could become
      // unusable
      // if we throw here.  In the future, we should convert this to an error.
      LOG.warn("Ignoring {} non-canonical blocks", childToParentRoot.size() - blockTree.size());
    }

    // Process blocks
    LOG.info("Process {} block(s) to regenerate state", blockTree.size());
    final AtomicInteger processedBlocks = new AtomicInteger(0);
    // TODO(#2291) - handle future properly
    stateGenerator
        .regenerateAllStates(
            (block, state) -> {
              final int processed = processedBlocks.incrementAndGet();
              if (processed % 100 == 0) {
                LOG.info("Processed {} blocks", processed);
              }
              putBlock(block);
              putBlockState(block.getRoot(), state);
            })
        .join();
    LOG.info("Finished processing {} block(s)", blockTree.size());
  }

  /**
   * Start reporting gauge values to metrics.
   *
   * <p>Gauges can only be created once so we delay initializing these metrics until we know that
   * this instance is the canonical store.
   */
  @Override
  public void startMetrics() {
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        "memory_state_count",
        "Number of beacon states held in the in-memory store",
        this::countStates);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        "memory_block_count",
        "Number of beacon blocks held in the in-memory store",
        this::countBlocks);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        "memory_checkpoint_state_count",
        "Number of checkpoint states held in the in-memory store",
        this::countCheckpointStates);
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
      final SignedBeaconBlock block = finalizedBlockAndState.getBlock();
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
  public BeaconBlock getBlock(Bytes32 blockRoot) {
    final SignedBeaconBlock signedBlock = getSignedBlock(blockRoot);
    return signedBlock != null ? signedBlock.getMessage() : null;
  }

  @Override
  public SignedBeaconBlock getSignedBlock(Bytes32 blockRoot) {
    // TODO(#2291) - handle future
    return retrieveSignedBlock(blockRoot).join().orElse(null);
  }

  @Override
  public Optional<SignedBlockAndState> getBlockAndState(final Bytes32 blockRoot) {
    final SignedBeaconBlock block = getSignedBlock(blockRoot);
    if (block == null) {
      return Optional.empty();
    }
    // TODO(#2291) - handle future properly
    return getOrGenerateBlockState(blockRoot)
        .join()
        .map((state) -> new SignedBlockAndState(block, state));
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
  public BeaconState getBlockState(Bytes32 blockRoot) {
    // TODO(#2291) - handle future properly
    return getOrGenerateBlockState(blockRoot).join().orElse(null);
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
  public BeaconState getCheckpointState(Checkpoint checkpoint) {
    return getCheckpointStateIfAvailable(checkpoint)
        .or(() -> regenerateAndStoreCheckpointState(checkpoint))
        .orElse(null);
  }

  private Optional<? extends BeaconState> regenerateAndStoreCheckpointState(
      final Checkpoint checkpoint) {
    final Optional<BeaconState> checkpointState =
        regenerateCheckpointState(checkpoint, this::getBlockState);
    checkpointState.ifPresent(
        state -> {
          lock.writeLock().lock();
          try {
            checkpoint_states.put(checkpoint, state);
          } finally {
            lock.writeLock().unlock();
          }
        });
    return checkpointState;
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

  private Optional<BeaconState> regenerateCheckpointState(
      final Checkpoint checkpoint, Function<Bytes32, BeaconState> getBlockState) {
    try {
      final BeaconState baseState = getBlockState.apply(checkpoint.getRoot());
      if (baseState == null || baseState.getSlot().equals(checkpoint.getEpochStartSlot())) {
        return Optional.ofNullable(baseState);
      }

      checkpointStateRequestRegenerateCounter.inc();
      return Optional.of(
          new StateTransition().process_slots(baseState, checkpoint.getEpochStartSlot()));
    } catch (SlotProcessingException | EpochProcessingException | IllegalArgumentException e) {
      throw new InvalidCheckpointException(e);
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

  private SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(final Bytes32 blockRoot) {
    if (!containsBlock(blockRoot)) {
      return EMPTY_BLOCK_FUTURE;
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

  private SafeFuture<Optional<BeaconState>> getOrGenerateBlockState(final Bytes32 blockRoot) {
    Optional<BeaconState> inMemoryState = getBlockStateIfAvailable(blockRoot);
    if (inMemoryState.isPresent()) {
      return SafeFuture.completedFuture(inMemoryState);
    }
    if (!containsBlock(blockRoot)) {
      // If we don't have the corresponding block, we can't possibly regenerate the state
      return EMPTY_STATE_FUTURE;
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
        return EMPTY_STATE_FUTURE;
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
                              regeneratedState -> {
                                stateRequestRegenerateCounter.inc();
                                putBlockState(blockRoot, regeneratedState);
                                return Optional.of(regeneratedState);
                              });
                    })
                .orElse(EMPTY_STATE_FUTURE));
  }

  private void putBlockState(final Bytes32 blockRoot, final BeaconState state) {
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

  private void putBlock(final SignedBeaconBlock block) {
    final Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      if (containsBlock(block.getRoot())) {
        blocks.put(block.getRoot(), block);
      }
    } finally {
      writeLock.unlock();
    }
  }

  private int countBlocks() {
    readLock.lock();
    try {
      return blocks.size();
    } finally {
      readLock.unlock();
    }
  }

  private int countStates() {
    readLock.lock();
    try {
      return block_states.size();
    } finally {
      readLock.unlock();
    }
  }

  private int countCheckpointStates() {
    readLock.lock();
    try {
      return checkpoint_states.size();
    } finally {
      readLock.unlock();
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
    Map<UnsignedLong, VoteTracker> votes = new ConcurrentHashMap<>();
    Map<Checkpoint, BeaconState> checkpointStateCache = new HashMap<>();
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
      return getLatestFinalizedBlockAndState().getSlot();
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
    public Optional<BeaconState> getBlockStateIfAvailable(final Bytes32 blockRoot) {
      return Optional.ofNullable(block_states.get(blockRoot))
          .or(() -> Store.this.getBlockStateIfAvailable(blockRoot));
    }

    @Override
    public Optional<SignedBeaconBlock> getBlockIfAvailable(final Bytes32 blockRoot) {
      return Optional.ofNullable(blocks.get(blockRoot))
          .or(() -> Store.this.getBlockIfAvailable(blockRoot));
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
      final BeaconState cachedState = checkpointStateCache.get(checkpoint);
      if (cachedState != null) {
        return cachedState;
      }
      final Optional<BeaconState> checkpointFromStore =
          Store.this.getCheckpointStateIfAvailable(checkpoint);
      return checkpointFromStore
          .or(() -> regenerateCheckpointState(checkpoint, this::getBlockState))
          .orElse(null);
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
