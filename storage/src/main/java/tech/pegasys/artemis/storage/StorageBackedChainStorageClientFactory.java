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

package tech.pegasys.artemis.storage;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.storage.events.GetStoreRequest;
import tech.pegasys.artemis.storage.events.GetStoreResponse;
import tech.pegasys.artemis.storage.events.StoreInitializedFromStorageEvent;
import tech.pegasys.artemis.util.async.SafeFuture;

class StorageBackedChainStorageClientFactory {
  private static final Logger LOG = LogManager.getLogger();
  private final ChainStorageClient client;
  private final EventBus eventBus;

  private final AtomicBoolean initializationStarted = new AtomicBoolean(false);
  private final SafeFuture<ChainStorageClient> initializationCompleted = new SafeFuture<>();
  private volatile OptionalLong getStoreRequestId = OptionalLong.empty();

  public StorageBackedChainStorageClientFactory(final EventBus eventBus) {
    this.client = new ChainStorageClient(eventBus, TransactionPrecommit.storageEnabled(eventBus));
    this.eventBus = eventBus;
    eventBus.register(client);
  }

  public SafeFuture<ChainStorageClient> get() {
    initializeFromStorage();
    return initializationCompleted;
  }

  private void initializeFromStorage() {
    if (initializationStarted.compareAndSet(false, true)) {
      LOG.trace("Begin initializing {} from storage", client.getClass().getSimpleName());
      final GetStoreRequest storeRequest = new GetStoreRequest();
      this.getStoreRequestId = OptionalLong.of(storeRequest.getId());
      eventBus.post(storeRequest);
    }
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onStoreResponse(GetStoreResponse response) {
    if (getStoreRequestId.isEmpty() || getStoreRequestId.getAsLong() != response.getRequestId()) {
      // This isn't a response to our query
      return;
    }
    response.getStore().ifPresent(client::setStore);
    if (initializationCompleted.complete(client)) {
      LOG.trace("Finish initializing {} from storage", client.getClass().getSimpleName());
    }
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onStoreInitializedFromStorage(
      final StoreInitializedFromStorageEvent storeInitializedEvent) {
    client.setStore(storeInitializedEvent.getStore());
    if (initializationCompleted.complete(client)) {
      LOG.trace("Finish initializing {} from storage", client.getClass().getSimpleName());
    }
  }
}
