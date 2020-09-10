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
import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatBlock;

import com.google.common.eventbus.EventBus;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.core.ForkChoiceUtil;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockProvider;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.protoarray.ProtoArrayForkChoiceStrategy;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.AnchorPoint;
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
  private final StateAndBlockProvider stateProvider;
  protected final EventBus eventBus;
  protected final FinalizedCheckpointChannel finalizedCheckpointChannel;
  protected final StorageUpdateChannel storageUpdateChannel;
  protected final ProtoArrayStorageChannel protoArrayStorageChannel;
  protected final AsyncRunner asyncRunner;
  protected final MetricsSystem metricsSystem;
  private final ReorgEventChannel reorgEventChannel;
  private final StoreConfig storeConfig;

  private final AtomicBoolean storeInitialized = new AtomicBoolean(false);
  private final SafeFuture<Void> storeInitializedFuture = new SafeFuture<>();
  private final SafeFuture<Void> bestBlockInitialized = new SafeFuture<>();
  private final Counter reorgCounter;

  private volatile UpdatableStore store;
  private volatile Optional<ProtoArrayForkChoiceStrategy> forkChoiceStrategy;
  private volatile Optional<ChainHead> chainHead = Optional.empty();
  private volatile UInt64 genesisTime;

  RecentChainData(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final BlockProvider blockProvider,
      final StateAndBlockProvider stateProvider,
      final StorageUpdateChannel storageUpdateChannel,
      final ProtoArrayStorageChannel protoArrayStorageChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ReorgEventChannel reorgEventChannel,
      final EventBus eventBus) {
    this.asyncRunner = asyncRunner;
    this.metricsSystem = metricsSystem;
    this.storeConfig = storeConfig;
    this.blockProvider = blockProvider;
    this.stateProvider = stateProvider;
    this.reorgEventChannel = reorgEventChannel;
    this.eventBus = eventBus;
    this.storageUpdateChannel = storageUpdateChannel;
    this.protoArrayStorageChannel = protoArrayStorageChannel;
    this.finalizedCheckpointChannel = finalizedCheckpointChannel;
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

  public SafeFuture<Void> initializeFromGenesis(final BeaconState genesisState) {
    final AnchorPoint genesis = AnchorPoint.fromGenesisState(genesisState);
    return StoreBuilder.forkChoiceStoreBuilder(
            asyncRunner, metricsSystem, blockProvider, stateProvider, genesis)
        .storeConfig(storeConfig)
        .build()
        .thenAccept(
            store -> {
              final boolean result = setStore(store);
              if (!result) {
                throw new IllegalStateException(
                    "Failed to set genesis state: store has already been initialized");
              }

              storageUpdateChannel.onGenesis(genesis);
              eventBus.post(genesis);

              // The genesis state is by definition finalized so just get the root from there.
              final SignedBlockAndState headBlock = store.getLatestFinalizedBlockAndState();
              updateHead(headBlock.getRoot(), headBlock.getSlot());
            });
  }

  public UInt64 getGenesisTime() {
    return genesisTime;
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
    ProtoArrayForkChoiceStrategy.initialize(this.store, protoArrayStorageChannel)
        .thenAccept(
            forkChoiceStrategy -> {
              this.forkChoiceStrategy = Optional.of(forkChoiceStrategy);
              storeInitializedFuture.complete(null);
            })
        .join();
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
                    forkChoiceStrategy.orElseThrow(), head.getRoot(), startSlot, step, count))
        .orElseGet(TreeMap::new);
  }

  public NavigableMap<UInt64, Bytes32> getAncestorsOnFork(final UInt64 startSlot, Bytes32 root) {
    return ForkChoiceUtil.getAncestorsOnFork(forkChoiceStrategy.orElseThrow(), root, startSlot);
  }

  public Optional<ForkChoiceStrategy> getForkChoiceStrategy() {
    return forkChoiceStrategy.map(Function.identity());
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
    updateHead(root, currentSlot, false);
  }

  public void updateHead(Bytes32 root, UInt64 currentSlot, boolean syncing) {
    final Optional<ChainHead> originalChainHead = chainHead;

    // Never let the fork choice slot go backwards.
    final UInt64 newForkChoiceSlot =
        currentSlot.max(originalChainHead.map(ChainHead::getForkChoiceSlot).orElse(UInt64.ZERO));
    store
        .retrieveBlockAndState(root)
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
        .thenAccept(headBlock -> updateChainHead(originalChainHead, headBlock, syncing))
        .reportExceptions();
  }

  private void updateChainHead(
      final Optional<ChainHead> originalHead, final ChainHead newChainHead, final boolean syncing) {
    synchronized (this) {
      if (!chainHead.equals(originalHead)) {
        // The chain head has been updated while we were waiting for the newChainHead
        // Skip this update to avoid accidentally regressing the chain head
        LOG.info("Skipping head block update to avoid potential rollback of the chain head.");
        return;
      }
      this.chainHead = Optional.of(newChainHead);
      if (originalHead
          .map(head -> hasReorgedFrom(head.getRoot(), head.getForkChoiceSlot()))
          .orElse(false)) {

        final ChainHead previousChainHead = originalHead.get();

        final UInt64 commonAncestorSlot = previousChainHead.findCommonAncestor(newChainHead);

        if (!syncing) {
          // Don't log reorgs while syncing as it's just pointless noise.
          // We're filling in historic slots so every block is a reorg vs the chain of empty slots
          LOG.info(
              "Chain reorg from {} to {}. Common ancestor at slot {}",
              formatBlock(previousChainHead.getForkChoiceSlot(), previousChainHead.getRoot()),
              formatBlock(newChainHead.getForkChoiceSlot(), newChainHead.getRoot()),
              commonAncestorSlot);
        }

        reorgCounter.inc();
        reorgEventChannel.reorgOccurred(
            newChainHead.getRoot(),
            newChainHead.getSlot(),
            previousChainHead.getRoot(),
            commonAncestorSlot);
      }
    }

    bestBlockInitialized.complete(null);
  }

  private boolean hasReorgedFrom(
      final Bytes32 originalHeadRoot, final UInt64 originalForkChoiceSlot) {
    // Get the block root in effect at the old fork choice slot on the current chain. If this is a
    // different fork to the previous chain the root at originalForkChoiceSlot will be different
    // from originalHeadRoot. If it's an extension of the same chain it will match.
    return getBlockRootBySlot(originalForkChoiceSlot)
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
    return chainHead.map(SignedBlockAndState::getRoot);
  }

  /** @return The block and state at the head of the chain. */
  public Optional<BeaconBlockAndState> getHeadBlockAndState() {
    return chainHead.map(SignedBlockAndState::toUnsigned);
  }

  /** @return The block at the head of the chain. */
  public Optional<SignedBeaconBlock> getHeadBlock() {
    return chainHead.map(SignedBlockAndState::getBlock);
  }

  /**
   * Retrieves the state of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public Optional<BeaconState> getBestState() {
    return chainHead.map(SignedBlockAndState::getState);
  }

  /**
   * Retrieves the slot of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public UInt64 getHeadSlot() {
    return chainHead.map(SignedBlockAndState::getSlot).orElse(UInt64.ZERO);
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
    return forkChoiceStrategy.flatMap(strategy -> get_ancestor(strategy, headBlockRoot, slot));
  }

  public UInt64 getFinalizedEpoch() {
    return getFinalizedCheckpoint().map(Checkpoint::getEpoch).orElse(UInt64.ZERO);
  }

  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return store == null ? Optional.empty() : Optional.of(store.getFinalizedCheckpoint());
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint finalizedCheckpoint) {
    finalizedCheckpointChannel.onNewFinalizedCheckpoint(finalizedCheckpoint);
    forkChoiceStrategy.ifPresent(strategy -> strategy.maybePrune(finalizedCheckpoint.getRoot()));
  }

  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(final Checkpoint checkpoint) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_STATE_FUTURE;
    }

    return store.retrieveCheckpointState(checkpoint);
  }
}
