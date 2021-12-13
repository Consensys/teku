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

package tech.pegasys.teku.services.chainstorage;

import static tech.pegasys.teku.util.config.Constants.STORAGE_QUERY_CHANNEL_PARALLELISM;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.server.ChainStorage;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;

public class StorageService extends Service implements StorageServiceFacade {
  private final StorageConfiguration config;
  private volatile ChainStorage chainStorage;
  private final ServiceConfig serviceConfig;
  private volatile Database database;

  public StorageService(
      final ServiceConfig serviceConfig, final StorageConfiguration storageConfiguration) {
    this.serviceConfig = serviceConfig;
    this.config = storageConfiguration;
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.fromRunnable(
        () -> {
          final VersionedDatabaseFactory dbFactory =
              new VersionedDatabaseFactory(
                  serviceConfig.getMetricsSystem(),
                  serviceConfig.getDataDirLayout().getBeaconDataDirectory(),
                  config.getDataStorageMode(),
                  config.getDataStorageCreateDbVersion(),
                  config.getDataStorageFrequency(),
                  config.getEth1DepositContract(),
                  config.isStoreNonCanonicalBlocksEnabled(),
                  config.getMaxKnownNodeCacheSize(),
                  config.getSpec());
          database = dbFactory.createDatabase();

          chainStorage = ChainStorage.create(database, config.getSpec());
          final DepositStorage depositStorage =
              DepositStorage.create(
                  serviceConfig.getEventChannels().getPublisher(Eth1EventsChannel.class), database);

          serviceConfig
              .getEventChannels()
              .subscribe(Eth1DepositStorageChannel.class, depositStorage)
              .subscribe(Eth1EventsChannel.class, depositStorage)
              .subscribe(StorageUpdateChannel.class, chainStorage)
              .subscribe(VoteUpdateChannel.class, chainStorage)
              .subscribeMultithreaded(
                  StorageQueryChannel.class, chainStorage, STORAGE_QUERY_CHANNEL_PARALLELISM);
        });
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.fromRunnable(
        () -> {
          database.close();
        });
  }

  @Override
  public ChainStorage getChainStorage() {
    return chainStorage;
  }
}
