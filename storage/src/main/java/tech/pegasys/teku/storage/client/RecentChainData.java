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

package tech.pegasys.teku.storage.client;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.store.EmptyStoreResults;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreUpdateHandler;

/** This class is the ChainStorage client-side logic */
public abstract class RecentChainData implements StoreUpdateHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final BlockProvider blockProvider;
  private final StateAndBlockSummaryProvider stateProvider;
  protected final FinalizedCheckpointChannel finalizedCheckpointChannel;
  protected final StorageUpdateChannel storageUpdateChannel;
  protected final VoteUpdateChannel voteUpdateChannel;
  protected final AsyncRunner asyncRunner;
  protected final MetricsSystem metricsSystem;
  private final ChainHeadChannel chainHeadChannel;
  private final StoreConfig storeConfig;
  private final Spec spec;

  private final AtomicBoolean storeInitialized = new AtomicBoolean(false);
  private final SafeFuture<Void> storeInitializedFuture = new SafeFuture<>();
  private final SafeFuture<Void> bestBlockInitialized = new SafeFuture<>();
  private final Counter reorgCounter;
  private final boolean updateHeadForEmptySlots;

  private volatile UpdatableStore store;
  private volatile Optional<GenesisData> genesisData = Optional.empty();
  private final Map<Bytes4, SpecMilestone> forkDigestToMilestone = new ConcurrentHashMap<>();
  private final Map<SpecMilestone, Bytes4> milestoneToForkDigest = new ConcurrentHashMap<>();
  private volatile Optional<ChainHead> chainHead = Optional.empty();
  private volatile UInt64 genesisTime;

  RecentChainData(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final BlockProvider blockProvider,
      final StateAndBlockSummaryProvider stateProvider,
      final StorageUpdateChannel storageUpdateChannel,
      final VoteUpdateChannel voteUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final Spec spec) {
    this.asyncRunner = asyncRunner;
    this.metricsSystem = metricsSystem;
    this.storeConfig = storeConfig;
    this.blockProvider = blockProvider;
    this.stateProvider = stateProvider;
    this.voteUpdateChannel = voteUpdateChannel;
    this.chainHeadChannel = chainHeadChannel;
    this.storageUpdateChannel = storageUpdateChannel;
    this.finalizedCheckpointChannel = finalizedCheckpointChannel;
    this.updateHeadForEmptySlots = storeConfig.updateHeadForEmptySlots();
    reorgCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "reorgs_total",
            "Total occurrences of reorganizations of the chain");
    this.spec = spec;
  }

  public void subscribeStoreInitialized(Runnable runnable) {
    storeInitializedFuture.always(runnable);
  }

  public void subscribeBestBlockInitialized(Runnable runnable) {
    bestBlockInitialized.always(runnable);
  }

  public void initializeFromGenesis(final BeaconState genesisState, final UInt64 currentTime) {
    final AnchorPoint genesis = AnchorPoint.fromGenesisState(spec, genesisState);
    initializeFromAnchorPoint(genesis, currentTime);
  }

  public void initializeFromAnchorPoint(final AnchorPoint anchorPoint, final UInt64 currentTime) {
    final UpdatableStore store =
        StoreBuilder.forkChoiceStoreBuilder(
                asyncRunner,
                metricsSystem,
                spec,
                blockProvider,
                stateProvider,
                anchorPoint,
                currentTime)
            .storeConfig(storeConfig)
            .build();

    final boolean result = setStore(store);
    if (!result) {
      throw new IllegalStateException(
          "Failed to initialize from state: store has already been initialized");
    }

    // Set the head to the anchor point
    updateHead(anchorPoint.getRoot(), anchorPoint.getEpochStartSlot());
    storageUpdateChannel.onChainInitialized(anchorPoint);
  }

  public UInt64 getGenesisTime() {
    return genesisTime;
  }

  public Optional<GenesisData> getGenesisData() {
    return genesisData;
  }

  public Optional<SpecMilestone> getMilestoneByForkDigest(final Bytes4 forkDigest) {
    return Optional.ofNullable(forkDigestToMilestone.get(forkDigest));
  }

  public Optional<Bytes4> getForkDigestByMilestone(final SpecMilestone milestone) {
    return Optional.ofNullable(milestoneToForkDigest.get(milestone));
  }

  public boolean isPreGenesis() {
    return this.store == null;
  }

  /** @return true if the chain head has been set, false otherwise. */
  public boolean isPreForkChoice() {
    return chainHead.isEmpty();
  }

  boolean setStore(UpdatableStore store) {
    if (!storeInitialized.compareAndSet(false, true)) {
      return false;
    }
    this.store = store;
    this.store.startMetrics();

    // Set data that depends on the genesis state
    this.genesisTime = this.store.getGenesisTime();
    final BeaconState anchorState = store.getLatestFinalized().getState();
    final Bytes32 genesisValidatorsRoot = anchorState.getGenesis_validators_root();
    this.genesisData =
        Optional.of(new GenesisData(anchorState.getGenesis_time(), genesisValidatorsRoot));
    spec.getForkSchedule()
        .getActiveMilestones()
        .forEach(
            forkAndMilestone -> {
              final Fork fork = forkAndMilestone.getFork();
              final ForkInfo forkInfo = new ForkInfo(fork, genesisValidatorsRoot);
              final Bytes4 forkDigest = forkInfo.getForkDigest(spec);
              this.forkDigestToMilestone.put(forkDigest, forkAndMilestone.getSpecMilestone());
              this.milestoneToForkDigest.put(forkAndMilestone.getSpecMilestone(), forkDigest);
            });

    storeInitializedFuture.complete(null);
    return true;
  }

  public UpdatableStore getStore() {
    return store;
  }

  public NavigableMap<UInt64, Bytes32> getAncestorRootsOnHeadChain(
      final UInt64 startSlot, final UInt64 step, final UInt64 count) {
    return chainHead
        .map(
            head ->
                spec.getAncestors(
                    store.getForkChoiceStrategy(), head.getRoot(), startSlot, step, count))
        .orElseGet(TreeMap::new);
  }

  public NavigableMap<UInt64, Bytes32> getAncestorsOnFork(final UInt64 startSlot, Bytes32 root) {
    return spec.getAncestorsOnFork(store.getForkChoiceStrategy(), root, startSlot);
  }

  public Optional<ForkChoiceStrategy> getForkChoiceStrategy() {
    return Optional.ofNullable(store).map(UpdatableStore::getForkChoiceStrategy);
  }

  public StoreTransaction startStoreTransaction() {
    return store.startTransaction(storageUpdateChannel, this);
  }

  public VoteUpdater startVoteUpdate() {
    return store.startVoteUpdate(voteUpdateChannel);
  }

  // NETWORKING RELATED INFORMATION METHODS:

  /**
   * Set the block that is the current chain head according to fork-choice processing.
   *
   * @param root The new head block root
   * @param currentSlot The current slot - the slot at which the new head was selected
   */
  public void updateHead(Bytes32 root, UInt64 currentSlot) {
    final Optional<ChainHead> originalChainHead = chainHead;

    // Never let the fork choice slot go backwards.
    final UInt64 newForkChoiceSlot =
        currentSlot.max(originalChainHead.map(ChainHead::getForkChoiceSlot).orElse(UInt64.ZERO));
    store
        .retrieveStateAndBlockSummary(root)
        .thenApply(
            headBlockAndState ->
                headBlockAndState
                    .map(head -> ChainHead.create(head, newForkChoiceSlot, spec))
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                String.format(
                                    "Unable to update head block as of slot %s.  Block is unavailable: %s.",
                                    currentSlot, root))))
        .thenAccept(newChainHead -> updateChainHead(originalChainHead, newChainHead))
        .reportExceptions();
  }

  private void updateChainHead(
      final Optional<ChainHead> originalHead, final ChainHead newChainHead) {
    synchronized (this) {
      if (!chainHead.equals(originalHead)) {
        // The chain head has been updated while we were waiting for the newChainHead
        // Skip this update to avoid accidentally regressing the chain head
        LOG.info("Skipping head block update to avoid potential rollback of the chain head.");
        return;
      }
      if (originalHead.isPresent() && isNewHeadSameAsOld(originalHead.get(), newChainHead)) {
        LOG.trace("Skipping head update because new head is same as previous head");
        return;
      }
      this.chainHead = Optional.of(newChainHead);
      final Optional<ReorgContext> optionalReorgContext;
      if (originalHead
          .map(head -> hasReorgedFrom(head.getRoot(), getChainHeadSlot(head)))
          .orElse(false)) {

        final ChainHead previousChainHead = originalHead.get();

        final SlotAndBlockRoot commonAncestorSlotAndBlockRoot =
            previousChainHead.findCommonAncestor(newChainHead);

        reorgCounter.inc();
        optionalReorgContext =
            ReorgContext.of(
                previousChainHead.getRoot(),
                previousChainHead.getSlot(),
                previousChainHead.getStateRoot(),
                commonAncestorSlotAndBlockRoot.getSlot(),
                commonAncestorSlotAndBlockRoot.getBlockRoot());
      } else {
        optionalReorgContext = ReorgContext.empty();
      }
      final boolean epochTransition =
          originalHead
              .map(
                  previousChainHead ->
                      previousChainHead
                          .getForkChoiceEpoch()
                          .isLessThan(newChainHead.getForkChoiceEpoch()))
              .orElse(false);
      final BeaconStateUtil beaconStateUtil =
          spec.atSlot(newChainHead.getForkChoiceSlot()).getBeaconStateUtil();
      chainHeadChannel.chainHeadUpdated(
          newChainHead.getSlot(),
          newChainHead.getStateRoot(),
          newChainHead.getRoot(),
          epochTransition,
          beaconStateUtil.getPreviousDutyDependentRoot(newChainHead.getState()),
          beaconStateUtil.getCurrentDutyDependentRoot(newChainHead.getState()),
          optionalReorgContext);
    }
    bestBlockInitialized.complete(null);
  }

  private UInt64 getChainHeadSlot(final ChainHead head) {
    return updateHeadForEmptySlots ? head.getForkChoiceSlot() : head.getSlot();
  }

  private boolean isNewHeadSameAsOld(final ChainHead originalHead, final ChainHead newChainHead) {
    if (updateHeadForEmptySlots) {
      return originalHead.equals(newChainHead);
    } else {
      return originalHead.getRoot().equals(newChainHead.getRoot());
    }
  }

  private boolean hasReorgedFrom(
      final Bytes32 originalHeadRoot, final UInt64 originalChainTipSlot) {
    // Get the block root in effect at the old chain tip on the current chain. Depending on
    // updateHeadForEmptySlots that chain "tip" may be the fork choice slot (when true) or the
    // latest block (when false). If this is a different fork to the previous chain the root at
    // originalChainTipSlot will be different from originalHeadRoot. If it's an extension of the
    // same chain it will match.
    return getBlockRootBySlot(originalChainTipSlot)
        .map(rootAtOldBestSlot -> !rootAtOldBestSlot.equals(originalHeadRoot))
        .orElse(true);
  }

  /**
   * Return the current slot based on our Store's time.
   *
   * @return The current slot.
   */
  public Optional<UInt64> getCurrentSlot() {
    if (isPreGenesis()) {
      return Optional.empty();
    }
    return Optional.of(spec.getCurrentSlot(store));
  }

  public Optional<UInt64> getCurrentEpoch() {
    return getCurrentSlot().map(spec::computeEpochAtSlot);
  }

  /** @return The current spec version according to the recorded time */
  public SpecVersion getCurrentSpec() {
    return getCurrentSlot().map(spec::atSlot).orElseGet(spec::getGenesisSpec);
  }

  public Spec getSpec() {
    return spec;
  }

  /** @return The number of slots between our chainhead and the current slot by time */
  public Optional<UInt64> getChainHeadSlotsBehind() {
    return chainHead
        .map(StateAndBlockSummary::getSlot)
        .flatMap(headSlot -> getCurrentSlot().map(s -> s.minusMinZero(headSlot)));
  }

  public Optional<Fork> getNextFork(final Fork fork) {
    return spec.getForkSchedule().getNextFork(fork.getEpoch());
  }

  private Optional<Fork> getCurrentFork() {
    return getCurrentEpoch().map(spec.getForkSchedule()::getFork);
  }

  private Fork getFork(final UInt64 epoch) {
    return spec.getForkSchedule().getFork(epoch);
  }

  /**
   * Returns the fork info that applies based on the current slot as calculated from the current
   * time, regardless of where the sync progress is up to.
   *
   * @return fork info based on the current time, not head block
   */
  public Optional<ForkInfo> getCurrentForkInfo() {
    return genesisData
        .map(GenesisData::getGenesisValidatorsRoot)
        .flatMap(
            validatorsRoot -> getCurrentFork().map(fork -> new ForkInfo(fork, validatorsRoot)));
  }

  public Optional<ForkInfo> getForkInfo(final UInt64 epoch) {
    return genesisData
        .map(GenesisData::getGenesisValidatorsRoot)
        .map(validatorsRoot -> new ForkInfo(getFork(epoch), validatorsRoot));
  }

  /**
   * Retrieves the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public Optional<Bytes32> getBestBlockRoot() {
    return chainHead.map(StateAndBlockSummary::getRoot);
  }

  /** @return The head of the chain. */
  public Optional<StateAndBlockSummary> getChainHead() {
    return chainHead.map(a -> a);
  }

  /** @return The block at the head of the chain. */
  public Optional<SignedBeaconBlock> getHeadBlock() {
    return chainHead.flatMap(StateAndBlockSummary::getSignedBeaconBlock);
  }

  /**
   * Retrieves the state of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public Optional<BeaconState> getBestState() {
    return chainHead.map(StateAndBlockSummary::getState);
  }

  /**
   * Retrieves the slot of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public UInt64 getHeadSlot() {
    return chainHead.map(StateAndBlockSummary::getSlot).orElse(UInt64.ZERO);
  }

  public boolean containsBlock(final Bytes32 root) {
    return Optional.ofNullable(store).map(s -> s.containsBlock(root)).orElse(false);
  }

  public Optional<UInt64> getSlotForBlockRoot(final Bytes32 root) {
    return getForkChoiceStrategy().flatMap(forkChoice -> forkChoice.blockSlot(root));
  }

  public Optional<Bytes32> getExecutionBlockHashForBlockRoot(final Bytes32 root) {
    return getForkChoiceStrategy().flatMap(forkChoice -> forkChoice.executionBlockHash(root));
  }

  public SafeFuture<Optional<BeaconBlock>> retrieveBlockByRoot(final Bytes32 root) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_BLOCK_FUTURE;
    }
    return store.retrieveBlock(root);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlockByRoot(final Bytes32 root) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_SIGNED_BLOCK_FUTURE;
    }
    return store.retrieveSignedBlock(root);
  }

  public SafeFuture<Optional<BeaconState>> retrieveBlockState(final Bytes32 blockRoot) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_STATE_FUTURE;
    }
    return store.retrieveBlockState(blockRoot);
  }

  public SafeFuture<Optional<BeaconState>> retrieveStateAtSlot(
      final SlotAndBlockRoot slotAndBlockRoot) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_STATE_FUTURE;
    }
    return store.retrieveStateAtSlot(slotAndBlockRoot);
  }

  public SafeFuture<Optional<BeaconState>> retrieveStateInEffectAtSlot(final UInt64 slot) {
    Optional<Bytes32> rootAtSlot = getBlockRootBySlot(slot);
    if (rootAtSlot.isEmpty()) {
      return EmptyStoreResults.EMPTY_STATE_FUTURE;
    }
    return store.retrieveBlockState(rootAtSlot.get());
  }

  public Optional<Bytes32> getBlockRootBySlot(final UInt64 slot) {
    return chainHead.flatMap(head -> getBlockRootBySlot(slot, head.getRoot()));
  }

  public Optional<Bytes32> getBlockRootBySlot(final UInt64 slot, final Bytes32 headBlockRoot) {
    return getForkChoiceStrategy()
        .flatMap(strategy -> spec.getAncestor(strategy, headBlockRoot, slot));
  }

  public UInt64 getFinalizedEpoch() {
    return getFinalizedCheckpoint().map(Checkpoint::getEpoch).orElse(UInt64.ZERO);
  }

  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return store == null ? Optional.empty() : Optional.of(store.getFinalizedCheckpoint());
  }

  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return store == null ? Optional.empty() : Optional.of(store.getJustifiedCheckpoint());
  }

  public boolean isJustifiedCheckpointFullyValidated() {
    return store != null
        && store.getForkChoiceStrategy().isFullyValidated(store.getJustifiedCheckpoint().getRoot());
  }

  public UInt64 getLatestValidFinalizedSlot() {
    return store == null ? UInt64.ZERO : store.getLatestValidFinalizedSlot();
  }

  public boolean isOptimisticSyncPossible() {
    return store != null
        && !store.getLatestFinalized().getExecutionBlockHash().map(Bytes32::isZero).orElse(true);
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint finalizedCheckpoint) {
    finalizedCheckpointChannel.onNewFinalizedCheckpoint(finalizedCheckpoint);
  }

  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(final Checkpoint checkpoint) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_STATE_FUTURE;
    }

    return store.retrieveCheckpointState(checkpoint);
  }

  public Map<Bytes32, UInt64> getChainHeads() {
    return getForkChoiceStrategy()
        .map(ReadOnlyForkChoiceStrategy::getChainHeads)
        .orElse(Collections.emptyMap());
  }

  public Map<Bytes32, UInt64> getOptimisticChainHeads() {
    return getForkChoiceStrategy()
        .map(ReadOnlyForkChoiceStrategy::getOptimisticChainHeads)
        .orElse(Collections.emptyMap());
  }

  public Set<Bytes32> getAllBlockRootsAtSlot(final UInt64 slot) {
    return getForkChoiceStrategy()
        .map(forkChoiceStrategy -> forkChoiceStrategy.getBlockRootsAtSlot(slot))
        .orElse(Collections.emptySet());
  }
}
