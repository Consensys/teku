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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.eventbus.EventBus;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockProvider;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.protoarray.StubProtoArrayStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubReorgEventChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class MemoryOnlyRecentChainData extends RecentChainData {

  private MemoryOnlyRecentChainData(
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final EventBus eventBus,
      final StorageUpdateChannel storageUpdateChannel,
      final ProtoArrayStorageChannel protoArrayStorageChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ReorgEventChannel reorgEventChannel) {
    super(
        metricsSystem,
        storeConfig,
        BlockProvider.NOOP,
        StateAndBlockProvider.NOOP,
        storageUpdateChannel,
        protoArrayStorageChannel,
        finalizedCheckpointChannel,
        reorgEventChannel,
        eventBus);
    eventBus.register(this);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static RecentChainData create(final EventBus eventBus) {
    return builder().eventBus(eventBus).build();
  }

  public static RecentChainData create(
      final EventBus eventBus, final ReorgEventChannel reorgEventChannel) {
    return builder().eventBus(eventBus).reorgEventChannel(reorgEventChannel).build();
  }

  public static RecentChainData createWithStore(
      final EventBus eventBus,
      final ReorgEventChannel reorgEventChannel,
      final UpdatableStore store) {
    final RecentChainData recentChainData =
        builder().eventBus(eventBus).reorgEventChannel(reorgEventChannel).build();
    recentChainData.setStore(store);
    return recentChainData;
  }

  public static class Builder {
    private StoreConfig storeConfig = StoreConfig.createDefault();
    private EventBus eventBus = new EventBus();
    private StorageUpdateChannel storageUpdateChannel = new StubStorageUpdateChannel();
    private ProtoArrayStorageChannel protoArrayStorageChannel = new StubProtoArrayStorageChannel();
    private FinalizedCheckpointChannel finalizedCheckpointChannel =
        new StubFinalizedCheckpointChannel();
    private ReorgEventChannel reorgEventChannel = new StubReorgEventChannel();

    public RecentChainData build() {
      return new MemoryOnlyRecentChainData(
          new NoOpMetricsSystem(),
          storeConfig,
          eventBus,
          storageUpdateChannel,
          protoArrayStorageChannel,
          finalizedCheckpointChannel,
          reorgEventChannel);
    }

    public Builder storeConfig(final StoreConfig storeConfig) {
      checkNotNull(storeConfig);
      this.storeConfig = storeConfig;
      return this;
    }

    public Builder eventBus(final EventBus eventBus) {
      checkNotNull(eventBus);
      this.eventBus = eventBus;
      return this;
    }

    public Builder storageUpdateChannel(final StorageUpdateChannel storageUpdateChannel) {
      checkNotNull(storageUpdateChannel);
      this.storageUpdateChannel = storageUpdateChannel;
      return this;
    }

    public Builder finalizedCheckpointChannel(
        final FinalizedCheckpointChannel finalizedCheckpointChannel) {
      checkNotNull(finalizedCheckpointChannel);
      this.finalizedCheckpointChannel = finalizedCheckpointChannel;
      return this;
    }

    public Builder reorgEventChannel(final ReorgEventChannel reorgEventChannel) {
      checkNotNull(reorgEventChannel);
      this.reorgEventChannel = reorgEventChannel;
      return this;
    }
  }
}
