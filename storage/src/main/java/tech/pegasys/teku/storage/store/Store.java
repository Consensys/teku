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

package tech.pegasys.teku.storage.store;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.dataproviders.generators.StateAtSlotTask.AsyncStateProvider.fromAnchor;
import static tech.pegasys.teku.dataproviders.lookup.BlockProvider.fromDynamicMap;
import static tech.pegasys.teku.dataproviders.lookup.BlockProvider.fromMap;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.dataproviders.generators.CachingTaskQueue;
import tech.pegasys.teku.dataproviders.generators.StateAtSlotTask;
import tech.pegasys.teku.dataproviders.generators.StateGenerationTask;
import tech.pegasys.teku.dataproviders.generators.StateRegenerationBaseSelector;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.BlockRootAndState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.protoarray.ProtoArray;

class Store implements UpdatableStore {
  private static final Logger LOG = LogManager.getLogger();
  public static final int VOTE_TRACKER_SPARE_CAPACITY = 1000;

  private final int hotStatePersistenceFrequencyInEpochs;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();

  private final MetricsSystem metricsSystem;
  private Optional<SettableGauge> blockCountGauge = Optional.empty();

  private final Spec spec;
  private final StateAndBlockSummaryProvider stateProvider;
  private final BlockProvider blockProvider;
  final ForkChoiceStrategy forkChoiceStrategy;
  final boolean asyncStorageEnabled;

  private final Optional<Checkpoint> initialCheckpoint;
  UInt64 timeMillis;
  UInt64 genesisTime;
  AnchorPoint finalizedAnchor;
  Checkpoint justifiedCheckpoint;
  Checkpoint bestJustifiedCheckpoint;
  Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload;
  Optional<Bytes32> proposerBoostRoot = Optional.empty();
  final CachingTaskQueue<Bytes32, StateAndBlockSummary> states;
  final Map<Bytes32, SignedBeaconBlock> blocks;
  final CachingTaskQueue<SlotAndBlockRoot, BeaconState> checkpointStates;
  VoteTracker[] votes;
  UInt64 highestVotedValidatorIndex;

  private Store(
      final MetricsSystem metricsSystem,
      final Spec spec,
      final int hotStatePersistenceFrequencyInEpochs,
      final BlockProvider blockProvider,
      final StateAndBlockSummaryProvider stateProvider,
      final CachingTaskQueue<Bytes32, StateAndBlockSummary> states,
      final Optional<Checkpoint> initialCheckpoint,
      final UInt64 time,
      final UInt64 genesisTime,
      final AnchorPoint finalizedAnchor,
      final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint bestJustifiedCheckpoint,
      final ForkChoiceStrategy forkChoiceStrategy,
      final Map<UInt64, VoteTracker> votes,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final CachingTaskQueue<SlotAndBlockRoot, BeaconState> checkpointStates,
      final boolean asyncStorageEnabled) {
    checkArgument(
        time.isGreaterThanOrEqualTo(genesisTime),
        "Time must be greater than or equal to genesisTime");
    this.forkChoiceStrategy = forkChoiceStrategy;
    this.stateProvider = stateProvider;
    this.asyncStorageEnabled = asyncStorageEnabled;
    LOG.trace(
        "Create store with hot state persistence configured to {}",
        hotStatePersistenceFrequencyInEpochs);

    // Set up metrics
    this.metricsSystem = metricsSystem;
    this.spec = spec;
    this.states = states;
    this.checkpointStates = checkpointStates;

    // Store instance variables
    this.initialCheckpoint = initialCheckpoint;
    this.hotStatePersistenceFrequencyInEpochs = hotStatePersistenceFrequencyInEpochs;
    this.timeMillis = secondsToMillis(time);
    this.genesisTime = genesisTime;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
    this.blocks = blocks;
    this.highestVotedValidatorIndex =
        votes.keySet().stream().max(Comparator.naturalOrder()).orElse(UInt64.ZERO);
    this.votes =
        new VoteTracker[this.highestVotedValidatorIndex.intValue() + VOTE_TRACKER_SPARE_CAPACITY];
    votes.forEach((key, value) -> this.votes[key.intValue()] = value);

    // Track latest finalized block
    this.finalizedAnchor = finalizedAnchor;
    states.cache(finalizedAnchor.getRoot(), finalizedAnchor);
    this.finalizedOptimisticTransitionPayload = finalizedOptimisticTransitionPayload;

    // Set up block provider to draw from in-memory blocks
    this.blockProvider =
        BlockProvider.combined(
            fromDynamicMap(
                () ->
                    this.getLatestFinalized()
                        .getSignedBeaconBlock()
                        .map((b) -> Map.of(b.getRoot(), b))
                        .orElseGet(Collections::emptyMap)),
            fromMap(this.blocks),
            blockProvider);

    if (asyncStorageEnabled) {
      LOG.info("Async storage updates enabled");
    }
  }

