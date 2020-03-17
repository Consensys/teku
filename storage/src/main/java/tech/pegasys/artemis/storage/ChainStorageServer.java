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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
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
import tech.pegasys.artemis.storage.events.GetStoreRequest;
import tech.pegasys.artemis.storage.events.GetStoreResponse;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateCompleteEvent;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;
import tech.pegasys.artemis.storage.events.StoreGenesisDiskUpdateEvent;
import tech.pegasys.artemis.storage.events.StoreInitializedFromStorageEvent;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class ChainStorageServer {
  private static final Logger LOG = LogManager.getLogger();

  private volatile Database database;
  private final EventBus eventBus;
  private final ArtemisConfiguration configuration;

  public final String DATABASE_VERSION = "1.0";

  private final AtomicBoolean storeIsPersisted;
  private volatile Optional<Store> cachedStore = Optional.empty();

  public ChainStorageServer(EventBus eventBus, ArtemisConfiguration config) {
    this.configuration = config;
    this.eventBus = eventBus;
    storeIsPersisted = new AtomicBoolean(configuration.startFromDisk());
  }

  public void start() {
    File dataStoragePath = new File(configuration.getDataPath());
    File databaseVersionPath = new File(dataStoragePath, "/db.version");
    File databaseStoragePath = new File(configuration.getDataPath() + "/db");

    LOG.info("Data directory set to: {}", dataStoragePath);
    preflightCheck(databaseStoragePath, databaseVersionPath);

    final StateStorageMode stateStorageMode =
        StateStorageMode.fromString(configuration.getStateStorageMode());
    this.database = MapDbDatabase.createOnDisk(databaseStoragePath, stateStorageMode);
    eventBus.register(this);

    final Optional<Store> store = getStore();
    eventBus.post(new StoreInitializedFromStorageEvent(store));
  }

  private synchronized Optional<Store> getStore() {
    if (!storeIsPersisted.get()) {
      return Optional.empty();
    } else if (cachedStore.isEmpty()) {
      // Create store from database
      cachedStore = database.createMemoryStore();
    }

    return cachedStore;
  }

  private synchronized void handleStoreUpdate(final DatabaseUpdateResult result) {
    if (result.isSuccessful()) {
      cachedStore = Optional.empty();
    }
  }

  private synchronized void handleStoreGenesis() {
    storeIsPersisted.set(true);
  }

  private void preflightCheck(File databaseStoragePath, File databaseVersionPath) {
    if (databaseStoragePath.exists() && !databaseVersionPath.exists()) {
      throw new DatabaseStorageException(
          String.format(
              "No database version file was found, and the database path %s exists.",
              databaseStoragePath.getAbsolutePath()));
    }
    if (!databaseStoragePath.exists() && !databaseStoragePath.mkdirs()) {
      throw new DatabaseStorageException(
          String.format(
              "Unable to create the path to store database files at %s",
              databaseStoragePath.getAbsolutePath()));
    }

    validateDatabaseVersion(databaseVersionPath);
  }

  private void validateDatabaseVersion(File databaseVersionFile) {
    try {
      if (databaseVersionFile.exists()) {
        String ver = Files.readString(databaseVersionFile.toPath()).trim();
        if (!DATABASE_VERSION.equals(ver)) {
          throw new DatabaseStorageException(
              String.format(
                  "The database version found (%s) does not match the expected version(%s).\n"
                      + "Aborting startup to prevent corruption of the database.\n",
                  ver, DATABASE_VERSION));
        }
        LOG.log(
            Level.INFO,
            String.format(
                "The existing database is version %s, from file: %s",
                DATABASE_VERSION, databaseVersionFile.getAbsolutePath()));
      } else {
        LOG.log(
            Level.INFO,
            String.format(
                "Recording database version %s to file: %s",
                DATABASE_VERSION, databaseVersionFile.getAbsolutePath()));
        Files.writeString(
            databaseVersionFile.toPath(), DATABASE_VERSION, StandardOpenOption.CREATE);
      }
    } catch (IOException e) {
      LOG.log(Level.ERROR, "Failed to write database version to file", e);
    }
  }

  @Subscribe
  public void onStoreRequest(final GetStoreRequest request) {
    eventBus.post(new GetStoreResponse(request.getId(), getStore()));
  }

  @Subscribe
  public void onStoreDiskUpdate(final StoreDiskUpdateEvent event) {
    final DatabaseUpdateResult result = database.update(event);
    handleStoreUpdate(result);
    eventBus.post(new StoreDiskUpdateCompleteEvent(event.getTransactionId(), result));
  }

  @Subscribe
  public void onStoreGenesis(final StoreGenesisDiskUpdateEvent event) {
    database.storeGenesis(event.getStore());
    handleStoreGenesis();
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
