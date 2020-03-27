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

package tech.pegasys.artemis.storage;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.logging.EventLogger.EVENT_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.storage.Store.StoreUpdateHandler;
import tech.pegasys.artemis.storage.api.StorageUpdateChannel;
import tech.pegasys.artemis.storage.events.FinalizedCheckpointEvent;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

/** This class is the ChainStorage client-side logic */
public class ChainStorageClient implements ChainStorage, StoreUpdateHandler {

  private static final Logger LOG = LogManager.getLogger();

  protected final EventBus eventBus;
  private final StorageUpdateChannel storageUpdateChannel;

  private final AtomicBoolean storeInitialized = new AtomicBoolean(false);
  private final SafeFuture<Void> storeInitializedFuture = new SafeFuture<>();
  private final SafeFuture<Void> bestBlockInitialized = new SafeFuture<>();

  private volatile Store store;
  private volatile Optional<Bytes32> bestBlockRoot =
      Optional.empty(); // block chosen by lmd ghost to build and attest on
  private volatile UnsignedLong bestSlot =
      UnsignedLong.ZERO; // slot of the block chosen by lmd ghost to build and attest on
  // Time
  private volatile UnsignedLong genesisTime;

  public static ChainStorageClient memoryOnlyClient(
      final EventBus eventBus, final StorageUpdateChannel storageUpdateChannel) {
    final ChainStorageClient client = new ChainStorageClient(storageUpdateChannel, eventBus);
    eventBus.register(client);
    return client;
  }

  public static SafeFuture<ChainStorageClient> storageBackedClient(
      final EventBus eventBus, final StorageUpdateChannel storageUpdateChannel) {
    final StorageBackedChainStorageClientFactory factory =
        new StorageBackedChainStorageClientFactory(storageUpdateChannel, eventBus);
    eventBus.register(factory);
    return factory.get();
  }

  @VisibleForTesting
  static ChainStorageClient memoryOnlyClientWithStore(
      final EventBus eventBus, final Store store, final StorageUpdateChannel storageUpdateChannel) {
    final ChainStorageClient client = new ChainStorageClient(storageUpdateChannel, eventBus);
    eventBus.register(client);
    client.setStore(store);
    return client;
  }

  ChainStorageClient(final StorageUpdateChannel storageUpdateChannel, final EventBus eventBus) {
    this.eventBus = eventBus;
    this.storageUpdateChannel = storageUpdateChannel;
  }

  public void subscribeStoreInitialized(Runnable runnable) {
    storeInitializedFuture.always(runnable);
  }

  public void subscribeBestBlockInitialized(Runnable runnable) {
    bestBlockInitialized.always(runnable);
  }

