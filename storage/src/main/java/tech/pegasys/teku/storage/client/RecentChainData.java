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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.ForkChoiceUtil;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.store.StoreFactory;
import tech.pegasys.teku.storage.store.StoreUpdateHandler;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.async.SafeFuture;

/** This class is the ChainStorage client-side logic */
public abstract class RecentChainData implements StoreUpdateHandler {

  private static final Logger LOG = LogManager.getLogger();

  protected final EventBus eventBus;
  protected final FinalizedCheckpointChannel finalizedCheckpointChannel;
  protected final StorageUpdateChannel storageUpdateChannel;
  private final MetricsSystem metricsSystem;
  private final ReorgEventChannel reorgEventChannel;

  private final AtomicBoolean storeInitialized = new AtomicBoolean(false);
  private final SafeFuture<Void> storeInitializedFuture = new SafeFuture<>();
  private final SafeFuture<Void> bestBlockInitialized = new SafeFuture<>();

  private volatile UpdatableStore store;
  private volatile Optional<SignedBlockAndState> chainHead = Optional.empty();
  private volatile UnsignedLong genesisTime;

  RecentChainData(
      final MetricsSystem metricsSystem,
      final StorageUpdateChannel storageUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ReorgEventChannel reorgEventChannel,
      final EventBus eventBus) {
    this.metricsSystem = metricsSystem;
    this.reorgEventChannel = reorgEventChannel;
    this.eventBus = eventBus;
    this.storageUpdateChannel = storageUpdateChannel;
    this.finalizedCheckpointChannel = finalizedCheckpointChannel;
  }

  public void subscribeStoreInitialized(Runnable runnable) {
    storeInitializedFuture.always(runnable);
  }

  public void subscribeBestBlockInitialized(Runnable runnable) {
    bestBlockInitialized.always(runnable);
  }

  public void initializeFromGenesis(final BeaconState genesisState) {
    final UpdatableStore store = StoreFactory.getForkChoiceStore(metricsSystem, genesisState);
    final boolean result = setStore(store);
    if (!result) {
      throw new IllegalStateException(
          "Failed to set genesis state: store has already been initialized");
    }

    storageUpdateChannel.onGenesis(store);
    eventBus.post(store);

    // The genesis state is by definition finalized so just get the root from there.
    Bytes32 headBlockRoot = store.getFinalizedCheckpoint().getRoot();
    BeaconBlock headBlock = store.getBlock(headBlockRoot);
    updateBestBlock(headBlockRoot, headBlock.getSlot());
  }

  public UnsignedLong getGenesisTime() {
    return genesisTime;
  }

  public boolean isPreGenesis() {
    return this.store == null;
  }

  /**
   * Returns true if the best block / chainhead has not yet been set.
   *
   * @return true if the best block is unknown, false otherwise.
   */
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

    // Update head
    final StoreTransaction tx = startStoreTransaction();
    tx.updateHead();
    tx.commit().reportExceptions();

