/*
 * Copyright Consensys Software Inc., 2022
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
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.dataproviders.generators.CachingTaskQueue;
import tech.pegasys.teku.dataproviders.generators.StateAtSlotTask;
import tech.pegasys.teku.dataproviders.generators.StateGenerationTask;
import tech.pegasys.teku.dataproviders.generators.StateRegenerationBaseSelector;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.EarliestBlobSidecarSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.BlockRootAndState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.protoarray.ProtoArray;
import tech.pegasys.teku.storage.protoarray.ProtoNode;

class Store extends CacheableStore {
  private static final Logger LOG = LogManager.getLogger();
  public static final int VOTE_TRACKER_SPARE_CAPACITY = 1000;

  private final int hotStatePersistenceFrequencyInEpochs;

  private final ReadWriteLock votesLock = new ReentrantReadWriteLock();
  private final Lock readVotesLock = votesLock.readLock();
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();

  private final MetricsSystem metricsSystem;
  private Optional<SettableGauge> blockCountGauge = Optional.empty();
  private Optional<SettableGauge> epochStatesCountGauge = Optional.empty();
  private Optional<SettableGauge> blobSidecarsBlocksCountGauge = Optional.empty();

  private final Optional<Map<Bytes32, StateAndBlockSummary>> maybeEpochStates;

  private final Spec spec;
  private final StateAndBlockSummaryProvider stateProvider;
  private final BlockProvider blockProvider;
  private final EarliestBlobSidecarSlotProvider earliestBlobSidecarSlotProvider;
  private final ForkChoiceStrategy forkChoiceStrategy;

  private final Optional<Checkpoint> initialCheckpoint;
  private final CachingTaskQueue<Bytes32, StateAndBlockSummary> states;
  private final Map<Bytes32, SignedBeaconBlock> blocks;
  private final CachingTaskQueue<SlotAndBlockRoot, BeaconState> checkpointStates;
  private final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars;
  private UInt64 timeMillis;
  private UInt64 genesisTime;
  private AnchorPoint finalizedAnchor;
  private Checkpoint justifiedCheckpoint;
  private Checkpoint bestJustifiedCheckpoint;
  private Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload;
  private Optional<Bytes32> proposerBoostRoot = Optional.empty();
  private VoteTracker[] votes;
  private UInt64 highestVotedValidatorIndex;

  private UInt64 reorgThreshold = UInt64.ZERO;
  private UInt64 parentThreshold = UInt64.ZERO;

  private Store(
      final MetricsSystem metricsSystem,
      final Spec spec,
      final int hotStatePersistenceFrequencyInEpochs,
      final BlockProvider blockProvider,
      final StateAndBlockSummaryProvider stateProvider,
      final EarliestBlobSidecarSlotProvider earliestBlobSidecarSlotProvider,
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
      final Optional<Map<Bytes32, StateAndBlockSummary>> maybeEpochStates,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars) {
    checkArgument(
        time.isGreaterThanOrEqualTo(genesisTime),
        "Time must be greater than or equal to genesisTime");
    this.forkChoiceStrategy = forkChoiceStrategy;
    this.stateProvider = stateProvider;
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
    this.blobSidecars = blobSidecars;
    this.highestVotedValidatorIndex =
        votes.keySet().stream().max(Comparator.naturalOrder()).orElse(UInt64.ZERO);
    this.votes =
        new VoteTracker[this.highestVotedValidatorIndex.intValue() + VOTE_TRACKER_SPARE_CAPACITY];
    votes.forEach((key, value) -> this.votes[key.intValue()] = value);

    // Track latest finalized block
    this.finalizedAnchor = finalizedAnchor;
    this.maybeEpochStates = maybeEpochStates;
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
            createBlockProviderFromMapWhileLocked(this.blocks),
            blockProvider);

    this.earliestBlobSidecarSlotProvider = earliestBlobSidecarSlotProvider;
  }

  private BlockProvider createBlockProviderFromMapWhileLocked(
      final Map<Bytes32, SignedBeaconBlock> blockMap) {
    return (roots) -> {
      readLock.lock();
      try {
        return SafeFuture.completedFuture(
            roots.stream()
                .filter(blockMap::containsKey)
                .collect(Collectors.toMap(Function.identity(), blockMap::get)));
      } finally {
        readLock.unlock();
      }
    };
  }

  static UpdatableStore create(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final Spec spec,
      final BlockProvider blockProvider,
      final StateAndBlockSummaryProvider stateAndBlockProvider,
      final EarliestBlobSidecarSlotProvider earliestBlobSidecarSlotProvider,
      final Optional<Checkpoint> initialCheckpoint,
      final UInt64 time,
      final UInt64 genesisTime,
      final AnchorPoint finalizedAnchor,
      final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint bestJustifiedCheckpoint,
      final Map<Bytes32, StoredBlockMetadata> blockInfoByRoot,
      final Map<UInt64, VoteTracker> votes,
      final StoreConfig config,
      final ForkChoiceStrategy forkChoiceStrategy) {
    final Map<Bytes32, SignedBeaconBlock> blocks =
        LimitedMap.createSynchronizedNatural(config.getBlockCacheSize());
    final CachingTaskQueue<SlotAndBlockRoot, BeaconState> checkpointStateTaskQueue =
        CachingTaskQueue.create(
            asyncRunner,
            metricsSystem,
            "memory_checkpoint_states",
            config.getCheckpointStateCacheSize());
    final CachingTaskQueue<Bytes32, StateAndBlockSummary> stateTaskQueue =
        CachingTaskQueue.create(
            asyncRunner, metricsSystem, "memory_states", config.getStateCacheSize());
    final Optional<Map<Bytes32, StateAndBlockSummary>> maybeEpochStates =
        config.getEpochStateCacheSize() > 0
            ? Optional.of(LimitedMap.createSynchronizedLRU(config.getEpochStateCacheSize()))
            : Optional.empty();
    final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars =
        LimitedMap.createSynchronizedNatural(config.getBlockCacheSize());

    return new Store(
        metricsSystem,
        spec,
        config.getHotStatePersistenceFrequencyInEpochs(),
        blockProvider,
        stateAndBlockProvider,
        earliestBlobSidecarSlotProvider,
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
        maybeEpochStates,
        blobSidecars);
  }

  public static UpdatableStore create(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final Spec spec,
      final BlockProvider blockProvider,
      final StateAndBlockSummaryProvider stateAndBlockProvider,
      final EarliestBlobSidecarSlotProvider earliestBlobSidecarSlotProvider,
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
    return create(
        asyncRunner,
        metricsSystem,
        spec,
        blockProvider,
        stateAndBlockProvider,
        earliestBlobSidecarSlotProvider,
        initialCheckpoint,
        time,
        genesisTime,
        finalizedAnchor,
        finalizedOptimisticTransitionPayload,
        justifiedCheckpoint,
        bestJustifiedCheckpoint,
        blockInfoByRoot,
        votes,
        config,
        forkChoiceStrategy);
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
            .spec(spec)
            .currentEpoch(currentEpoch)
            .initialCheckpoint(initialCheckpoint)
            .justifiedCheckpoint(justifiedCheckpoint)
            .finalizedCheckpoint(finalizedAnchor.getCheckpoint())
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
          block.getExecutionBlockNumber().orElse(ProtoNode.NO_EXECUTION_BLOCK_NUMBER),
          block.getExecutionBlockHash().orElse(ProtoNode.NO_EXECUTION_BLOCK_HASH),
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
    votesLock.writeLock().lock();
    lock.writeLock().lock();
    try {
      blockCountGauge =
          Optional.of(
              SettableGauge.create(
                  metricsSystem,
                  TekuMetricCategory.STORAGE,
                  "memory_block_count",
                  "Number of beacon blocks held in the in-memory store"));
      blobSidecarsBlocksCountGauge =
          Optional.of(
              SettableGauge.create(
                  metricsSystem,
                  TekuMetricCategory.STORAGE,
                  "memory_blocks_with_cached_blobs_count",
                  "Number of beacon blocks with complete blobs held in the in-memory store"));

      if (maybeEpochStates.isPresent()) {
        epochStatesCountGauge =
            Optional.of(
                SettableGauge.create(
                    metricsSystem,
                    TekuMetricCategory.STORAGE,
                    "memory_epoch_states_cache_size",
                    "Number of Epoch aligned states held in the in-memory store"));
      }
      states.startMetrics();
      checkpointStates.startMetrics();
    } finally {
      votesLock.writeLock().unlock();
      lock.writeLock().unlock();
    }
  }

  @Override
  public ForkChoiceStrategy getForkChoiceStrategy() {
    return forkChoiceStrategy;
  }

  @Override
  @VisibleForTesting
  public void clearCaches() {
    states.clear();
    checkpointStates.clear();
    blocks.clear();
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
    return new StoreVoteUpdater(this, votesLock, voteUpdateChannel);
  }

  @Override
  public UInt64 getTimeInMillis() {
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
  public boolean containsBlock(final Bytes32 blockRoot) {
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
  public boolean isHeadWeak(final Bytes32 root) {
    final Optional<ProtoNodeData> maybeBlockData = getBlockDataFromForkChoiceStrategy(root);
    return maybeBlockData
        .map(
            blockData -> {
              final UInt64 headWeight = blockData.getWeight();

              final boolean result = headWeight.isLessThan(reorgThreshold);

              LOG.trace(
                  "isHeadWeak {}: headWeight: {}, reorgThreshold: {}, result: {}",
                  root,
                  headWeight,
                  reorgThreshold,
                  result);
              return result;
            })
        .orElse(false);
  }

  @Override
  public boolean isParentStrong(final Bytes32 parentRoot) {
    final Optional<ProtoNodeData> maybeBlockData = getBlockDataFromForkChoiceStrategy(parentRoot);
    return maybeBlockData
        .map(
            blockData -> {
              final UInt64 parentWeight = blockData.getWeight();
              final boolean result = parentWeight.isGreaterThan(parentThreshold);

              LOG.debug(
                  "isParentStrong {}: parentWeight: {}, parentThreshold: {}, result: {}",
                  parentRoot,
                  parentWeight,
                  parentThreshold,
                  result);
              return result;
            })
        .orElse(true);
  }

  @Override
  public void computeBalanceThresholds(final BeaconState justifiedState) {
    final SpecVersion specVersion = spec.atSlot(justifiedState.getSlot());
    final BeaconStateAccessors beaconStateAccessors = specVersion.beaconStateAccessors();
    reorgThreshold =
        beaconStateAccessors.calculateCommitteeFraction(
            justifiedState, specVersion.getConfig().getReorgHeadWeightThreshold());
    parentThreshold =
        beaconStateAccessors.calculateCommitteeFraction(
            justifiedState, specVersion.getConfig().getReorgParentWeightThreshold());
  }

  @Override
  public Optional<Boolean> isFfgCompetitive(final Bytes32 headRoot, final Bytes32 parentRoot) {
    final Optional<ProtoNodeData> maybeHeadData = getBlockDataFromForkChoiceStrategy(headRoot);
    final Optional<ProtoNodeData> maybeParentData = getBlockDataFromForkChoiceStrategy(parentRoot);
    if (maybeParentData.isEmpty() || maybeHeadData.isEmpty()) {
      return Optional.empty();
    }
    final Checkpoint headUnrealizedJustifiedCheckpoint =
        maybeHeadData.get().getCheckpoints().getUnrealizedJustifiedCheckpoint();
    final Checkpoint parentUnrealizedJustifiedCheckpoint =
        maybeParentData.get().getCheckpoints().getUnrealizedJustifiedCheckpoint();
    LOG.trace(
        "head {}, compared to parent {}",
        headUnrealizedJustifiedCheckpoint,
        parentUnrealizedJustifiedCheckpoint);
    return Optional.of(
        headUnrealizedJustifiedCheckpoint.equals(parentUnrealizedJustifiedCheckpoint));
  }

  private Optional<ProtoNodeData> getBlockDataFromForkChoiceStrategy(final Bytes32 root) {
    readLock.lock();
    try {
      return forkChoiceStrategy.getBlockData(root);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(final Bytes32 blockRoot) {
    if (!containsBlock(blockRoot)) {
      return EmptyStoreResults.EMPTY_SIGNED_BLOCK_FUTURE;
    }
    return blockProvider.getBlock(blockRoot);
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(final Bytes32 blockRoot) {
    return getAndCacheBlockAndState(blockRoot);
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> retrieveStateAndBlockSummary(
      final Bytes32 blockRoot) {
    return getOrRegenerateBlockAndState(blockRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveBlockState(final Bytes32 blockRoot) {
    return getAndCacheBlockAndState(blockRoot)
        .thenApply(
            maybeStateAndBlockSummary ->
                maybeStateAndBlockSummary.map(StateAndBlockSummary::getState));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(final Checkpoint checkpoint) {
    return checkpointStates.perform(
        new StateAtSlotTask(spec, checkpoint.toSlotAndBlockRoot(spec), this::retrieveBlockState));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveStateAtSlot(
      final SlotAndBlockRoot slotAndBlockRoot) {
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
      final Checkpoint checkpoint, final BeaconState latestStateAtEpoch) {
    return checkpointStates.perform(
        new StateAtSlotTask(
            spec,
            checkpoint.toSlotAndBlockRoot(spec),
            blockRoot -> SafeFuture.completedFuture(Optional.of(latestStateAtEpoch))));
  }

  @Override
  public Optional<List<BlobSidecar>> getBlobSidecarsIfAvailable(
      final SlotAndBlockRoot slotAndBlockRoot) {
    readLock.lock();
    try {
      return Optional.ofNullable(blobSidecars.get(slotAndBlockRoot));
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public SafeFuture<Optional<UInt64>> retrieveEarliestBlobSidecarSlot() {
    return earliestBlobSidecarSlotProvider.getEarliestBlobSidecarSlot();
  }

  /** Non-synchronized, no lock, unsafe if Store is not locked externally */
  @Override
  void cacheBlocks(final Collection<BlockAndCheckpoints> blockAndCheckpoints) {
    blockAndCheckpoints.stream()
        .sorted(Comparator.comparing(BlockAndCheckpoints::getSlot))
        .map(BlockAndCheckpoints::getBlock)
        .forEach(block -> blocks.put(block.getRoot(), block));
    blockCountGauge.ifPresent(gauge -> gauge.set(blocks.size()));
  }

  /** Non-synchronized, no lock, unsafe if Store is not locked externally */
  @Override
  void cacheTimeMillis(final UInt64 timeMillis) {
    if (timeMillis.isGreaterThanOrEqualTo(this.timeMillis)) {
      this.timeMillis = timeMillis;
    }
  }

  /** Non-synchronized, no lock, unsafe if Store is not locked externally */
  @Override
  void cacheGenesisTime(final UInt64 genesisTime) {
    this.genesisTime = genesisTime;
  }

  /** Non-synchronized, no lock, unsafe if Store is not locked externally */
  @Override
  void cacheProposerBoostRoot(final Optional<Bytes32> proposerBoostRoot) {
    this.proposerBoostRoot = proposerBoostRoot;
  }

  /** Non-synchronized, no lock, unsafe if Store is not locked externally */
  @Override
  void cacheStates(final Map<Bytes32, StateAndBlockSummary> stateAndBlockSummaries) {
    states.cacheAll(stateAndBlockSummaries);
  }

  /** Non-synchronized, no lock, unsafe if Store is not locked externally */
  @Override
  void cacheBlobSidecars(final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsMap) {
    blobSidecarsMap.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> blobSidecars.put(entry.getKey(), entry.getValue()));
    blobSidecarsBlocksCountGauge.ifPresent(gauge -> gauge.set(blobSidecars.size()));
  }

  /** Non-synchronized, no lock, unsafe if Store is not locked externally */
  @Override
  void cacheFinalizedOptimisticTransitionPayload(
      final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload) {
    this.finalizedOptimisticTransitionPayload = finalizedOptimisticTransitionPayload;
  }

  /** Non-synchronized, no lock, unsafe if Store is not locked externally */
  @Override
  void cleanupCheckpointStates(final Predicate<SlotAndBlockRoot> removalCondition) {
    checkpointStates.removeIf(removalCondition);
  }

  /** Non-synchronized, no lock, unsafe if Store is not locked externally */
  @Override
  void setHighestVotedValidatorIndex(final UInt64 highestVotedValidatorIndex) {
    this.highestVotedValidatorIndex = highestVotedValidatorIndex;

    // Expand votes array if needed
    if (highestVotedValidatorIndex.isGreaterThanOrEqualTo(votes.length)) {
      this.votes =
          Arrays.copyOf(
              votes, highestVotedValidatorIndex.plus(VOTE_TRACKER_SPARE_CAPACITY).intValue());
    }
  }

  /** Non-synchronized, no lock, unsafe if Store is not locked externally */
  @Override
  void setVote(final int index, final VoteTracker voteTracker) {
    votes[index] = voteTracker;
  }

  UInt64 getHighestVotedValidatorIndex() {
    readVotesLock.lock();
    try {
      return highestVotedValidatorIndex;
    } finally {
      readVotesLock.unlock();
    }
  }

  VoteTracker getVote(final UInt64 validatorIndex) {
    readVotesLock.lock();
    try {
      if (validatorIndex.intValue() >= votes.length) {
        return null;
      }
      return votes[validatorIndex.intValue()];
    } finally {
      readVotesLock.unlock();
    }
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
                  .map(
                      signedBeaconBlock ->
                          SafeFuture.completedFuture(Optional.of(signedBeaconBlock)))
                  .orElseGet(() -> blockProvider.getBlock(blockRoot))
                  .thenApply(
                      block -> block.map(b -> new SignedBlockAndState(b, res.get().getState())));
            });
  }

  private SafeFuture<Optional<StateAndBlockSummary>> getOrRegenerateBlockAndState(
      final Bytes32 blockRoot) {
    // Avoid generating the hash tree to rebuild if the state is already available.
    final Optional<StateAndBlockSummary> cachedResult = states.getIfAvailable(blockRoot);
    if (cachedResult.isPresent()) {
      return SafeFuture.completedFuture(cachedResult).thenPeek(this::cacheIfEpochState);
    }

    // is it an epoch boundary?
    final Optional<StateAndBlockSummary> maybeEpochState =
        maybeEpochStates.flatMap(epochStates -> Optional.ofNullable(epochStates.get(blockRoot)));
    if (maybeEpochState.isPresent()) {
      LOG.trace("epochCache GET {}", () -> maybeEpochState.get().getSlot());
      return SafeFuture.completedFuture(maybeEpochState);
    }

    // if finalized is gone from cache we can still reconstruct that without regenerating
    if (finalizedAnchor.getRoot().equals(blockRoot)) {
      LOG.trace("epochCache GET finalizedAnchor {}", finalizedAnchor::getSlot);
      return SafeFuture.completedFuture(
          Optional.of(
              StateAndBlockSummary.create(
                  finalizedAnchor.getBlockSummary(), finalizedAnchor.getState())));
    }

    maybeEpochStates.ifPresent(
        epochStates ->
            LOG.trace(
                "epochCache states in cache: {}",
                () ->
                    epochStates.values().stream()
                        .map(StateAndBlockSummary::getSlot)
                        .map(UInt64::toString)
                        .collect(Collectors.joining(", "))));
    return createStateGenerationTask(blockRoot)
        .thenCompose(
            maybeTask ->
                maybeTask.isPresent()
                    ? states.perform(maybeTask.get()).thenPeek(this::cacheIfEpochState)
                    : EmptyStoreResults.EMPTY_STATE_AND_BLOCK_SUMMARY_FUTURE);
  }

  private void cacheIfEpochState(final Optional<StateAndBlockSummary> maybeStateAndBlockSummary) {
    if (maybeStateAndBlockSummary.isPresent() && maybeEpochStates.isPresent()) {
      final StateAndBlockSummary stateAndBlockSummary = maybeStateAndBlockSummary.get();
      final UInt64 slot = stateAndBlockSummary.getSlot();
      if (!isSlotAtNthEpochBoundary(slot, stateAndBlockSummary.getParentRoot(), 1)) {
        return;
      }

      final Map<Bytes32, StateAndBlockSummary> epochStates = maybeEpochStates.get();
      if (!slot.mod(spec.getSlotsPerEpoch(slot)).isZero()) {
        // pre-epoch transition state
        // This will be referenced during epoch transition if the first slot of the epoch is empty
        final Optional<StateAndBlockSummary> maybeParentStateAndBlockSummary =
            states.getIfAvailable(stateAndBlockSummary.getParentRoot());
        maybeParentStateAndBlockSummary.ifPresent(
            parentStateAndBlockSummary -> {
              if (epochStates.put(parentStateAndBlockSummary.getRoot(), parentStateAndBlockSummary)
                  == null) {
                LOG.trace("epochCache ADD.PRE {}", parentStateAndBlockSummary::getSlot);
              }
            });
      } else {
        // post epoch transition state
        if (epochStates.put(stateAndBlockSummary.getRoot(), stateAndBlockSummary) == null) {
          LOG.trace("epochCache ADD {}", stateAndBlockSummary::getSlot);
        }
      }

      epochStatesCountGauge.ifPresent(counter -> counter.set(maybeEpochStates.get().size()));
    }
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

  @VisibleForTesting
  Optional<Map<Bytes32, StateAndBlockSummary>> getEpochStates() {
    return maybeEpochStates;
  }

  void removeStateAndBlock(final Bytes32 root) {
    blocks.remove(root);
    states.remove(root);
    maybeEpochStates.ifPresent(
        epochStates -> {
          if (!finalizedAnchor.getRoot().equals(root)) {
            final StateAndBlockSummary stateAndBlockSummary = epochStates.remove(root);
            if (stateAndBlockSummary != null) {
              LOG.trace("epochCache REM {}", stateAndBlockSummary::getSlot);
            }
          }
        });
  }

  void updateFinalizedAnchor(final AnchorPoint latestFinalized) {
    pruneOldFinalizedStateFromEpochCache(this.finalizedAnchor);
    finalizedAnchor = latestFinalized;
    cacheFinalizedAnchorPoint(latestFinalized);
  }

  private void cacheFinalizedAnchorPoint(final AnchorPoint latestFinalized) {
    maybeEpochStates.ifPresent(
        epochStates -> {
          final BeaconState state = latestFinalized.getState();
          StateAndBlockSummary stateAndBlockSummary =
              StateAndBlockSummary.create(latestFinalized.getBlockSummary(), state);
          final Bytes32 root = latestFinalized.getRoot();
          if (epochStates.put(root, stateAndBlockSummary) == null) {
            LOG.trace("epochCache ADD FINALIZED {}", stateAndBlockSummary::getSlot);
          }
        });
  }

  private void pruneOldFinalizedStateFromEpochCache(final AnchorPoint anchorPoint) {
    // ensure the old finalized state is not stored in cache, we no longer require it.
    maybeEpochStates.ifPresent(
        epochStates -> {
          final StateAndBlockSummary stateAndBlockSummary =
              epochStates.remove(anchorPoint.getRoot());
          if (stateAndBlockSummary != null) {
            LOG.trace("epochCache REM FINALIZED {}", stateAndBlockSummary::getSlot);
          }
        });
  }

  void updateJustifiedCheckpoint(final Checkpoint checkpoint) {
    this.justifiedCheckpoint = checkpoint;
    maybeEpochStates.ifPresent(
        epochStates -> {
          final SlotAndBlockRoot slotAndBlockRoot = checkpoint.toSlotAndBlockRoot(spec);
          if (epochStates.get(slotAndBlockRoot.getBlockRoot()) != null) {
            LOG.trace("epochCache JUSTIFIED {}", slotAndBlockRoot::getSlot);
          } else {
            LOG.trace("epochCache MISS JUSTIFIED {}", slotAndBlockRoot::getSlot);
          }
        });
  }

  void updateBestJustifiedCheckpoint(final Checkpoint checkpoint) {
    this.bestJustifiedCheckpoint = checkpoint;
  }
}
