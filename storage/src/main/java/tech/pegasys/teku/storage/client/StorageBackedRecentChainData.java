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

import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;

public class StorageBackedRecentChainData extends RecentChainData {
  private final BlockProvider blockProvider;

  public StorageBackedRecentChainData(
      final MetricsSystem metricsSystem,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ReorgEventChannel reorgEventChannel,
      final EventBus eventBus) {
    super(
        metricsSystem,
        storageQueryChannel::getHotBlocksByRoot,
        storageUpdateChannel,
        finalizedCheckpointChannel,
        reorgEventChannel,
        eventBus);
    this.blockProvider = storageQueryChannel::getHotBlocksByRoot;
    eventBus.register(this);
  }

  public static SafeFuture<RecentChainData> create(
      final MetricsSystem metricsSystem,
      final AsyncRunner asyncRunner,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ReorgEventChannel reorgEventChannel,
      final EventBus eventBus) {
    StorageBackedRecentChainData client =
        new StorageBackedRecentChainData(
            metricsSystem,
            storageQueryChannel,
            storageUpdateChannel,
            finalizedCheckpointChannel,
            reorgEventChannel,
            eventBus);

    return client.initializeFromStorageWithRetry(asyncRunner);
  }

  @VisibleForTesting
  public static RecentChainData createImmediately(
      final MetricsSystem metricsSystem,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ReorgEventChannel reorgEventChannel,
      final EventBus eventBus) {
    StorageBackedRecentChainData client =
        new StorageBackedRecentChainData(
            metricsSystem,
            storageQueryChannel,
            storageUpdateChannel,
            finalizedCheckpointChannel,
            reorgEventChannel,
            eventBus);

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
        maybeStore -> {
          maybeStore
              .map(builder -> builder.blockProvider(blockProvider).build())
              .ifPresent(this::setStore);
          STATUS_LOG.finishInitializingChainData();
          return this;
        });
  }

  private SafeFuture<Optional<StoreBuilder>> requestInitialStore() {
    return storageUpdateChannel
        .onStoreRequest()
        .orTimeout(Constants.STORAGE_REQUEST_TIMEOUT, TimeUnit.SECONDS);
  }

  private SafeFuture<Optional<StoreBuilder>> requestInitialStoreWithRetry(
      final AsyncRunner asyncRunner) {
    return requestInitialStore()
        .exceptionallyCompose(
            (err) ->
                asyncRunner.runAfterDelay(
                    () -> requestInitialStoreWithRetry(asyncRunner),
                    Constants.STORAGE_REQUEST_TIMEOUT,
                    TimeUnit.SECONDS));
  }
}