  public void initializeFromGenesis(final BeaconState genesisState) {
    final Store store = Store.get_genesis_store(genesisState);
    final boolean result = setStore(store);
    if (!result) {
      throw new IllegalStateException(
          "Failed to set genesis state: store has already been initialized");
    }

    storageUpdateChannel.onGenesis(store);
    eventBus.post(store);

    // The genesis state is by definition finalised so just get the root from there.
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

  boolean setStore(Store store) {
    if (!storeInitialized.compareAndSet(false, true)) {
      return false;
    }
    this.store = store;
    this.genesisTime = this.store.getGenesisTime();
    storeInitializedFuture.complete(null);
    return true;
  }

  public Store getStore() {
    return store;
  }

  public Store.Transaction startStoreTransaction() {
    return store.startTransaction(storageUpdateChannel, this);
  }

  // NETWORKING RELATED INFORMATION METHODS:

  /**
   * Update Best Block
   *
   * @param root
   * @param slot
   */
  public void updateBestBlock(Bytes32 root, UnsignedLong slot) {
    this.bestBlockRoot = Optional.of(root);
    this.bestSlot = slot;
    bestBlockInitialized.complete(null);
  }

  public Bytes4 getForkAtHead() {
    return getForkAtSlot(bestSlot);
  }

  public Bytes4 getForkAtSlot(UnsignedLong slot) {
    return getForkAtEpoch(compute_epoch_at_slot(slot));
  }

  public Bytes4 getForkAtEpoch(UnsignedLong epoch) {
    // TODO - add better fork configuration management
    final Optional<BeaconState> bestStateRoot = getBestBlockRootState();
    if (isPreGenesis() || bestStateRoot.isEmpty()) {
      // We don't have anywhere to look for fork data, so just return the initial fork
      return Constants.GENESIS_FORK_VERSION;
    }
    // For now, we don't have any forks, so just use the latest
    Fork latestFork = bestStateRoot.get().getFork();
    return epoch.compareTo(latestFork.getEpoch()) < 0
        ? latestFork.getPrevious_version()
        : latestFork.getCurrent_version();
  }

  /**
   * Retrieves the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public Optional<Bytes32> getBestBlockRoot() {
    return this.bestBlockRoot;
  }

  /**
   * Retrieves the state of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public Optional<BeaconState> getBestBlockRootState() {
    return bestBlockRoot.map(root -> this.store.getBlockState(root));
  }

  /**
   * Retrieves the slot of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public UnsignedLong getBestSlot() {
    return this.bestSlot;
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    EVENT_LOG.unprocessedBlock(block.getState_root());
  }

  @Subscribe
  public void onNewUnprocessedAttestation(Attestation attestation) {
    EVENT_LOG.unprocessedAttestation(attestation.getData().getBeacon_block_root());
  }

  @Subscribe
  public void onNewAggregateAndProof(AggregateAndProof attestation) {
    EVENT_LOG.aggregateAndProof(attestation.getAggregate().getData().getBeacon_block_root());
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

  public Optional<BeaconState> getBlockState(final Bytes32 blockRoot) {
    if (store == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(store.getBlockState(blockRoot));
  }

  public Optional<BeaconBlock> getBlockBySlot(final UnsignedLong slot) {
    return getBlockRootBySlot(slot)
        .map(blockRoot -> store.getBlock(blockRoot))
        .filter(block -> block.getSlot().equals(slot));
  }

  public Optional<BeaconState> getStateBySlot(final UnsignedLong slot) {
    return getBlockRootBySlot(slot)
        .map(blockRoot -> store.getBlockState(blockRoot))
        .filter(state -> state.getSlot().equals(slot));
  }

  public boolean isIncludedInBestState(final Bytes32 blockRoot) {
    if (store == null) {
      return false;
    }
    final BeaconBlock block = store.getBlock(blockRoot);
    if (block == null) {
      return false;
    }
    return getBlockRootBySlot(block.getSlot())
        .map(actualRoot -> actualRoot.equals(block.hash_tree_root()))
        .orElse(false);
  }

  public Optional<Bytes32> getBlockRootBySlot(final UnsignedLong slot) {
    if (store == null || bestBlockRoot.isEmpty()) {
      LOG.trace("No block root at slot {} because store or best block root is not set", slot);
      return Optional.empty();
    }
    if (bestSlot.equals(slot)) {
      LOG.trace("Block root at slot {} is the current best slot root", slot);
      return bestBlockRoot;
    }

    final BeaconState bestState = store.getBlockState(bestBlockRoot.get());
    if (bestState == null) {
      LOG.trace("No block root at slot {} because best state is not available", slot);
      return Optional.empty();
    }

    if (!BeaconStateUtil.isBlockRootAvailableFromState(bestState, slot)) {
      LOG.trace("No block root at slot {} because slot is not within historical root", slot);
      return Optional.empty();
    }

    return Optional.of(BeaconStateUtil.get_block_root_at_slot(bestState, slot));
  }

  // TODO: These methods should not return zero if null. We should handle this better
  public UnsignedLong getFinalizedEpoch() {
    return store == null ? UnsignedLong.ZERO : store.getFinalizedCheckpoint().getEpoch();
  }

  public Bytes32 getFinalizedRoot() {
    return store == null ? null : store.getFinalizedCheckpoint().getRoot();
  }

  public UnsignedLong getJustifiedEpoch() {
    return store == null ? UnsignedLong.ZERO : store.getJustifiedCheckpoint().getEpoch();
  }

  public Bytes32 getJustifiedRoot() {
    return store == null ? null : store.getJustifiedCheckpoint().getRoot();
  }

  @Override
  public void onNewFinalizedCheckpoint(final Checkpoint finalizedCheckpoint) {
    eventBus.post(new FinalizedCheckpointEvent(finalizedCheckpoint));
  }
}
