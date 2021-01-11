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

import static tech.pegasys.teku.core.ForkChoiceUtil.get_ancestor;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.getCurrentDutyDependentRoot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.getPreviousDutyDependentRoot;

import com.google.common.eventbus.EventBus;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.core.ForkChoiceUtil;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.datastructures.genesis.GenesisData;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
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
  protected final EventBus eventBus;
  protected final FinalizedCheckpointChannel finalizedCheckpointChannel;
  protected final StorageUpdateChannel storageUpdateChannel;
  protected final ProtoArrayStorageChannel protoArrayStorageChannel;
  protected final AsyncRunner asyncRunner;
  protected final MetricsSystem metricsSystem;
  private final ChainHeadChannel chainHeadChannel;
  private final StoreConfig storeConfig;

  private final AtomicBoolean storeInitialized = new AtomicBoolean(false);
  private final SafeFuture<Void> storeInitializedFuture = new SafeFuture<>();
  private final SafeFuture<Void> bestBlockInitialized = new SafeFuture<>();
  private final Counter reorgCounter;
  private final boolean updateHeadForEmptySlots;

  private volatile UpdatableStore store;
  private volatile Optional<ChainHead> chainHead = Optional.empty();
  private volatile UInt64 genesisTime;

  RecentChainData(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final BlockProvider blockProvider,
      final StateAndBlockSummaryProvider stateProvider,
      final StorageUpdateChannel storageUpdateChannel,
      final ProtoArrayStorageChannel protoArrayStorageChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final EventBus eventBus) {
    this.asyncRunner = asyncRunner;
    this.metricsSystem = metricsSystem;
    this.storeConfig = storeConfig;
    this.blockProvider = blockProvider;
    this.stateProvider = stateProvider;
    this.chainHeadChannel = chainHeadChannel;
    this.eventBus = eventBus;
    this.storageUpdateChannel = storageUpdateChannel;
    this.protoArrayStorageChannel = protoArrayStorageChannel;
    this.finalizedCheckpointChannel = finalizedCheckpointChannel;
    this.updateHeadForEmptySlots = storeConfig.updateHeadForEmptySlots();
    reorgCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "reorgs_total",
            "Total occurrences of reorganizations of the chain");
  }

  public void subscribeStoreInitialized(Runnable runnable) {
    storeInitializedFuture.always(runnable);
  }

  public void subscribeBestBlockInitialized(Runnable runnable) {
    bestBlockInitialized.always(runnable);
  }

  public void initializeFromGenesis(final BeaconState genesisState) {
    final AnchorPoint genesis = AnchorPoint.fromGenesisState(genesisState);
    initializeFromAnchorPoint(genesis);
  }

  public void initializeFromAnchorPoint(final AnchorPoint anchorPoint) {
    final UpdatableStore store =
        StoreBuilder.forkChoiceStoreBuilder(
                asyncRunner, metricsSystem, blockProvider, stateProvider, anchorPoint)
            .storeConfig(storeConfig)
            .build();

    final boolean result = setStore(store);
    if (!result) {
      throw new IllegalStateException(
          "Failed to initialize from state: store has already been initialized");
    }

    eventBus.post(anchorPoint);

    // Set the head to the anchor point
    updateHead(anchorPoint.getRoot(), anchorPoint.getEpochStartSlot());
    storageUpdateChannel.onChainInitialized(anchorPoint);
  }

  public UInt64 getGenesisTime() {
    return genesisTime;
  }

  public Optional<GenesisData> getGenesisData() {
    if (isPreGenesis() || isPreForkChoice()) {
      return Optional.empty();
    }

    return getBestState()
        .map(state -> new GenesisData(state.getGenesis_time(), state.getGenesis_validators_root()));
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
    this.genesisTime = this.store.getGenesisTime();
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
                ForkChoiceUtil.getAncestors(
                    store.getForkChoiceStrategy(), head.getRoot(), startSlot, step, count))
        .orElseGet(TreeMap::new);
  }

  public NavigableMap<UInt64, Bytes32> getAncestorsOnFork(final UInt64 startSlot, Bytes32 root) {
    return ForkChoiceUtil.getAncestorsOnFork(store.getForkChoiceStrategy(), root, startSlot);
  }

  public Optional<ForkChoiceStrategy> getForkChoiceStrategy() {
    return Optional.ofNullable(store).map(UpdatableStore::getForkChoiceStrategy);
  }

  public StoreTransaction startStoreTransaction() {
    return store.startTransaction(storageUpdateChannel, this);
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
                    .map(head -> ChainHead.create(head, newForkChoiceSlot))
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

        final UInt64 commonAncestorSlot = previousChainHead.findCommonAncestor(newChainHead);

        reorgCounter.inc();
        optionalReorgContext =
            ReorgContext.of(
                previousChainHead.getRoot(), previousChainHead.getStateRoot(), commonAncestorSlot);
      } else {
        optionalReorgContext = ReorgContext.empty();
      }
      final boolean epochTransition =
          originalHead
              .map(
                  previousChainHead ->
                      compute_epoch_at_slot(previousChainHead.getForkChoiceSlot())
                          .isLessThan(compute_epoch_at_slot(newChainHead.getForkChoiceSlot())))
              .orElse(false);
      chainHeadChannel.chainHeadUpdated(
          newChainHead.getForkChoiceSlot(),
          newChainHead.getStateRoot(),
          newChainHead.getRoot(),
          epochTransition,
          getPreviousDutyDependentRoot(newChainHead.getState()),
          getCurrentDutyDependentRoot(newChainHead.getState()),
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
    return Optional.of(ForkChoiceUtil.get_current_slot(store));
  }

  public Optional<ForkInfo> getHeadForkInfo() {
    return getBestState().map(BeaconState::getForkInfo);
  }

  public Optional<Fork> getNextFork() {
    // There is no future fork defined at this point.
    return Optional.empty();
  }

  /**
   * Returns the fork info that applies based on the node's current slot, regardless of where the
   * sync progress is up to.
   *
   * <p>NOTE: Works on the basis that there is only one future forked scheduled as that's all we can
   * currently support.
   *
   * @return fork info based on the current time, not head block
   */
  public Optional<ForkInfo> getForkInfoAtCurrentTime() {
    return getHeadForkInfo()
        .map(
            headForkInfo ->
                getNextFork()
                    .filter(this::isForkActive)
                    .map(
                        nextFork -> new ForkInfo(nextFork, headForkInfo.getGenesisValidatorsRoot()))
                    .orElse(headForkInfo));
  }

  private boolean isForkActive(final Fork fork) {
    return getCurrentSlot()
        .map(currentSlot -> compute_epoch_at_slot(currentSlot).compareTo(fork.getEpoch()) >= 0)
        .orElse(false);
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
    return getForkChoiceStrategy().flatMap(strategy -> get_ancestor(strategy, headBlockRoot, slot));
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
}
