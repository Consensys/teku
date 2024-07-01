/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.services.beaconchain.init;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.services.beaconchain.SlotProcessor;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.services.beaconchain.init.BeaconModule.GenesisTimeTracker;
import tech.pegasys.teku.services.beaconchain.init.SpecModule.CurrentSlotProvider;
import tech.pegasys.teku.services.beaconchain.init.WSModule.WeakSubjectivityFinalizedConfig;
import tech.pegasys.teku.services.beaconchain.init.WSModule.WeakSubjectivityPeriodValidator;
import tech.pegasys.teku.services.beaconchain.init.WSModule.WeakSubjectivityStoreChainValidator;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.EarliestAvailableBlockSlot;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.client.ValidatorIsConnectedProvider;
import tech.pegasys.teku.storage.store.FileKeyValueStore;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

@Module
public interface StorageModule {

  String KEY_VALUE_STORE_SUBDIRECTORY = "kvstore";

  interface OnStoreInitializedHandler {
    void handle();
  }

  @Binds
  StorageQueryChannel bindStorageQueryChannel(CombinedStorageChannel combinedStorageChannel);

  @Binds
  StorageUpdateChannel bindStorageUpdateChannel(CombinedStorageChannel combinedStorageChannel);

  @Provides
  @Singleton
  static KeyValueStore<String, Bytes> keyValueStore(final DataDirLayout dataDirLayout) {
    return new FileKeyValueStore(
        dataDirLayout.getBeaconDataDirectory().resolve(KEY_VALUE_STORE_SUBDIRECTORY));
  }

  @Provides
  @Singleton
  static EarliestAvailableBlockSlot earliestAvailableBlockSlot(
      final StoreConfig storeConfig,
      final TimeProvider timeProvider,
      final StorageQueryChannel storageQueryChannel) {
    return new EarliestAvailableBlockSlot(
        storageQueryChannel, timeProvider, storeConfig.getEarliestAvailableBlockSlotFrequency());
  }

  @Provides
  @Singleton
  static CombinedChainDataClient combinedChainDataClient(
      final Spec spec,
      final StorageQueryChannel storageQueryChannel,
      final RecentChainData recentChainData,
      final EarliestAvailableBlockSlot earliestAvailableBlockSlot) {
    return new CombinedChainDataClient(
        recentChainData, storageQueryChannel, spec, earliestAvailableBlockSlot);
  }

  @Provides
  @Singleton
  @SuppressWarnings("UnusedVariable")
  static SafeFuture<RecentChainData> recentChainDataFuture(
      @BeaconAsyncRunner final AsyncRunner beaconAsyncRunner,
      final TimeProvider timeProvider,
      final MetricsSystem metricsSystem,
      final Spec spec,
      final StoreConfig storeConfig,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel,
      final Lazy<BlockBlobSidecarsTrackersPool> blockBlobSidecarsTrackersPool,
      final VoteUpdateChannel voteUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final ValidatorIsConnectedProvider validatorIsConnectedProvider,
      // TODO is there a better option for this dependency ?
      final WeakSubjectivityFinalizedConfig __) {

    return StorageBackedRecentChainData.create(
        metricsSystem,
        storeConfig,
        beaconAsyncRunner,
        timeProvider,
        blockRoot -> blockBlobSidecarsTrackersPool.get().getBlock(blockRoot),
        (blockRoot, index) -> blockBlobSidecarsTrackersPool.get().getBlobSidecar(blockRoot, index),
        storageQueryChannel,
        storageUpdateChannel,
        voteUpdateChannel,
        finalizedCheckpointChannel,
        chainHeadChannel,
        validatorIsConnectedProvider,
        spec);
  }

  @Provides
  @Singleton
  // TODO producer ?
  static RecentChainData recentChainData(
      final Eth2NetworkConfiguration eth2NetworkConfig,
      final SafeFuture<RecentChainData> recentChainDataFuture,
      final StatusLogger statusLogger,
      final Lazy<WeakSubjectivityPeriodValidator> weakSubjectivityPeriodValidator,
      final Lazy<RecentChainDataStateInitializer> recentChainDataStateInitializer) {

    RecentChainData recentChainData = recentChainDataFuture.join();

    boolean isAllowSyncOutsideWeakSubjectivityPeriod =
        eth2NetworkConfig.getNetworkBoostrapConfig().isAllowSyncOutsideWeakSubjectivityPeriod();
    boolean isUsingCustomInitialState =
        eth2NetworkConfig.getNetworkBoostrapConfig().isUsingCustomInitialState();

    if (isAllowSyncOutsideWeakSubjectivityPeriod) {
      statusLogger.warnIgnoringWeakSubjectivityPeriod();
    }

    // Setup chain storage
    if (recentChainData.isPreGenesis()) {
      recentChainDataStateInitializer.get().setupInitialState(recentChainData);
    } else {
      if (isUsingCustomInitialState) {
        statusLogger.warnInitialStateIgnored();
      }
      if (!isAllowSyncOutsideWeakSubjectivityPeriod) {
        weakSubjectivityPeriodValidator.get().validate(recentChainData);
      }
    }

    return recentChainData;
  }

  @Provides
  @Singleton
  static OnStoreInitializedHandler onStoreInitializedHandler(
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final CurrentSlotProvider currentSlotProvider,
      final Lazy<WeakSubjectivityStoreChainValidator> weakSubjectivityStoreChainValidator,
      final Lazy<GenesisTimeTracker> genesisTimeTracker,
      final SlotProcessor slotProcessor,
      final PerformanceTracker performanceTracker) {
    return () -> {
      UInt64 genesisTime = recentChainData.getGenesisTime();
      UInt64 currentTime = timeProvider.getTimeInSeconds();
      final UInt64 currentSlot = currentSlotProvider.getCurrentSlot(currentTime, genesisTime);
      if (currentTime.compareTo(genesisTime) >= 0) {
        // Validate that we're running within the weak subjectivity period
        weakSubjectivityStoreChainValidator.get().validate(currentSlot);
      } else {
        genesisTimeTracker.get().update();
      }
      slotProcessor.setCurrentSlot(currentSlot);
      performanceTracker.start(currentSlot);
    };
  }
}