  public static UpdatableStore create(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final Spec spec,
      final BlockProvider blockProvider,
      final StateAndBlockSummaryProvider stateAndBlockProvider,
      final Optional<Checkpoint> initialCheckpoint,
      final UInt64 time,
      final UInt64 genesisTime,
      final AnchorPoint finalizedAnchor,
      final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint bestJustifiedCheckpoint,
      final Map<Bytes32, StoredBlockMetadata> blockInfoByRoot,
      final Map<UInt64, VoteTracker> votes,
      final StoreConfig config) {

    // Create limited collections for non-final data
    final Map<Bytes32, SignedBeaconBlock> blocks =
        LimitedMap.createSynchronized(config.getBlockCacheSize());
    final CachingTaskQueue<SlotAndBlockRoot, BeaconState> checkpointStateTaskQueue =
        CachingTaskQueue.create(
            asyncRunner,
            metricsSystem,
            "memory_checkpoint_states",
            config.getCheckpointStateCacheSize());
    final CachingTaskQueue<Bytes32, StateAndBlockSummary> stateTaskQueue =
        CachingTaskQueue.create(
            asyncRunner, metricsSystem, "memory_states", config.getStateCacheSize());
    final UInt64 currentEpoch = spec.computeEpochAtSlot(spec.getCurrentSlot(time, genesisTime));
    final ForkChoiceStrategy forkChoiceStrategy =
        ForkChoiceStrategy.initialize(
            spec,
            buildProtoArray(
                spec,
                blockInfoByRoot,
                initialCheckpoint,
                currentEpoch,
                justifiedCheckpoint,
                finalizedAnchor));

    return new Store(
        metricsSystem,
        spec,
        config.getHotStatePersistenceFrequencyInEpochs(),
        blockProvider,
        stateAndBlockProvider,
        stateTaskQueue,
        initialCheckpoint,
        time,
        genesisTime,
        finalizedAnchor,
        finalizedOptimisticTransitionPayload,
        justifiedCheckpoint,
        bestJustifiedCheckpoint,
        forkChoiceStrategy,
        votes,
        blocks,
        checkpointStateTaskQueue,
        config.isAsyncStorageEnabled());
  }

