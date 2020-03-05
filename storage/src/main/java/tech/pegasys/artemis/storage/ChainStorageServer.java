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
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.storage.events.GetFinalizedStateAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedStateAtSlotResponse;
import tech.pegasys.artemis.storage.events.GetLatestFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetLatestFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateCompleteEvent;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;
import tech.pegasys.artemis.storage.events.StoreGenesisDiskUpdateEvent;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.teku.logging.StatusLogger;

public class ChainStorageServer {

  private static final StatusLogger STATUS_LOG = StatusLogger.getLogger();

  private Database database;
  private final EventBus eventBus;
  private final ArtemisConfiguration configuration;

  public final String DATABASE_VERSION = "1.0";

  public ChainStorageServer(EventBus eventBus, ArtemisConfiguration config) {
    this.configuration = config;
    this.eventBus = eventBus;
  }

  public void start() {
    File dataStoragePath = new File(configuration.getDataPath());
    File databaseVersionPath = new File(dataStoragePath, "/db.version");
    File databaseStoragePath = new File(configuration.getDataPath() + "/db");

    preflightCheck(databaseStoragePath, databaseVersionPath);

    this.database = MapDbDatabase.createOnDisk(databaseStoragePath, configuration.startFromDisk());
    eventBus.register(this);
    if (configuration.startFromDisk()) {
      Store memoryStore = database.createMemoryStore();
      eventBus.post(memoryStore);
    }
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
        STATUS_LOG.log(
            Level.INFO,
            String.format(
                "The existing database is version %s, from file: %s",
                DATABASE_VERSION, databaseVersionFile.getAbsolutePath()));
      } else {
        STATUS_LOG.log(
            Level.INFO,
            String.format(
                "Recording database version %s to file: %s",
                DATABASE_VERSION, databaseVersionFile.getAbsolutePath()));
        Files.writeString(
            databaseVersionFile.toPath(), DATABASE_VERSION, StandardOpenOption.CREATE);
      }
    } catch (IOException e) {
      STATUS_LOG.log(Level.ERROR, "Failed to write database version to file", e);
    }
  }

  @Subscribe
  public void onStoreDiskUpdate(final StoreDiskUpdateEvent event) {
    final DatabaseUpdateResult result = database.update(event);
    eventBus.post(new StoreDiskUpdateCompleteEvent(event.getTransactionId(), result));
  }

  @Subscribe
  public void onStoreGenesis(final StoreGenesisDiskUpdateEvent event) {
    database.storeGenesis(event.getStore());
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
  public void onGetLatestBlockBySlotRequest(final GetLatestFinalizedBlockAtSlotRequest request) {
    final Optional<SignedBeaconBlock> block =
        database.getLatestFinalizedRootAtSlot(request.getSlot()).flatMap(database::getSignedBlock);
    eventBus.post(new GetLatestFinalizedBlockAtSlotResponse(request.getSlot(), block));
  }
}
