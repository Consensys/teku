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
import java.util.concurrent.atomic.AtomicInteger;
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
import tech.pegasys.teku.core.StateGenerator;
import tech.pegasys.teku.core.StateGenerator.StateHandler;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BlockTree;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointAndBlock;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.protoarray.ProtoArrayForkChoiceStrategy;
import tech.pegasys.teku.protoarray.ProtoArrayForkChoiceStrategyUpdater;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.collections.ConcurrentLimitedMap;
import tech.pegasys.teku.util.collections.LimitStrategy;

class Store implements UpdatableStore {
  private static final Logger LOG = LogManager.getLogger();
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Counter stateRequestCachedCounter;
  private final Counter stateRequestRegenerateCounter;
  private final Counter stateRequestMissCounter;
  private final MetricsSystem metricsSystem;

  final ProtoArrayForkChoiceStrategy forkChoiceState;
  UnsignedLong time;
  UnsignedLong genesis_time;
  Checkpoint justified_checkpoint;
  Checkpoint finalized_checkpoint;
  Checkpoint best_justified_checkpoint;
  Map<Bytes32, SignedBeaconBlock> blocks;
  Map<Bytes32, BeaconState> block_states;
  Map<Checkpoint, BeaconState> checkpoint_states;
  SignedBlockAndState finalizedBlockAndState;

  final NavigableMap<UnsignedLong, Set<Bytes32>> rootsBySlotLookup = new TreeMap<>();