  private static ProtoArray buildProtoArray(
      final Spec spec,
      final Map<Bytes32, StoredBlockMetadata> blockInfoByRoot,
      final Optional<Checkpoint> initialCheckpoint,
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final AnchorPoint finalizedAnchor) {
    final List<StoredBlockMetadata> blocks = new ArrayList<>(blockInfoByRoot.values());
    blocks.sort(Comparator.comparing(StoredBlockMetadata::getBlockSlot));
    final ProtoArray protoArray =
        ProtoArray.builder()
            .currentEpoch(currentEpoch)
            .initialCheckpoint(initialCheckpoint)
            .justifiedCheckpoint(justifiedCheckpoint)
            .finalizedCheckpoint(finalizedAnchor.getCheckpoint())
            .progressiveBalancesMode(spec.getGenesisSpecConfig().getProgressiveBalancesMode())
            .build();
    for (StoredBlockMetadata block : blocks) {
      if (block.getCheckpointEpochs().isEmpty()) {
        throw new IllegalStateException(
            "Incompatible database version detected. The data in this database is too old to be read by Teku. A re-sync will be required.");
      }
      protoArray.onBlock(
          block.getBlockSlot(),
          block.getBlockRoot(),
          block.getParentRoot(),
          block.getStateRoot(),
          block.getCheckpointEpochs().get(),
          block.getExecutionBlockHash().orElse(Bytes32.ZERO),
          spec.isBlockProcessorOptimistic(block.getBlockSlot()));
    }
    return protoArray;
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
  public ForkChoiceStrategy getForkChoiceStrategy() {
    return forkChoiceStrategy;
  }

  @Override
  public StoreTransaction startTransaction(final StorageUpdateChannel storageUpdateChannel) {
    return startTransaction(storageUpdateChannel, StoreUpdateHandler.NOOP);
  }

  @Override
  public StoreTransaction startTransaction(
      final StorageUpdateChannel storageUpdateChannel, final StoreUpdateHandler updateHandler) {
    return new tech.pegasys.teku.storage.store.StoreTransaction(
        spec, this, lock, storageUpdateChannel, updateHandler);
  }

  @Override
  public VoteUpdater startVoteUpdate(final VoteUpdateChannel voteUpdateChannel) {
    return new StoreVoteUpdater(this, lock, voteUpdateChannel);
  }

  @Override
  public UInt64 getTimeMillis() {
    readLock.lock();
    try {
      return timeMillis;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public UInt64 getGenesisTime() {
    readLock.lock();
    try {
      return genesisTime;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Optional<Checkpoint> getInitialCheckpoint() {
    return initialCheckpoint;
  }

  @Override
  public Checkpoint getJustifiedCheckpoint() {
    readLock.lock();
    try {
      return justifiedCheckpoint;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    readLock.lock();
    try {
      return finalizedAnchor.getCheckpoint();
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public AnchorPoint getLatestFinalized() {
    readLock.lock();
    try {
      return finalizedAnchor;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Optional<SlotAndExecutionPayloadSummary> getFinalizedOptimisticTransitionPayload() {
    readLock.lock();
    try {
      return finalizedOptimisticTransitionPayload;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public UInt64 getLatestFinalizedBlockSlot() {
    readLock.lock();
    try {
      return finalizedAnchor.getBlockSlot();
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Checkpoint getBestJustifiedCheckpoint() {
    readLock.lock();
    try {
      return bestJustifiedCheckpoint;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Optional<Bytes32> getProposerBoostRoot() {
    readLock.lock();
    try {
      return proposerBoostRoot;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean containsBlock(Bytes32 blockRoot) {
    readLock.lock();
    try {
      return forkChoiceStrategy.contains(blockRoot);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Collection<Bytes32> getOrderedBlockRoots() {
    readLock.lock();
    try {
      final List<Bytes32> blockRoots = new ArrayList<>();
      forkChoiceStrategy.processAllInOrder((root, slot, parent) -> blockRoots.add(root));
      return blockRoots;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Optional<BeaconState> getBlockStateIfAvailable(final Bytes32 blockRoot) {
    return states.getIfAvailable(blockRoot).map(StateAndBlockSummary::getState);
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
  public SafeFuture<Optional<StateAndBlockSummary>> retrieveStateAndBlockSummary(
      final Bytes32 blockRoot) {
    return getAndCacheStateAndBlockSummary(blockRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveBlockState(Bytes32 blockRoot) {
    return getAndCacheBlockState(blockRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(Checkpoint checkpoint) {
    return checkpointStates.perform(
        new StateAtSlotTask(spec, checkpoint.toSlotAndBlockRoot(spec), this::retrieveBlockState));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveStateAtSlot(SlotAndBlockRoot slotAndBlockRoot) {
    return checkpointStates.perform(
        new StateAtSlotTask(spec, slotAndBlockRoot, this::retrieveBlockState));
  }

  @Override
  public SafeFuture<CheckpointState> retrieveFinalizedCheckpointAndState() {
    final AnchorPoint finalized;

    readLock.lock();
    try {
      finalized = this.finalizedAnchor;
    } finally {
      readLock.unlock();
    }

    return checkpointStates
        .perform(
            new StateAtSlotTask(
                spec, finalized.getCheckpoint().toSlotAndBlockRoot(spec), fromAnchor(finalized)))
        .thenApply(
            maybeState ->
                CheckpointState.create(
                    spec,
                    finalized.getCheckpoint(),
                    finalized.getBlockSummary(),
                    maybeState.orElseThrow()));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
      Checkpoint checkpoint, final BeaconState latestStateAtEpoch) {
    return checkpointStates.perform(
        new StateAtSlotTask(
            spec,
            checkpoint.toSlotAndBlockRoot(spec),
            blockRoot -> SafeFuture.completedFuture(Optional.of(latestStateAtEpoch))));
  }

  UInt64 getHighestVotedValidatorIndex() {
    readLock.lock();
    try {
      return highestVotedValidatorIndex;
    } finally {
      readLock.unlock();
    }
  }

  VoteTracker getVote(UInt64 validatorIndex) {
    readLock.lock();
    try {
      if (validatorIndex.intValue() >= votes.length) {
        return null;
      }
      return votes[validatorIndex.intValue()];
    } finally {
      readLock.unlock();
    }
  }

  private SafeFuture<Optional<BeaconState>> getAndCacheBlockState(final Bytes32 blockRoot) {
    return getOrRegenerateBlockAndState(blockRoot)
        .thenApply(res -> res.map(StateAndBlockSummary::getState));
  }

  private SafeFuture<Optional<SignedBlockAndState>> getAndCacheBlockAndState(
      final Bytes32 blockRoot) {
    return getOrRegenerateBlockAndState(blockRoot)
        .thenCompose(
            res -> {
              if (res.isEmpty()) {
                return SafeFuture.completedFuture(Optional.empty());
              }
              final Optional<SignedBeaconBlock> maybeBlock =
                  res.flatMap(StateAndBlockSummary::getSignedBeaconBlock);
              return maybeBlock
                  .map(b -> SafeFuture.completedFuture(Optional.of(b)))
                  .orElseGet(() -> blockProvider.getBlock(blockRoot))
                  .thenPeek(block -> block.ifPresent(this::putBlock))
                  .thenApply(
                      block -> block.map(b -> new SignedBlockAndState(b, res.get().getState())));
            });
  }

  private SafeFuture<Optional<StateAndBlockSummary>> getAndCacheStateAndBlockSummary(
      final Bytes32 blockRoot) {
    return getOrRegenerateBlockAndState(blockRoot);
  }

  private SafeFuture<Optional<StateAndBlockSummary>> getOrRegenerateBlockAndState(
      final Bytes32 blockRoot) {
    // Avoid generating the hash tree to rebuild if the state is already available.
    final Optional<StateAndBlockSummary> cachedResult = states.getIfAvailable(blockRoot);
    if (cachedResult.isPresent()) {
      return SafeFuture.completedFuture(cachedResult);
    }
    return createStateGenerationTask(blockRoot)
        .thenCompose(
            maybeTask ->
                maybeTask.isPresent()
                    ? states.perform(maybeTask.get())
                    : EmptyStoreResults.EMPTY_STATE_AND_BLOCK_SUMMARY_FUTURE);
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
      treeBuilder.rootHash(finalizedAnchor.getRoot());
    } finally {
      readLock.unlock();
    }

    return SafeFuture.completedFuture(
        Optional.of(
            new StateGenerationTask(
                spec,
                blockRoot,
                treeBuilder.build(),
                blockProvider,
                new StateRegenerationBaseSelector(
                    spec,
                    Optional.ofNullable(latestEpochBoundary.get()),
                    () -> getClosestAvailableBlockRootAndState(blockRoot),
                    stateProvider,
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
          (root, slot, parent, executionHash) -> {
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
      final AnchorPoint finalized = getLatestFinalized();
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
        && isSlotAtNthEpochBoundary(blockSlot, parentRoot, hotStatePersistenceFrequencyInEpochs);
  }

  boolean shouldPersistState(final UInt64 blockSlot, final Optional<UInt64> parentSlot) {
    return hotStatePersistenceFrequencyInEpochs > 0
        && parentSlot
            .map(
                slot ->
                    spec.getGenesisSpec()
                        .miscHelpers()
                        .isSlotAtNthEpochBoundary(
                            blockSlot, slot, hotStatePersistenceFrequencyInEpochs))
            .orElse(false);
  }

  private boolean isSlotAtNthEpochBoundary(
      final UInt64 blockSlot, final Bytes32 parentRoot, final int n) {
    return forkChoiceStrategy
        .blockSlot(parentRoot)
        .map(
            parentSlot ->
                spec.getGenesisSpec()
                    .miscHelpers()
                    .isSlotAtNthEpochBoundary(blockSlot, parentSlot, n))
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
