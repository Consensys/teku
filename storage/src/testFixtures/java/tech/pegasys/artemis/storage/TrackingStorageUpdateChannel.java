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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.artemis.storage.api.StorageUpdateChannel;
import tech.pegasys.artemis.storage.events.StorageUpdate;
import tech.pegasys.artemis.storage.events.StorageUpdateResult;
import tech.pegasys.artemis.storage.server.Database;
import tech.pegasys.artemis.util.async.SafeFuture;

public class TrackingStorageUpdateChannel implements StorageUpdateChannel {
  private final Database database;
  private final List<StorageUpdateResult> updateResults = new ArrayList<>();

  public TrackingStorageUpdateChannel(final Database database) {
    this.database = database;
  }

  public List<StorageUpdateResult> getStorageUpdates() {
    return updateResults;
  }

  @Override
  public SafeFuture<Optional<Store>> onStoreRequest() {
    return SafeFuture.completedFuture(database.createMemoryStore());
  }

  @Override
  public SafeFuture<StorageUpdateResult> onStorageUpdate(StorageUpdate event) {
    return SafeFuture.of(
        () -> {
          final StorageUpdateResult result = database.update(event);
          updateResults.add(result);
          return result;
        });
  }

  @Override
  public void onGenesis(Store store) {}
}
