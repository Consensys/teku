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
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import com.google.common.eventbus.EventBus;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.StubSpecProvider;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubChainHeadChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.store.StoreConfig;

public class MemoryOnlyRecentChainData extends RecentChainData {

  private MemoryOnlyRecentChainData(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final EventBus eventBus,
      final StorageUpdateChannel storageUpdateChannel,
      final VoteUpdateChannel voteUpdateChannel,
      final ProtoArrayStorageChannel protoArrayStorageChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final SpecProvider specProvider) {
    super(
        asyncRunner,
        metricsSystem,
        storeConfig,
        BlockProvider.NOOP,
        StateAndBlockSummaryProvider.NOOP,
        storageUpdateChannel,
        voteUpdateChannel,
        protoArrayStorageChannel,
        finalizedCheckpointChannel,
        chainHeadChannel,
        eventBus,
        specProvider);
    eventBus.register(this);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static RecentChainData create(final EventBus eventBus) {
    return builder().eventBus(eventBus).build();
  }

  public static RecentChainData create(
      final EventBus eventBus, final ChainHeadChannel chainHeadChannel) {
    return builder().eventBus(eventBus).reorgEventChannel(chainHeadChannel).build();
  }

  public static class Builder {
    private StoreConfig storeConfig = StoreConfig.createDefault();
    private SpecProvider specProvider = StubSpecProvider.createMinimal();
    private EventBus eventBus = new EventBus();
    private StorageUpdateChannel storageUpdateChannel = new StubStorageUpdateChannel();
    private FinalizedCheckpointChannel finalizedCheckpointChannel =
        new StubFinalizedCheckpointChannel();
    private ChainHeadChannel chainHeadChannel = new StubChainHeadChannel();

    public RecentChainData build() {
      return new MemoryOnlyRecentChainData(
          SYNC_RUNNER,
          new NoOpMetricsSystem(),
          storeConfig,
          eventBus,
          storageUpdateChannel,
          votes -> {},
          ProtoArrayStorageChannel.NO_OP,
          finalizedCheckpointChannel,
          chainHeadChannel,
          specProvider);
    }

    public Builder storeConfig(final StoreConfig storeConfig) {
      checkNotNull(storeConfig);
      this.storeConfig = storeConfig;
      return this;
    }

    public Builder specProvider(final SpecProvider specProvider) {
      checkNotNull(specProvider);
      this.specProvider = specProvider;
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

    public Builder reorgEventChannel(final ChainHeadChannel chainHeadChannel) {
      checkNotNull(chainHeadChannel);
      this.chainHeadChannel = chainHeadChannel;
      return this;
    }
  }
}
