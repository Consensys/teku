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
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.async.SafeFuture;

public class ChainStorage implements StorageUpdateChannel, StorageQueryChannel {
  private final EventBus eventBus;

  private volatile Database database;
  private volatile Optional<UpdatableStore> cachedStore = Optional.empty();

  private ChainStorage(final EventBus eventBus, final Database database) {
    this.eventBus = eventBus;
    this.database = database;
  }

  public static ChainStorage create(final EventBus eventBus, final Database database) {
    return new ChainStorage(eventBus, database);
  }

  public void start() {
    eventBus.register(this);
  }

  public void stop() {
    eventBus.unregister(this);
  }

  private synchronized Optional<UpdatableStore> getStore() {
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
  public SafeFuture<Optional<UpdatableStore>> onStoreRequest() {
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
    Optional<SignedBeaconBlock> block =
        database.getFinalizedRootAtSlot(slot).flatMap(database::getSignedBlock);
    return SafeFuture.completedFuture(block);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(
      final UnsignedLong slot) {
    final Optional<SignedBeaconBlock> block =
        database.getLatestFinalizedRootAtSlot(slot).flatMap(database::getSignedBlock);
    return SafeFuture.completedFuture(block);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(database.getSignedBlock(blockRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UnsignedLong slot) {
    return SafeFuture.completedFuture(
        database.getLatestFinalizedRootAtSlot(slot).flatMap(database::getFinalizedState));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(database.getFinalizedState(blockRoot));
  }
}
