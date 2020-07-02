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
import tech.pegasys.teku.storage.server.ProtoArrayStorage;
import tech.pegasys.teku.storage.server.rocksdb.InMemoryRocksDbDatabaseFactory;
import tech.pegasys.teku.storage.server.rocksdb.core.MockRocksDbInstance;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaFinalized;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaHot;
import tech.pegasys.teku.util.config.StateStorageMode;

public class InMemoryStorageSystem extends AbstractStorageSystem {
  private final EventBus eventBus;
  private final TrackingReorgEventChannel reorgEventChannel;
  private final TrackingEth1EventsChannel eth1EventsChannel = new TrackingEth1EventsChannel();

  private final StateStorageMode storageMode;
  private final Database database;
  private final CombinedChainDataClient combinedChainDataClient;
  private final RestartedStorageSupplier restartedStorageSupplier;

  public InMemoryStorageSystem(
      final EventBus eventBus,
      final TrackingReorgEventChannel reorgEventChannel,
      final StateStorageMode storageMode,
      final Database database,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final RestartedStorageSupplier restartedStorageSupplier) {
    super(recentChainData);

    this.eventBus = eventBus;
    this.reorgEventChannel = reorgEventChannel;
    this.storageMode = storageMode;
    this.database = database;
    this.combinedChainDataClient = combinedChainDataClient;
    this.restartedStorageSupplier = restartedStorageSupplier;
  }

  // V5 only differs by the RocksDB configuration which doesn't apply to the in-memory version
  public static StorageSystem createEmptyV5StorageSystem(
      final StateStorageMode storageMode, final long stateStorageFrequency) {
    return createEmptyV4StorageSystem(storageMode, stateStorageFrequency);
  }

  public static StorageSystem createEmptyV4StorageSystem(
      final StateStorageMode storageMode, final long stateStorageFrequency) {

    final MockRocksDbInstance hotDb = MockRocksDbInstance.createEmpty(V4SchemaHot.class);
    final MockRocksDbInstance coldDb = MockRocksDbInstance.createEmpty(V4SchemaFinalized.class);
    return createV4(hotDb, coldDb, storageMode, stateStorageFrequency);
  }

  public static StorageSystem createEmptyV3StorageSystem(final StateStorageMode storageMode) {
    final MockRocksDbInstance rocksDbInstance =
        InMemoryRocksDbDatabaseFactory.createEmptyV3RocksDbInstance();
    return createV3(rocksDbInstance, storageMode);
  }

  private static StorageSystem createV4(
      final MockRocksDbInstance hotDb,
      final MockRocksDbInstance coldDb,
      final StateStorageMode storageMode,
      final long stateStorageFrequency) {
    final Database database =
        InMemoryRocksDbDatabaseFactory.createV4(hotDb, coldDb, storageMode, stateStorageFrequency);
    final RestartedStorageSupplier restartedStorageSupplier =
        (mode) -> createV4(hotDb.reopen(), coldDb.reopen(), mode, stateStorageFrequency);
    return create(database, restartedStorageSupplier, storageMode);
  }

  private static StorageSystem createV3(
      final MockRocksDbInstance rocksDbInstance, final StateStorageMode storageMode) {
    final Database database = InMemoryRocksDbDatabaseFactory.createV3(rocksDbInstance, storageMode);
    final RestartedStorageSupplier restartedStorageSupplier =
        (mode) -> createV3(rocksDbInstance.reopen(), mode);
    return create(database, restartedStorageSupplier, storageMode);
  }

  private static StorageSystem create(
      final Database database,
      final RestartedStorageSupplier restartedStorageSupplier,
      final StateStorageMode storageMode) {
    final EventBus eventBus = new EventBus();

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
        storageMode,
        database,
        recentChainData,
        combinedChainDataClient,
        restartedStorageSupplier);
  }

  @Override
  public DepositStorage createDepositStorage(final boolean eth1DepositsFromStorageEnabled) {
    return DepositStorage.create(eth1EventsChannel, database, eth1DepositsFromStorageEnabled);
  }

  @Override
  public ProtoArrayStorage createProtoArrayStorage() {
    return new ProtoArrayStorage(database);
  }

  @Override
  public Database getDatabase() {
    return database;
  }

  @Override
  public StorageSystem restarted() {
    return restarted(storageMode);
  }

  @Override
  public StorageSystem restarted(final StateStorageMode storageMode) {
    try {
      database.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return restartedStorageSupplier.restart(storageMode);
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

  @Override
  public void close() throws Exception {
    database.close();
  }
}
