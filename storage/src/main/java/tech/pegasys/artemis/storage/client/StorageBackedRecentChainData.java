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

package tech.pegasys.artemis.storage.client;

import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.artemis.storage.api.StorageUpdateChannel;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

public class StorageBackedRecentChainData extends RecentChainData {
  private static final Logger LOG = LogManager.getLogger();
  private final AsyncRunner asyncRunner;

  public StorageBackedRecentChainData(
      final AsyncRunner asyncRunner,
      final StorageUpdateChannel storageUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final EventBus eventBus) {
    super(storageUpdateChannel, finalizedCheckpointChannel, eventBus);
    this.asyncRunner = asyncRunner;
    eventBus.register(this);
  }

  public static SafeFuture<RecentChainData> create(
      final AsyncRunner asyncRunner,
      final StorageUpdateChannel storageUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final EventBus eventBus) {
    StorageBackedRecentChainData client =
        new StorageBackedRecentChainData(
            asyncRunner, storageUpdateChannel, finalizedCheckpointChannel, eventBus);
    return client.initializeFromStorage();
  }

  private SafeFuture<RecentChainData> initializeFromStorage() {
    LOG.trace("Begin initializing ChainStorageClient from storage");
    return requestInitialStore()
        .thenApply(
            maybeStore -> {
              maybeStore.ifPresent(
                  (store) -> {
                    this.setStore(store);
                    LOG.debug("Finish initializing ChainStorageClient from storage");
                  });
              return this;
            });
  }

  private SafeFuture<Optional<Store>> requestInitialStore() {
    return storageUpdateChannel
        .onStoreRequest()
        .orTimeout(Constants.STORAGE_REQUEST_TIMEOUT, TimeUnit.SECONDS)
        .exceptionallyCompose(
            (err) ->
                asyncRunner.runAfterDelay(
                    this::requestInitialStore,
                    Constants.STORAGE_REQUEST_TIMEOUT,
                    TimeUnit.SECONDS));
  }
}
