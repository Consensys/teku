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

package tech.pegasys.teku.storage.storageSystem;

import com.google.common.eventbus.EventBus;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.pow.api.TrackingEth1EventsChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.TrackingReorgEventChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.server.ChainStorage;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.rocksdb.InMemoryRocksDbDatabaseFactory;
import tech.pegasys.teku.storage.server.rocksdb.core.MockRocksDbInstance;
import tech.pegasys.teku.util.config.StateStorageMode;

public class InMemoryStorageSystem extends AbstractStorageSystem implements StorageSystem {
  private final EventBus eventBus;
  private final TrackingReorgEventChannel reorgEventChannel;
  private final TrackingEth1EventsChannel eth1EventsChannel = new TrackingEth1EventsChannel();

  private final Database database;
  private final MockRocksDbInstance rocksDbInstance;
  private final CombinedChainDataClient combinedChainDataClient;

  public InMemoryStorageSystem(
      final EventBus eventBus,
      final TrackingReorgEventChannel reorgEventChannel,
      final Database database,
      final MockRocksDbInstance rocksDbInstance,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient) {
    super(recentChainData);

    this.eventBus = eventBus;
    this.reorgEventChannel = reorgEventChannel;
    this.database = database;
    this.rocksDbInstance = rocksDbInstance;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  public static StorageSystem createEmptyV3StorageSystem(final StateStorageMode storageMode) {
    final MockRocksDbInstance rocksDbInstance =
        InMemoryRocksDbDatabaseFactory.createEmptyV3RocksDbInstance();
    return createV3StorageSystem(rocksDbInstance, storageMode);
  }

  private static StorageSystem createV3StorageSystem(
      final MockRocksDbInstance rocksDbInstance, final StateStorageMode storageMode) {
    try {
      final EventBus eventBus = new EventBus();
      final Database database =
          InMemoryRocksDbDatabaseFactory.createV3(rocksDbInstance, storageMode);

      // Create and start storage server
      final ChainStorage chainStorageServer = ChainStorage.create(eventBus, database);
      chainStorageServer.start();

      // Create recent chain data
      final FinalizedCheckpointChannel finalizedCheckpointChannel =
          new StubFinalizedCheckpointChannel();
      final TrackingReorgEventChannel reorgEventChannel = new TrackingReorgEventChannel();
      final RecentChainData recentChainData =
          StorageBackedRecentChainData.createImmediately(
              new NoOpMetricsSystem(),
              chainStorageServer,
              finalizedCheckpointChannel,
              reorgEventChannel,
              eventBus);

      // Create combined client
      final CombinedChainDataClient combinedChainDataClient =
          new CombinedChainDataClient(recentChainData, chainStorageServer);

      // Return storage system
      return new InMemoryStorageSystem(
          eventBus,
          reorgEventChannel,
          database,
          rocksDbInstance,
          recentChainData,
          combinedChainDataClient);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to initialize storage system", e);
    }
  }

  @Override
  public DepositStorage createDepositStorage(final boolean eth1DepositsFromStorageEnabled) {
    return DepositStorage.create(eth1EventsChannel, database, eth1DepositsFromStorageEnabled);
  }

  @Override
  public Database getDatabase() {
    return database;
  }

  @Override
  public StorageSystem restarted(final StateStorageMode storageMode) {
    final MockRocksDbInstance restartedRocksDbInstance = rocksDbInstance.reopen();
    return createV3StorageSystem(restartedRocksDbInstance, storageMode);
  }

  @Override
  public CombinedChainDataClient combinedChainDataClient() {
    return combinedChainDataClient;
  }

  @Override
  public EventBus eventBus() {
    return eventBus;
  }

  @Override
  public TrackingReorgEventChannel reorgEventChannel() {
    return reorgEventChannel;
  }

  @Override
  public TrackingEth1EventsChannel eth1EventsChannel() {
    return eth1EventsChannel;
  }
}
