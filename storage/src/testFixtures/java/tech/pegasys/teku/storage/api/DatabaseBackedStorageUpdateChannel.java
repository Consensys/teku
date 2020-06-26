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

package tech.pegasys.teku.storage.api;

import java.util.Optional;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.async.SafeFuture;

public class DatabaseBackedStorageUpdateChannel implements StorageUpdateChannel {
  private final Database database;

  public DatabaseBackedStorageUpdateChannel(final Database database) {
    this.database = database;
  }

  @Override
  public SafeFuture<Optional<StoreBuilder>> onStoreRequest() {
    return SafeFuture.completedFuture(database.createMemoryStore());
  }

  @Override
  public SafeFuture<Void> onStorageUpdate(StorageUpdate event) {
    return SafeFuture.fromRunnable(() -> database.update(event));
  }

  @Override
  public void onGenesis(UpdatableStore store) {
    database.storeGenesis(store);
  }
}
