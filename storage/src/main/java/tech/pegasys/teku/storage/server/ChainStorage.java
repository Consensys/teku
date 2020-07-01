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

package tech.pegasys.teku.storage.server;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.server.state.FinalizedStateCache;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;

public class ChainStorage implements StorageUpdateChannel, StorageQueryChannel {

  private static final int FINALIZED_STATE_CACHE_SIZE = Constants.SLOTS_PER_EPOCH * 3;

  private final EventBus eventBus;

  private final Database database;
  private final FinalizedStateCache finalizedStateCache;
  private volatile Optional<StoreBuilder> cachedStore = Optional.empty();

  private ChainStorage(
      final EventBus eventBus,
      final Database database,
      final FinalizedStateCache finalizedStateCache) {
    this.eventBus = eventBus;
    this.database = database;
    this.finalizedStateCache = finalizedStateCache;
  }

  public static ChainStorage create(final EventBus eventBus, final Database database) {
    return new ChainStorage(
        eventBus, database, new FinalizedStateCache(database, FINALIZED_STATE_CACHE_SIZE, true));
  }

  public void start() {
    eventBus.register(this);
  }

  public void stop() {
    eventBus.unregister(this);
  }

  private synchronized Optional<StoreBuilder> getStore() {
    if (cachedStore.isEmpty()) {
      // Create store from database
      cachedStore = database.createMemoryStore();
    }

    return cachedStore;
  }

  private synchronized void handleStoreUpdate() {
    cachedStore = Optional.empty();
  }

  @Override
  public SafeFuture<Optional<StoreBuilder>> onStoreRequest() {
    if (database == null) {
      return SafeFuture.failedFuture(new IllegalStateException("Database not initialized yet"));
    }

    return SafeFuture.completedFuture(getStore());
  }

  @Override
  public SafeFuture<Void> onStorageUpdate(final StorageUpdate event) {
    return SafeFuture.fromRunnable(
        () -> {
          database.update(event);
          handleStoreUpdate();
        });
  }

  @Override
  public void onGenesis(final UpdatableStore store) {
    database.storeGenesis(store);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(final UnsignedLong slot) {
    return SafeFuture.of(() -> database.getFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(
      final UnsignedLong slot) {
    return SafeFuture.of(() -> database.getLatestFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(() -> database.getSignedBlock(blockRoot));
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(
      final Set<Bytes32> blockRoots) {
    return SafeFuture.of(() -> database.getHotBlocks(blockRoots));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UnsignedLong slot) {
    return SafeFuture.of(() -> getLatestFinalizedStateAtSlotSync(slot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(
        () ->
            database
                .getSlotForFinalizedBlockRoot(blockRoot)
                .flatMap(this::getLatestFinalizedStateAtSlotSync));
  }

  private Optional<BeaconState> getLatestFinalizedStateAtSlotSync(final UnsignedLong slot) {
    return finalizedStateCache.getFinalizedState(slot);
  }
}
