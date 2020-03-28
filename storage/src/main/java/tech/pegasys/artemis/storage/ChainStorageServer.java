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

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Optional;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.storage.api.StorageUpdateChannel;
import tech.pegasys.artemis.storage.events.GetBlockByBlockRootRequest;
import tech.pegasys.artemis.storage.events.GetBlockByBlockRootResponse;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.storage.events.GetFinalizedStateAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedStateAtSlotResponse;
import tech.pegasys.artemis.storage.events.GetFinalizedStateByBlockRootRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedStateByBlockRootResponse;
import tech.pegasys.artemis.storage.events.GetLatestFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetLatestFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.storage.events.diskupdates.StorageUpdate;
import tech.pegasys.artemis.storage.events.diskupdates.StorageUpdateResult;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class ChainStorageServer implements StorageUpdateChannel {
  private final EventBus eventBus;
  private final VersionedDatabaseFactory databaseFactory;

  private volatile Database database;
  private volatile Optional<Store> cachedStore = Optional.empty();

  private ChainStorageServer(EventBus eventBus, final VersionedDatabaseFactory dbFactory) {
    this.eventBus = eventBus;
    this.databaseFactory = dbFactory;
  }

  public static ChainStorageServer create(EventBus eventBus, ArtemisConfiguration config) {
    return new ChainStorageServer(eventBus, new VersionedDatabaseFactory(config));
  }

  public void start() {
    this.database = databaseFactory.createDatabase();
    eventBus.register(this);
  }

  private synchronized Optional<Store> getStore() {
    if (cachedStore.isEmpty()) {
      // Create store from database
      cachedStore = database.createMemoryStore();
    }

    return cachedStore;
  }

  private synchronized void handleStoreUpdate(final StorageUpdateResult result) {
    if (result.isSuccessful()) {
      cachedStore = Optional.empty();
    }
  }

  @Override
  public SafeFuture<Optional<Store>> onStoreRequest() {
    if (database == null) {
      return SafeFuture.failedFuture(new RuntimeException("Database not initialized yet"));
    }

    return SafeFuture.completedFuture(getStore());
  }

  @Override
  public SafeFuture<StorageUpdateResult> onStorageUpdate(final StorageUpdate event) {
    return SafeFuture.of(
        () -> {
          StorageUpdateResult result = database.update(event);
          handleStoreUpdate(result);
          return result;
        });
  }

  @Override
  public void onGenesis(final Store store) {
    database.storeGenesis(store);
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onGetBlockBySlotRequest(final GetFinalizedBlockAtSlotRequest request) {
    final Optional<SignedBeaconBlock> block =
        database.getFinalizedRootAtSlot(request.getSlot()).flatMap(database::getSignedBlock);
    eventBus.post(new GetFinalizedBlockAtSlotResponse(request.getSlot(), block));
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onGetStateBySlotRequest(final GetFinalizedStateAtSlotRequest request) {
    final Optional<BeaconState> state =
        database.getFinalizedRootAtSlot(request.getSlot()).flatMap(database::getState);
    eventBus.post(new GetFinalizedStateAtSlotResponse(request.getSlot(), state));
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onGetStateByBlockRequest(final GetFinalizedStateByBlockRootRequest request) {
    final Optional<BeaconState> state = database.getState(request.getBlockRoot());
    eventBus.post(new GetFinalizedStateByBlockRootResponse(request.getBlockRoot(), state));
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onGetLatestBlockBySlotRequest(final GetLatestFinalizedBlockAtSlotRequest request) {
    final Optional<SignedBeaconBlock> block =
        database.getLatestFinalizedRootAtSlot(request.getSlot()).flatMap(database::getSignedBlock);
    eventBus.post(new GetLatestFinalizedBlockAtSlotResponse(request.getSlot(), block));
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onGetBlockByBlockRootRequest(final GetBlockByBlockRootRequest request) {
    final Optional<SignedBeaconBlock> block = database.getSignedBlock(request.getBlockRoot());
    eventBus.post(new GetBlockByBlockRootResponse(request.getBlockRoot(), block));
  }
}