  Store(
      final MetricsSystem metricsSystem,
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final StateProvider blockStateProvider,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final BeaconState latestFinalizedBlockState,
      final Map<UnsignedLong, VoteTracker> votes,
      final int stateCacheSize) {
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
    this.time = time;
    this.genesis_time = genesis_time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.best_justified_checkpoint = best_justified_checkpoint;
    this.blocks = new ConcurrentHashMap<>(blocks);
    this.block_states =
        ConcurrentLimitedMap.create(stateCacheSize, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
    this.checkpoint_states = new ConcurrentHashMap<>(checkpoint_states);

    // Track latest finalized block
    final SignedBeaconBlock finalizedBlock = blocks.get(finalized_checkpoint.getRoot());
    this.finalizedBlockAndState =
        new SignedBlockAndState(finalizedBlock, latestFinalizedBlockState);
    block_states.put(finalizedBlock.getRoot(), latestFinalizedBlockState);

    forkChoiceState =
        ProtoArrayForkChoiceStrategy.create(votes, finalized_checkpoint, justified_checkpoint);

    // Process blocks
    LOG.info("Process {} block(s) to regenerate state", blocks.size());
    final AtomicInteger processedBlocks = new AtomicInteger(0);

    final ProtoArrayForkChoiceStrategyUpdater forkChoiceUpdater = forkChoiceState.updater();
    forkChoiceUpdater.onBlock(finalizedBlockAndState);
    blockStateProvider.provide(
        (blockRoot, state) -> {
          forkChoiceUpdater.onBlock(blocks.get(blockRoot), state);
          final int processed = processedBlocks.incrementAndGet();
          if (processed % 100 == 0) {
            LOG.info("Processed {} blocks", processed);
          }
          this.block_states.put(blockRoot, state);
        });
    forkChoiceUpdater.commit();
    LOG.info("Finished processing {} block(s)", blocks.size());

    // Setup slot to root mappings
    indexBlockRootsBySlot(rootsBySlotLookup, this.blocks.values());
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
        block_states::size);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        "memory_block_count",
        "Number of beacon blocks held in the in-memory store",
        blocks::size);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        "memory_checkpoint_state_count",
        "Number of checkpoint states held in the in-memory store",
        checkpoint_states::size);
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
    if (block == null) {
      return Optional.empty();
    }
    return getOrGenerateBlockState(blockRoot).map((state) -> new SignedBlockAndState(block, state));
  }

  @Override
  public Bytes32 getHead() {
    return forkChoiceState.getHead();
  }

  @Override
  public Optional<UnsignedLong> getBlockSlot(final Bytes32 blockRoot) {
    return forkChoiceState.getBlockSlot(blockRoot);
  }

  @Override
  public Optional<Bytes32> getBlockParent(final Bytes32 blockRoot) {
    return forkChoiceState.getBlockParent(blockRoot);
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

  private Optional<BeaconState> getOrGenerateBlockState(final Bytes32 blockRoot) {
    Optional<BeaconState> state = getBlockStateIfAvailable(blockRoot);
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
      final Optional<BeaconState> blockState = getBlockStateIfAvailable(block.getRoot());
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
      if (!blocks.containsKey(finalizedBlock.getRoot())) {
        // We must have finalized a new block while processing and moved past our target root
        return Optional.empty();
      }
      baseBlock = finalizedBlock;
    }

    // Regenerate state
    final BlockTree tree =
        BlockTree.builder().rootBlock(baseBlock.getBlock()).blocks(blocks.values()).build();
    final StateGenerator stateGenerator = StateGenerator.create(tree, baseBlock.getState());
    final BeaconState regeneratedState = stateGenerator.regenerateStateForBlock(blockRoot);
    stateRequestRegenerateCounter.inc();

    // Save regenerated state
    if (regeneratedState == null) {
      throw new IllegalStateException("Unable to generate state for block " + blockRoot);
    }
    putState(blockRoot, regeneratedState);

    return Optional.of(regeneratedState);
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

    // Fork choice
    final ProtoArrayForkChoiceStrategyUpdater forkChoiceUpdater;
    boolean headUpdated = false;

    // Other store updates
    Optional<UnsignedLong> time = Optional.empty();
    Optional<UnsignedLong> genesis_time = Optional.empty();
    Optional<Checkpoint> justified_checkpoint = Optional.empty();
    Optional<Checkpoint> finalized_checkpoint = Optional.empty();
    Optional<Checkpoint> best_justified_checkpoint = Optional.empty();
    Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BeaconState> block_states = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    private final StoreUpdateHandler updateHandler;

    Transaction(
        final StorageUpdateChannel storageUpdateChannel, final StoreUpdateHandler updateHandler) {
      this.storageUpdateChannel = storageUpdateChannel;
      this.updateHandler = updateHandler;
      forkChoiceUpdater = Store.this.forkChoiceState.updater();
    }

    @Override
    public void putCheckpointState(Checkpoint checkpoint, BeaconState state) {
      checkpoint_states.put(checkpoint, state);
    }

    @Override
    public void putBlockAndState(SignedBeaconBlock block, BeaconState state) {
      checkArgument(
          block.getStateRoot().equals(state.hash_tree_root()),
          "State must belong to the given block");
      blocks.put(block.getRoot(), block);
      block_states.put(block.getRoot(), state);
      forkChoiceUpdater.onBlock(block, state);
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
      forkChoiceUpdater.updateFinalizedBlock(finalized_checkpoint.getRoot());
    }

    @Override
    public void setBestJustifiedCheckpoint(Checkpoint best_justified_checkpoint) {
      this.best_justified_checkpoint = Optional.of(best_justified_checkpoint);
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

                updates.invokeUpdateHandler(Store.this, updateHandler);
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
    public Bytes32 getHead() {
      return Store.this.getHead();
    }

    @Override
    public Optional<UnsignedLong> getBlockSlot(final Bytes32 blockRoot) {
      return Store.this
          .getBlockSlot(blockRoot)
          .or(() -> Optional.ofNullable(blocks.get(blockRoot)).map(SignedBeaconBlock::getSlot));
    }

    @Override
    public Optional<Bytes32> getBlockParent(final Bytes32 blockRoot) {
      return Store.this
          .getBlockParent(blockRoot)
          .or(
              () ->
                  Optional.ofNullable(blocks.get(blockRoot))
                      .map(SignedBeaconBlock::getParent_root));
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

    @Override
    public void updateHead() {
      final Checkpoint finalized = getFinalizedCheckpoint();
      final Checkpoint justified = getJustifiedCheckpoint();
      final BeaconState justifiedState = getCheckpointState(justified);
      forkChoiceUpdater.updateHead(finalized, justified, justifiedState);
      this.headUpdated = true;
    }

    @Override
    public void processAttestation(final IndexedAttestation attestation) {
      forkChoiceUpdater.onAttestation(attestation);
    }

    Map<UnsignedLong, VoteTracker> getForkChoiceVotes() {
      return forkChoiceUpdater.getVotes();
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

  interface StateProvider {
    StateProvider NOOP = stateHandler -> {};

    void provide(StateHandler stateHandler);
  }
}
