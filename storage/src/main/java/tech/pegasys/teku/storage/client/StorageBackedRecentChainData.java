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

package tech.pegasys.teku.storage.client;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.config.Constants;

public class StorageBackedRecentChainData extends RecentChainData {
  private static final Logger LOG = LogManager.getLogger();
  private final BlockProvider blockProvider;
  private final StateAndBlockSummaryProvider stateProvider;
  private final StorageQueryChannel storageQueryChannel;
  private final StoreConfig storeConfig;

  public StorageBackedRecentChainData(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel,
      final VoteUpdateChannel voteUpdateChannel,
      final ProtoArrayStorageChannel protoArrayStorageChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final EventBus eventBus,
      final SpecProvider specProvider) {
    super(
        asyncRunner,
        metricsSystem,
        storeConfig,
        storageQueryChannel::getHotBlocksByRoot,
        storageQueryChannel::getHotStateAndBlockSummaryByBlockRoot,
        storageUpdateChannel,
        voteUpdateChannel,
        protoArrayStorageChannel,
        finalizedCheckpointChannel,
        chainHeadChannel,
        eventBus,
        specProvider);
    this.storeConfig = storeConfig;
    this.storageQueryChannel = storageQueryChannel;
    this.blockProvider = storageQueryChannel::getHotBlocksByRoot;
    this.stateProvider = storageQueryChannel::getHotStateAndBlockSummaryByBlockRoot;
    eventBus.register(this);
  }

  public static SafeFuture<RecentChainData> create(
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final AsyncRunner asyncRunner,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel,
      final VoteUpdateChannel voteUpdateChannel,
      final ProtoArrayStorageChannel protoArrayStorageChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final EventBus eventBus,
      final SpecProvider specProvider) {
    StorageBackedRecentChainData client =
        new StorageBackedRecentChainData(
            asyncRunner,
            metricsSystem,
            storeConfig,
            storageQueryChannel,
            storageUpdateChannel,
            voteUpdateChannel,
            protoArrayStorageChannel,
            finalizedCheckpointChannel,
            chainHeadChannel,
            eventBus,
            specProvider);

    return client.initializeFromStorageWithRetry(asyncRunner);
  }

  @VisibleForTesting
  public static RecentChainData createImmediately(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel,
      final VoteUpdateChannel voteUpdateChannel,
      final ProtoArrayStorageChannel protoArrayStorageChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final EventBus eventBus,
      final SpecProvider specProvider) {
    StorageBackedRecentChainData client =
        new StorageBackedRecentChainData(
            asyncRunner,
            metricsSystem,
            storeConfig,
            storageQueryChannel,
            storageUpdateChannel,
            voteUpdateChannel,
            protoArrayStorageChannel,
            finalizedCheckpointChannel,
            chainHeadChannel,
            eventBus,
            specProvider);

    return client.initializeFromStorage().join();
  }

  private SafeFuture<RecentChainData> initializeFromStorage() {
    STATUS_LOG.beginInitializingChainData();
    return processStoreFuture(requestInitialStore());
  }

  private SafeFuture<RecentChainData> initializeFromStorageWithRetry(
      final AsyncRunner asyncRunner) {
    STATUS_LOG.beginInitializingChainData();
    return processStoreFuture(requestInitialStoreWithRetry(asyncRunner));
  }

  private SafeFuture<RecentChainData> processStoreFuture(
      SafeFuture<Optional<StoreBuilder>> storeFuture) {
    return storeFuture.thenApply(
        maybeStoreBuilder -> {
          if (maybeStoreBuilder.isEmpty()) {
            STATUS_LOG.finishInitializingChainData();
            return this;
          }

          final UpdatableStore store =
              maybeStoreBuilder
                  .get()
                  .asyncRunner(asyncRunner)
                  .blockProvider(blockProvider)
                  .stateProvider(stateProvider)
                  .storeConfig(storeConfig)
                  .protoArrayStorageChannel(protoArrayStorageChannel)
                  .build();
          setStore(store);
          STATUS_LOG.finishInitializingChainData();
          return this;
        });
  }

  private SafeFuture<Optional<StoreBuilder>> requestInitialStore() {
    return storageQueryChannel.onStoreRequest().orTimeout(Constants.STORAGE_REQUEST_TIMEOUT);
  }

  private SafeFuture<Optional<StoreBuilder>> requestInitialStoreWithRetry(
      final AsyncRunner asyncRunner) {
    return requestInitialStore()
        .exceptionallyCompose(
            (err) -> {
              if (Throwables.getRootCause(err) instanceof TimeoutException) {
                LOG.trace("Storage initialization timed out, will retry.");
                return asyncRunner.runAfterDelay(
                    () -> requestInitialStoreWithRetry(asyncRunner),
                    Constants.STORAGE_REQUEST_TIMEOUT);
              } else {
                STATUS_LOG.fatalErrorInitialisingStorage(err);
                return SafeFuture.failedFuture(err);
              }
            });
  }
}
