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

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubReorgEventChannel;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.async.StubAsyncRunner;

public class StorageBackedRecentChainDataTest {

  private static final BeaconState INITIAL_STATE =
      new DataStructureUtil(3).randomBeaconState(UnsignedLong.ZERO);

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
    when(storageUpdateChannel.onStoreRequest()).thenReturn(storeRequestFuture);

    final EventBus eventBus = new EventBus();
    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            asyncRunner,
            storageQueryChannel,
            storageUpdateChannel,
            finalizedCheckpointChannel,
            reorgEventChannel,
            eventBus);

    // We should have posted a request to get the store from storage
    verify(storageUpdateChannel).onStoreRequest();

    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();

    // Post a store response to complete initialization
    final StoreBuilder genesisStoreBuilder =
        StoreBuilder.forkChoiceStoreBuilder(
            new StubMetricsSystem(), BlockProvider.NOOP, INITIAL_STATE);
    storeRequestFuture.complete(Optional.of(genesisStoreBuilder));
    assertThat(client).isCompleted();
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    assertThat(client.get().getStore()).isEqualTo(genesisStoreBuilder.build());
  }

  @Test
  public void storageBackedClient_storeInitializeViaNewGenesisState()
      throws ExecutionException, InterruptedException {
    SafeFuture<Optional<StoreBuilder>> storeRequestFuture = new SafeFuture<>();
    when(storageUpdateChannel.onStoreRequest()).thenReturn(storeRequestFuture);

    final EventBus eventBus = new EventBus();
    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            asyncRunner,
            storageQueryChannel,
            storageUpdateChannel,
            finalizedCheckpointChannel,
            reorgEventChannel,
            eventBus);

    // We should have posted a request to get the store from storage
    verify(storageUpdateChannel).onStoreRequest();
    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();

    // Post a store event to complete initialization
    storeRequestFuture.complete(Optional.empty());
    assertThat(client).isCompleted();
    assertStoreNotInitialized(client.get());
    assertThat(client.get().getStore()).isNull();

    // Now set the genesis state
    final UpdatableStore genesisStore =
        StoreBuilder.buildForkChoiceStore(
            new StubMetricsSystem(), BlockProvider.NOOP, INITIAL_STATE);
    client.get().initializeFromGenesis(INITIAL_STATE);
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    assertThat(client.get().getStore()).isEqualTo(genesisStore);
  }

  @Test
  public void storageBackedClient_storeInitializeViaGetStoreRequestAfterTimeout()
      throws ExecutionException, InterruptedException {
    SafeFuture<Optional<StoreBuilder>> storeRequestFuture = new SafeFuture<>();
    when(storageUpdateChannel.onStoreRequest())
        .thenReturn(SafeFuture.failedFuture(new TimeoutException()))
        .thenReturn(storeRequestFuture);

    final EventBus eventBus = new EventBus();
    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            asyncRunner,
            storageQueryChannel,
            storageUpdateChannel,
            finalizedCheckpointChannel,
            reorgEventChannel,
            eventBus);

    // We should have posted a request to get the store from storage
    verify(storageUpdateChannel).onStoreRequest();

    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();

    // Now set the genesis state
    final StoreBuilder genesisStoreBuilder =
        StoreBuilder.forkChoiceStoreBuilder(
            new StubMetricsSystem(), BlockProvider.NOOP, INITIAL_STATE);
    storeRequestFuture.complete(Optional.of(genesisStoreBuilder));
    assertThat(client).isCompleted();
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    assertThat(client.get().getStore()).isEqualTo(genesisStoreBuilder.build());
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
    assertThatThrownBy(() -> client.initializeFromGenesis(INITIAL_STATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to set genesis state: store has already been initialized");
  }
}
