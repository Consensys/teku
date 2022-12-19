/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.spec.config.Constants.STORAGE_QUERY_CHANNEL_PARALLELISM;

import java.util.Optional;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.server.BatchingVoteUpdateChannel;
import tech.pegasys.teku.storage.server.ChainStorage;
import tech.pegasys.teku.storage.server.CombinedStorageChannelSplitter;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.RetryingStorageUpdateChannel;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.storage.server.pruner.BlobsPruner;
import tech.pegasys.teku.storage.server.pruner.BlockPruner;

public class StorageService extends Service implements StorageServiceFacade {
  private final StorageConfiguration config;
  private volatile ChainStorage chainStorage;
  private final ServiceConfig serviceConfig;
  private volatile Database database;
  private volatile BatchingVoteUpdateChannel batchingVoteUpdateChannel;
  private volatile Optional<BlockPruner> blockPruner = Optional.empty();
  private volatile Optional<BlobsPruner> blobsPruner = Optional.empty();
  private final boolean depositSnapshotStorageEnabled;

  public StorageService(
      final ServiceConfig serviceConfig,
      final StorageConfiguration storageConfiguration,
      final boolean depositSnapshotStorageEnabled) {
    this.serviceConfig = serviceConfig;
    this.config = storageConfiguration;
    this.depositSnapshotStorageEnabled = depositSnapshotStorageEnabled;
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.fromRunnable(
            () -> {
              final AsyncRunner storagePrunerAsyncRunner =
                  serviceConfig.createAsyncRunner("storagePrunerAsyncRunner", 1);
              final VersionedDatabaseFactory dbFactory =
                  new VersionedDatabaseFactory(
                      serviceConfig.getMetricsSystem(),
                      serviceConfig.getDataDirLayout().getBeaconDataDirectory(),
                      config);
              database = dbFactory.createDatabase();

              database.migrate();

              if (!config.getDataStorageMode().storesAllBlocks()) {
                blockPruner =
                    Optional.of(
                        new BlockPruner(
                            config.getSpec(),
                            database,
                            storagePrunerAsyncRunner,
                            config.getBlockPruningInterval()));
              }
              if (config.getSpec().isMilestoneSupported(SpecMilestone.EIP4844)) {
                blobsPruner =
                    Optional.of(
                        new BlobsPruner(
                            config.getSpec(),
                            database,
                            storagePrunerAsyncRunner,
                            serviceConfig.getTimeProvider(),
                            config.getBlobsPruningInterval(),
                            config.getBlobsPruningLimit()));
              }
              final EventChannels eventChannels = serviceConfig.getEventChannels();
              chainStorage = ChainStorage.create(database, config.getSpec());
              final DepositStorage depositStorage =
                  DepositStorage.create(
                      eventChannels.getPublisher(Eth1EventsChannel.class),
                      database,
                      depositSnapshotStorageEnabled);

              batchingVoteUpdateChannel =
                  new BatchingVoteUpdateChannel(
                      chainStorage,
                      new AsyncRunnerEventThread(
                          "batch-vote-updater", serviceConfig.getAsyncRunnerFactory()));

              eventChannels.subscribe(
                  CombinedStorageChannel.class,
                  new CombinedStorageChannelSplitter(
                      serviceConfig.createAsyncRunner(
                          "storage_query", STORAGE_QUERY_CHANNEL_PARALLELISM),
                      new RetryingStorageUpdateChannel(
                          chainStorage, serviceConfig.getTimeProvider()),
                      chainStorage));

              eventChannels
                  .subscribe(Eth1DepositStorageChannel.class, depositStorage)
                  .subscribe(Eth1EventsChannel.class, depositStorage)
                  .subscribe(VoteUpdateChannel.class, batchingVoteUpdateChannel);
            })
        .thenCompose(
            __ ->
                blockPruner
                    .map(BlockPruner::start)
                    .orElseGet(() -> SafeFuture.completedFuture(null)))
        .thenCompose(
            __ ->
                blobsPruner
                    .map(BlobsPruner::start)
                    .orElseGet(() -> SafeFuture.completedFuture(null)));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return blockPruner
        .map(BlockPruner::stop)
        .orElseGet(() -> SafeFuture.completedFuture(null))
        .thenCompose(__ -> SafeFuture.fromRunnable(database::close));
  }

  @Override
  public ChainStorage getChainStorage() {
    return chainStorage;
  }
}
