package tech.pegasys.teku.services.beaconchain.init;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.EarliestAvailableBlockSlot;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.FileKeyValueStore;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.StoreConfig;

import javax.inject.Singleton;

@Module
public interface StorageModule {

  String KEY_VALUE_STORE_SUBDIRECTORY = "kvstore";

  @Binds
  StorageQueryChannel bindStorageQueryChannel(CombinedStorageChannel combinedStorageChannel);

  @Binds
  StorageUpdateChannel bindStorageUpdateChannel(CombinedStorageChannel combinedStorageChannel);

  @Provides
  @Singleton
  static KeyValueStore<String, Bytes> keyValueStore(DataDirLayout dataDirLayout) {
    return new FileKeyValueStore(
        dataDirLayout.getBeaconDataDirectory().resolve(KEY_VALUE_STORE_SUBDIRECTORY));
  }

  @Provides
  @Singleton
  static EarliestAvailableBlockSlot earliestAvailableBlockSlot(
      StoreConfig storeConfig, TimeProvider timeProvider, StorageQueryChannel storageQueryChannel) {
    return new EarliestAvailableBlockSlot(
        storageQueryChannel, timeProvider, storeConfig.getEarliestAvailableBlockSlotFrequency());
  }

  @Provides
  @Singleton
  static CombinedChainDataClient combinedChainDataClient(
      Spec spec,
      StorageQueryChannel storageQueryChannel,
      RecentChainData recentChainData,
      EarliestAvailableBlockSlot earliestAvailableBlockSlot) {
    return new CombinedChainDataClient(
        recentChainData, storageQueryChannel, spec, earliestAvailableBlockSlot);
  }
}