    return true;
  }

  public UpdatableStore getStore() {
    return store;
  }

  public NavigableMap<UnsignedLong, Bytes32> getAncestorRoots(
      final UnsignedLong startSlot, final UnsignedLong step, final UnsignedLong count) {
    return chainHead
        .map(head -> ForkChoiceUtil.getAncestors(store, head.getRoot(), startSlot, step, count))
        .orElseGet(TreeMap::new);
  }

  public StoreTransaction startStoreTransaction() {
    return store.startTransaction(storageUpdateChannel, this);
  }

  // NETWORKING RELATED INFORMATION METHODS:

  /**
   * Update Best Block
   *
   * @param root
   * @param slot
   */
  // TODO - we should make this method private
  @VisibleForTesting
  public void updateBestBlock(Bytes32 root, UnsignedLong slot) {
    synchronized (this) {
      final SignedBeaconBlock newBestBlock = store.getSignedBlock(root);
      final BeaconState newBestState = store.getBlockState(root);
      if (newBestBlock == null || newBestState == null) {
        LOG.warn(
            "Unable to update best block (slot={}, root={}). Corresponding {} unavailable",
            slot,
            root,
            newBestBlock == null ? "block" : "state");
        return;
      }
      final SignedBlockAndState newChainHead = new SignedBlockAndState(newBestBlock, newBestState);

      final Optional<Bytes32> originalBestRoot = chainHead.map(SignedBlockAndState::getRoot);
      final UnsignedLong originalBestSlot =
          chainHead.map(SignedBlockAndState::getSlot).orElse(UnsignedLong.ZERO);

      this.chainHead = Optional.of(newChainHead);
      if (originalBestRoot
          .map(original -> hasReorgedFrom(original, originalBestSlot))
          .orElse(false)) {
        reorgEventChannel.reorgOccurred(root, newChainHead.getSlot());
      }
    }

    bestBlockInitialized.complete(null);
  }

  private boolean hasReorgedFrom(
      final Bytes32 originalBestRoot, final UnsignedLong originalBestSlot) {
    // Get the block root in effect at the old best slot on the current best chain. If this is a
    // different fork to the previous chain the root at originalBestSlot will be different from
    // originalBestRoot. If it's an extension of the same chain it will match.
    return getBlockRootBySlot(originalBestSlot)
        .map(rootAtOldBestSlot -> !rootAtOldBestSlot.equals(originalBestRoot))
        .orElse(true);
  }

  /**
   * Return the current slot based on our Store's time.
   *
   * @return The current slot.
   */
  public Optional<UnsignedLong> getCurrentSlot() {
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

  /**
   * If available, return the best block and state.
   *
   * @return The best block along with its corresponding state.
   */
  public Optional<BeaconBlockAndState> getBestBlockAndState() {
    return chainHead.map(SignedBlockAndState::toUnsigned);
  }

  /**
   * If available, return the best block.
   *
   * @return The best block.
   */
  public Optional<SignedBeaconBlock> getBestBlock() {
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
  public UnsignedLong getBestSlot() {
    return chainHead.map(SignedBlockAndState::getSlot).orElse(UnsignedLong.ZERO);
  }

  public boolean containsBlock(final Bytes32 root) {
    return Optional.ofNullable(store).map(s -> s.containsBlock(root)).orElse(false);
  }

  public Optional<BeaconBlock> getBlockByRoot(final Bytes32 root) {
    if (store == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(store.getBlock(root));
  }

  public Optional<SignedBeaconBlock> getSignedBlockByRoot(final Bytes32 root) {
    if (store == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(store.getSignedBlock(root));
  }

  public Optional<BeaconState> getBlockState(final Bytes32 blockRoot) {
    if (store == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(store.getBlockState(blockRoot));
  }

  public Optional<BeaconState> getStateInEffectAtSlot(final UnsignedLong slot) {
    return getBlockRootBySlot(slot).map(blockRoot -> store.getBlockState(blockRoot));
  }

  public Optional<Bytes32> getBlockRootBySlot(final UnsignedLong slot) {
    return chainHead.flatMap(head -> getBlockRootBySlot(slot, head.getRoot()));
  }

  public Optional<Bytes32> getBlockRootBySlot(
      final UnsignedLong slot, final Bytes32 headBlockRoot) {
    return Optional.ofNullable(store).flatMap(store -> get_ancestor(store, headBlockRoot, slot));
  }

  // TODO: These methods should not return zero if null. We should handle this better
  public UnsignedLong getFinalizedEpoch() {
    return store == null ? UnsignedLong.ZERO : store.getFinalizedCheckpoint().getEpoch();
  }

  public UnsignedLong getBestJustifiedEpoch() {
    return store == null ? UnsignedLong.ZERO : store.getBestJustifiedCheckpoint().getEpoch();
  }

  public Bytes32 getFinalizedRoot() {
    return store == null ? null : store.getFinalizedCheckpoint().getRoot();
  }

  @Override
  public void onNewHeadBlock(final Bytes32 headRoot) {
    final UnsignedLong headSlot =
        store
            .getBlockSlot(headRoot)
            .orElseThrow(
                () -> new IllegalStateException("Unable to retrieve the slot of fork choice head"));
    this.updateBestBlock(headRoot, headSlot);
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint finalizedCheckpoint) {
    finalizedCheckpointChannel.onNewFinalizedCheckpoint(finalizedCheckpoint);
  }
}
