/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.storage;

import com.google.common.eventbus.EventBus;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.TrackingReorgEventChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.server.ChainStorageServer;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseFactory;
import tech.pegasys.teku.storage.server.rocksdb.InMemoryRocksDbDatabaseFactory;
import tech.pegasys.teku.storage.server.rocksdb.core.MockRocksDbInstance;
import tech.pegasys.teku.util.config.StateStorageMode;

public class InMemoryStorageSystem {
  private final EventBus eventBus;
  private final TrackingReorgEventChannel reorgEventChannel;

  private final MockRocksDbInstance rocksDbInstance;
  private final RecentChainData recentChainData;
  private final CombinedChainDataClient combinedChainDataClient;

  public InMemoryStorageSystem(
      final EventBus eventBus,
      final TrackingReorgEventChannel reorgEventChannel,
      final MockRocksDbInstance rocksDbInstance,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient) {
    this.eventBus = eventBus;
    this.reorgEventChannel = reorgEventChannel;
    this.rocksDbInstance = rocksDbInstance;
    this.recentChainData = recentChainData;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  public static InMemoryStorageSystem createEmptyV3StorageSystem(
      final StateStorageMode storageMode) {
    final MockRocksDbInstance rocksDbInstance =
        InMemoryRocksDbDatabaseFactory.createEmptyV3RocksDbInstance();
    return createV3StorageSystem(rocksDbInstance, storageMode);
  }

  private static InMemoryStorageSystem createV3StorageSystem(
      final MockRocksDbInstance rocksDbInstance, final StateStorageMode storageMode) {
    try {
      final EventBus eventBus = new EventBus();
      final Database database =
          InMemoryRocksDbDatabaseFactory.createV3(rocksDbInstance, storageMode);
      final DatabaseFactory dbFactory = () -> database;

      // Create and start storage server
      final ChainStorageServer chainStorageServer = ChainStorageServer.create(eventBus, dbFactory);
      chainStorageServer.start();

      // Create recent chain data
      final FinalizedCheckpointChannel finalizedCheckpointChannel =
          new StubFinalizedCheckpointChannel();
      final TrackingReorgEventChannel reorgEventChannel = new TrackingReorgEventChannel();
      final RecentChainData recentChainData =
          StorageBackedRecentChainData.createImmediately(
              chainStorageServer, finalizedCheckpointChannel, reorgEventChannel, eventBus);

      // Create combined client
      final CombinedChainDataClient combinedChainDataClient =
          new CombinedChainDataClient(recentChainData, chainStorageServer);

      // Return storage system
      return new InMemoryStorageSystem(
          eventBus, reorgEventChannel, rocksDbInstance, recentChainData, combinedChainDataClient);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to initialize storage system", e);
    }
  }

  public InMemoryStorageSystem restarted(final StateStorageMode storageMode) {
    final MockRocksDbInstance restartedRocksDbInstance = rocksDbInstance.reopen();
    return createV3StorageSystem(restartedRocksDbInstance, storageMode);
  }

  public RecentChainData recentChainData() {
    return recentChainData;
  }

  public CombinedChainDataClient combinedChainDataClient() {
    return combinedChainDataClient;
  }

  public EventBus getEventBus() {
    return eventBus;
  }

  public TrackingReorgEventChannel getReorgEventChannel() {
    return reorgEventChannel;
  }
}
