/*
 * Copyright Consensys Software Inc., 2022
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
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlobSidecarProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.SidecarUpdateChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;

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
      final TimeProvider timeProvider,
      final SingleBlockProvider validatedBlockProvider,
      final SingleBlobSidecarProvider validatedBlobSidecarProvider,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel,
      final VoteUpdateChannel voteUpdateChannel,
      final SidecarUpdateChannel sidecarUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final ValidatorIsConnectedProvider validatorIsConnectedProvider,
      final Spec spec) {
    super(
        asyncRunner,
        metricsSystem,
        storeConfig,
        storageQueryChannel::getHotBlocksByRoot,
        validatedBlockProvider,
        validatedBlobSidecarProvider,
        storageQueryChannel::getHotStateAndBlockSummaryByBlockRoot,
        storageQueryChannel::getEarliestAvailableBlobSidecarSlot,
        storageUpdateChannel,
        voteUpdateChannel,
        sidecarUpdateChannel,
        finalizedCheckpointChannel,
        chainHeadChannel,
        validatorIsConnectedProvider,
        spec);
    this.storeConfig = storeConfig;
    this.storageQueryChannel = storageQueryChannel;
    this.blockProvider = storageQueryChannel::getHotBlocksByRoot;
    this.stateProvider = storageQueryChannel::getHotStateAndBlockSummaryByBlockRoot;
  }

  public static SafeFuture<RecentChainData> create(
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final SingleBlockProvider validatedBlockProvider,
      final SingleBlobSidecarProvider validatedBlobSidecarProvider,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel,
      final VoteUpdateChannel voteUpdateChannel,
      final SidecarUpdateChannel sidecarUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final ValidatorIsConnectedProvider validatorIsConnectedProvider,
      final Spec spec) {
    StorageBackedRecentChainData client =
        new StorageBackedRecentChainData(
            asyncRunner,
            metricsSystem,
            storeConfig,
            timeProvider,
            validatedBlockProvider,
            validatedBlobSidecarProvider,
            storageQueryChannel,
            storageUpdateChannel,
            voteUpdateChannel,
            sidecarUpdateChannel,
            finalizedCheckpointChannel,
            chainHeadChannel,
            validatorIsConnectedProvider,
            spec);

    return client.initializeFromStorageWithRetry(asyncRunner);
  }

  @VisibleForTesting
  public static RecentChainData createImmediately(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final TimeProvider timeProvider,
      final SingleBlockProvider validatedBlockProvider,
      final SingleBlobSidecarProvider validatedBlobSidecarProvider,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel,
      final VoteUpdateChannel voteUpdateChannel,
      final SidecarUpdateChannel sidecarUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final ValidatorIsConnectedProvider validatorIsConnectedProvider,
      final Spec spec) {
    StorageBackedRecentChainData client =
        new StorageBackedRecentChainData(
            asyncRunner,
            metricsSystem,
            storeConfig,
            timeProvider,
            validatedBlockProvider,
            validatedBlobSidecarProvider,
            storageQueryChannel,
            storageUpdateChannel,
            voteUpdateChannel,
            sidecarUpdateChannel,
            finalizedCheckpointChannel,
            chainHeadChannel,
            validatorIsConnectedProvider,
            spec);

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
      final SafeFuture<Optional<OnDiskStoreData>> storeFuture) {
    return storeFuture.thenApply(
        maybeData -> {
          if (maybeData.isEmpty()) {
            STATUS_LOG.finishInitializingChainData();
            return this;
          }

          final OnDiskStoreData data = maybeData.get();

          final UpdatableStore store =
              StoreBuilder.create()
                  .metricsSystem(metricsSystem)
                  .specProvider(spec)
                  .onDiskStoreData(data)
                  .asyncRunner(asyncRunner)
                  .blockProvider(blockProvider)
                  .stateProvider(stateProvider)
                  .storeConfig(storeConfig)
                  .build();
          setStore(store);
          STATUS_LOG.finishInitializingChainData();
          return this;
        });
  }

  private SafeFuture<Optional<OnDiskStoreData>> requestInitialStore() {
    return storageQueryChannel.onStoreRequest().orTimeout(Constants.STORAGE_REQUEST_TIMEOUT);
  }

  private SafeFuture<Optional<OnDiskStoreData>> requestInitialStoreWithRetry(
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
