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

import static com.google.common.primitives.UnsignedLong.ONE;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.StreamingStateRegenerator;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.async.SafeFuture;

public class ChainStorage implements StorageUpdateChannel, StorageQueryChannel {
  private final EventBus eventBus;

  private final Database database;
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
  public SafeFuture<Optional<BeaconState>> getFinalizedStateAtSlot(final UnsignedLong slot) {
    return SafeFuture.of(() -> getFinalizedStateAtSlotSync(slot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(
        () ->
            database
                .getSlotForFinalizedBlockRoot(blockRoot)
                .flatMap(this::getFinalizedStateAtSlotSync));
  }

  private Optional<BeaconState> getFinalizedStateAtSlotSync(final UnsignedLong slot) {
    return database
        .getLatestAvailableFinalizedState(slot)
        .map(state -> regenerateState(slot, state));
  }

  private BeaconState regenerateState(final UnsignedLong slot, final BeaconState state) {
    if (state.getSlot().equals(slot)) {
      return state;
    }
    try (final Stream<SignedBeaconBlock> blocks =
        database.streamFinalizedBlocks(state.getSlot().plus(ONE), slot)) {
      return StreamingStateRegenerator.regenerate(state, blocks);
    }
  }
}
