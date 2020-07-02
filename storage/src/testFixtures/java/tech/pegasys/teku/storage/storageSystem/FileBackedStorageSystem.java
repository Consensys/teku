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
import java.nio.file.Path;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.metrics.StubMetricsSystem;
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
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabase;
import tech.pegasys.teku.util.config.StateStorageMode;

public class FileBackedStorageSystem extends AbstractStorageSystem {

  private final EventBus eventBus;
  private final TrackingReorgEventChannel reorgEventChannel;
  private final TrackingEth1EventsChannel eth1EventsChannel = new TrackingEth1EventsChannel();

  private final StateStorageMode storageMode;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Database database;
  private final RestartedStorageSupplier restartedSupplier;

  public FileBackedStorageSystem(
      final EventBus eventBus,
      final TrackingReorgEventChannel reorgEventChannel,
      final StateStorageMode storageMode,
      final Database database,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final RestartedStorageSupplier restartedSupplier) {
    super(recentChainData);

    this.eventBus = eventBus;
    this.reorgEventChannel = reorgEventChannel;
    this.storageMode = storageMode;
    this.database = database;
    this.combinedChainDataClient = combinedChainDataClient;
    this.restartedSupplier = restartedSupplier;
  }

  public static StorageSystem createV5StorageSystem(
      final Path dataDir, final StateStorageMode storageMode, final long stateStorageFrequency) {
    return createV5StorageSystem(
        dataDir.resolve("hot"), dataDir.resolve("archive"), storageMode, stateStorageFrequency);
  }

  public static StorageSystem createV5StorageSystem(
      final Path hotDir,
      final Path archiveDir,
      final StateStorageMode storageMode,
      final long stateStorageFrequency) {
    final Database database =
        RocksDbDatabase.createV4(
            new StubMetricsSystem(),
            RocksDbConfiguration.v5HotDefaults().withDatabaseDir(hotDir),
            RocksDbConfiguration.v5ArchiveDefaults().withDatabaseDir(archiveDir),
            storageMode,
            stateStorageFrequency);
    return create(
        database,
        (mode) -> createV5StorageSystem(hotDir, archiveDir, mode, stateStorageFrequency),
        storageMode);
  }

  public static StorageSystem createV4StorageSystem(
      final Path dataDir, final StateStorageMode storageMode, final long stateStorageFrequency) {
    return createV4StorageSystem(
        dataDir.resolve("hot"), dataDir.resolve("archive"), storageMode, stateStorageFrequency);
  }

  public static StorageSystem createV4StorageSystem(
      final Path hotDir,
      final Path archiveDir,
      final StateStorageMode storageMode,
      final long stateStorageFrequency) {
    final Database database =
        RocksDbDatabase.createV4(
            new StubMetricsSystem(),
            RocksDbConfiguration.v3And4Settings(hotDir),
            RocksDbConfiguration.v3And4Settings(archiveDir),
            storageMode,
            stateStorageFrequency);
    return create(
        database,
        (mode) -> createV4StorageSystem(hotDir, archiveDir, mode, stateStorageFrequency),
        storageMode);
  }

  public static StorageSystem createV3StorageSystem(
      final Path dataPath, final StateStorageMode storageMode) {
    final RocksDbConfiguration rocksDbConfiguration = RocksDbConfiguration.v3And4Settings(dataPath);
    final Database database =
        RocksDbDatabase.createV3(new StubMetricsSystem(), rocksDbConfiguration, storageMode);
    return create(database, (mode) -> createV3StorageSystem(dataPath, mode), storageMode);
  }

  private static StorageSystem create(
      final Database database,
      final RestartedStorageSupplier restartedSupplier,
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
    return new FileBackedStorageSystem(
        eventBus,
        reorgEventChannel,
        storageMode,
        database,
        recentChainData,
        combinedChainDataClient,
        restartedSupplier);
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
    return restartedSupplier.restart(storageMode);
  }

  @Override
  public RecentChainData recentChainData() {
    return recentChainData;
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
    this.database.close();
  }
}
