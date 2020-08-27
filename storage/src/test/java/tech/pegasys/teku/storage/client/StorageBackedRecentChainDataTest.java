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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import com.google.common.eventbus.EventBus;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockProvider;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.protoarray.StubProtoArrayStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubReorgEventChannel;
import tech.pegasys.teku.storage.events.AnchorPoint;
import tech.pegasys.teku.storage.store.StoreAssertions;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class StorageBackedRecentChainDataTest {

  private static final BeaconState INITIAL_STATE =
      new DataStructureUtil(3).randomBeaconState(UInt64.ZERO);

  private final StorageQueryChannel storageQueryChannel = mock(StorageQueryChannel.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
  private final FinalizedCheckpointChannel finalizedCheckpointChannel =
      new StubFinalizedCheckpointChannel();
  private final ReorgEventChannel reorgEventChannel = new StubReorgEventChannel();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @Test
  public void storageBackedClient_storeInitializeViaGetStoreRequest()
      throws ExecutionException, InterruptedException {
    SafeFuture<Optional<StoreBuilder>> storeRequestFuture = new SafeFuture<>();
    when(storageQueryChannel.onStoreRequest()).thenReturn(storeRequestFuture);

    final EventBus eventBus = new EventBus();
    final StoreConfig storeConfig =
        StoreConfig.builder().hotStatePersistenceFrequencyInEpochs(5).build();
    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            storeConfig,
            asyncRunner,
            storageQueryChannel,
            storageUpdateChannel,
            new StubProtoArrayStorageChannel(),
            finalizedCheckpointChannel,
            reorgEventChannel,
            eventBus);

    // We should have posted a request to get the store from storage
    verify(storageQueryChannel).onStoreRequest();

    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();

    // Post a store response to complete initialization
    final StoreBuilder genesisStoreBuilder =
        StoreBuilder.forkChoiceStoreBuilder(
            SYNC_RUNNER,
            new StubMetricsSystem(),
            BlockProvider.NOOP,
            StateAndBlockProvider.NOOP,
            AnchorPoint.fromGenesisState(INITIAL_STATE));
    storeRequestFuture.complete(Optional.of(genesisStoreBuilder));
    assertThat(client).isCompleted();
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    final UpdatableStore expectedStore =
        genesisStoreBuilder.storeConfig(storeConfig).build().join();
    StoreAssertions.assertStoresMatch(client.get().getStore(), expectedStore);
  }

  @Test
  public void storageBackedClient_storeInitializeViaNewGenesisState()
      throws ExecutionException, InterruptedException {
    SafeFuture<Optional<StoreBuilder>> storeRequestFuture = new SafeFuture<>();
    when(storageQueryChannel.onStoreRequest()).thenReturn(storeRequestFuture);

    final EventBus eventBus = new EventBus();
    final StoreConfig storeConfig =
        StoreConfig.builder().hotStatePersistenceFrequencyInEpochs(5).build();
    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            storeConfig,
            asyncRunner,
            storageQueryChannel,
            storageUpdateChannel,
            new StubProtoArrayStorageChannel(),
            finalizedCheckpointChannel,
            reorgEventChannel,
            eventBus);

    // We should have posted a request to get the store from storage
    verify(storageQueryChannel).onStoreRequest();
    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();

    // Post a store event to complete initialization
    storeRequestFuture.complete(Optional.empty());
    assertThat(client).isCompleted();
    assertStoreNotInitialized(client.get());
    assertThat(client.get().getStore()).isNull();

    // Now set the genesis state
    final UpdatableStore genesisStore =
        StoreBuilder.forkChoiceStoreBuilder(
                SYNC_RUNNER,
                new StubMetricsSystem(),
                BlockProvider.NOOP,
                StateAndBlockProvider.NOOP,
                AnchorPoint.fromGenesisState(INITIAL_STATE))
            .storeConfig(storeConfig)
            .build()
            .join();
    final SafeFuture<Void> initialized = client.get().initializeFromGenesis(INITIAL_STATE);
    assertThat(initialized).isCompleted();
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    StoreAssertions.assertStoresMatch(client.get().getStore(), genesisStore);
  }

  @Test
  public void storageBackedClient_storeInitializeViaGetStoreRequestAfterTimeout()
      throws ExecutionException, InterruptedException {
    SafeFuture<Optional<StoreBuilder>> storeRequestFuture = new SafeFuture<>();
    when(storageQueryChannel.onStoreRequest())
        .thenReturn(SafeFuture.failedFuture(new TimeoutException()))
        .thenReturn(storeRequestFuture);

    final EventBus eventBus = new EventBus();
    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            StoreConfig.createDefault(),
            asyncRunner,
            storageQueryChannel,
            storageUpdateChannel,
            new StubProtoArrayStorageChannel(),
            finalizedCheckpointChannel,
            reorgEventChannel,
            eventBus);

    // We should have posted a request to get the store from storage
    verify(storageQueryChannel).onStoreRequest();

    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();

    // Now set the genesis state
    final StoreBuilder genesisStoreBuilder =
        StoreBuilder.forkChoiceStoreBuilder(
            SYNC_RUNNER,
            new StubMetricsSystem(),
            BlockProvider.NOOP,
            StateAndBlockProvider.NOOP,
            AnchorPoint.fromGenesisState(INITIAL_STATE));
    storeRequestFuture.complete(Optional.of(genesisStoreBuilder));
    assertThat(client).isCompleted();
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    StoreAssertions.assertStoresMatch(client.get().getStore(), genesisStoreBuilder.build().join());
  }

  @Test
  public void storageBackedClient_storeInitializeViaGetStoreRequestAfterIOException()
      throws ExecutionException, InterruptedException {
    SafeFuture<Optional<StoreBuilder>> storeRequestFuture = new SafeFuture<>();
    when(storageQueryChannel.onStoreRequest())
        .thenReturn(SafeFuture.failedFuture(new IOException()))
        .thenReturn(storeRequestFuture);

    final EventBus eventBus = new EventBus();
    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            StoreConfig.createDefault(),
            asyncRunner,
            storageQueryChannel,
            storageUpdateChannel,
            new StubProtoArrayStorageChannel(),
            finalizedCheckpointChannel,
            reorgEventChannel,
            eventBus);

    // We should have posted a request to get the store from storage
    verify(storageQueryChannel).onStoreRequest();

    assertThat(client).isCompletedExceptionally();
  }

  private void assertStoreInitialized(final RecentChainData client) {
    final AtomicBoolean initialized = new AtomicBoolean(false);
    client.subscribeStoreInitialized(() -> initialized.set(true));
    assertThat(initialized).isTrue();
  }

  private void assertStoreNotInitialized(final RecentChainData client) {
    final AtomicBoolean initialized = new AtomicBoolean(false);
    client.subscribeStoreInitialized(() -> initialized.set(true));
    assertThat(initialized).isFalse();
  }

  private void assertStoreIsSet(final RecentChainData client) {
    assertThat(client.getStore()).isNotNull();

    // With a store set, we shouldn't be allowed to overwrite the store by setting the genesis state
    final SafeFuture<Void> initialized = client.initializeFromGenesis(INITIAL_STATE);
    assertThat(initialized).isCompletedExceptionally();
    assertThatThrownBy(initialized::get)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to set genesis state: store has already been initialized");
  }
}
