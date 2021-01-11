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

import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import com.google.common.eventbus.EventBus;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.pow.api.TrackingEth1EventsChannel;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.server.ChainStorage;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.ProtoArrayStorage;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.util.config.StateStorageMode;

public class StorageSystem implements AutoCloseable {
  private final ChainBuilder chainBuilder = ChainBuilder.createDefault();
  private final ChainUpdater chainUpdater;
  private final TrackingEth1EventsChannel eth1EventsChannel = new TrackingEth1EventsChannel();

  private final EventBus eventBus;
  private final TrackingChainHeadChannel reorgEventChannel;
  private final StubMetricsSystem metricsSystem;
  private final RecentChainData recentChainData;
  private final StateStorageMode storageMode;
  private final CombinedChainDataClient combinedChainDataClient;
  private final ChainStorage chainStorage;
  private final Database database;
  private final RestartedStorageSupplier restartedSupplier;

  private StorageSystem(
      final StubMetricsSystem metricsSystem,
      final EventBus eventBus,
      final TrackingChainHeadChannel reorgEventChannel,
      final StateStorageMode storageMode,
      final ChainStorage chainStorage,
      final Database database,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final RestartedStorageSupplier restartedSupplier) {
    this.metricsSystem = metricsSystem;
    this.chainStorage = chainStorage;
    this.recentChainData = recentChainData;
    this.eventBus = eventBus;
    this.reorgEventChannel = reorgEventChannel;
    this.storageMode = storageMode;
    this.database = database;
    this.combinedChainDataClient = combinedChainDataClient;
    this.restartedSupplier = restartedSupplier;

    chainUpdater = new ChainUpdater(this.recentChainData, chainBuilder);
  }

  static StorageSystem create(
      final Database database,
      final RestartedStorageSupplier restartedSupplier,
      final StateStorageMode storageMode,
      final StoreConfig storeConfig) {
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();
    final EventBus eventBus = new EventBus();

    // Create and start storage server
    final ChainStorage chainStorageServer = ChainStorage.create(eventBus, database);
    chainStorageServer.start();

    // Create recent chain data
    final FinalizedCheckpointChannel finalizedCheckpointChannel =
        new StubFinalizedCheckpointChannel();
    final TrackingChainHeadChannel reorgEventChannel = new TrackingChainHeadChannel();
    final RecentChainData recentChainData =
        StorageBackedRecentChainData.createImmediately(
            SYNC_RUNNER,
            metricsSystem,
            storeConfig,
            chainStorageServer,
            chainStorageServer,
            ProtoArrayStorageChannel.NO_OP,
            finalizedCheckpointChannel,
            reorgEventChannel,
            eventBus);

    // Create combined client
    final CombinedChainDataClient combinedChainDataClient =
        new CombinedChainDataClient(recentChainData, chainStorageServer);

    // Return storage system
    return new StorageSystem(
        metricsSystem,
        eventBus,
        reorgEventChannel,
        storageMode,
        chainStorageServer,
        database,
        recentChainData,
        combinedChainDataClient,
        restartedSupplier);
  }

  public StubMetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  public RecentChainData recentChainData() {
    return recentChainData;
  }

  public ChainBuilder chainBuilder() {
    return chainBuilder;
  }

  public ChainUpdater chainUpdater() {
    return chainUpdater;
  }

  public DepositStorage createDepositStorage() {
    return DepositStorage.create(eth1EventsChannel, database);
  }

  public ProtoArrayStorage createProtoArrayStorage() {
    return new ProtoArrayStorage(database);
  }

  public Database database() {
    return database;
  }

  public ChainStorage chainStorage() {
    return chainStorage;
  }

  public StorageSystem restarted() {
    return restarted(storageMode);
  }

  public StorageSystem restarted(final StateStorageMode storageMode) {
    try {
      database.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return restartedSupplier.restart(storageMode);
  }

  public CombinedChainDataClient combinedChainDataClient() {
    return combinedChainDataClient;
  }

  public EventBus eventBus() {
    return eventBus;
  }

  public TrackingChainHeadChannel reorgEventChannel() {
    return reorgEventChannel;
  }

  public TrackingEth1EventsChannel eth1EventsChannel() {
    return eth1EventsChannel;
  }

  @Override
  public void close() throws Exception {
    this.database.close();
  }

  public interface RestartedStorageSupplier {
    StorageSystem restart(final StateStorageMode storageMode);
  }
}
