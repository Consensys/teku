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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.server.ChainStorage;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.ProtoArrayStorage;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;

public class StorageService extends Service {
  private final StorageConfiguration config;
  private volatile ChainStorage chainStorage;
  private volatile ProtoArrayStorage protoArrayStorage;
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
                  Optional.empty(),
                  config.getDataStorageMode(),
                  config.getDataStorageCreateDbVersion(),
                  config.getDataStorageFrequency(),
                  config.getEth1DepositContract());
          database = dbFactory.createDatabase();

          chainStorage = ChainStorage.create(serviceConfig.getEventBus(), database);
          final DepositStorage depositStorage =
              DepositStorage.create(
                  serviceConfig.getEventChannels().getPublisher(Eth1EventsChannel.class), database);
          protoArrayStorage = new ProtoArrayStorage(database);

          serviceConfig
              .getEventChannels()
              .subscribe(Eth1DepositStorageChannel.class, depositStorage)
              .subscribe(Eth1EventsChannel.class, depositStorage)
              .subscribe(StorageUpdateChannel.class, chainStorage)
              .subscribe(ProtoArrayStorageChannel.class, protoArrayStorage)
              .subscribeMultithreaded(
                  StorageQueryChannel.class, chainStorage, STORAGE_QUERY_CHANNEL_PARALLELISM);

          chainStorage.start();
        });
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.fromRunnable(
        () -> {
          chainStorage.stop();
          database.close();
        });
  }
}
