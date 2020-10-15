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

package tech.pegasys.teku.storage.server.rocksdb;

import java.io.File;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.storageSystem.FileBackedStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.util.config.StateStorageMode;

public class V6SingleRocksDbDatabaseTest extends AbstractRocksDbDatabaseWithHotStatesTest {

  @Override
  protected StorageSystem createStorageSystem(
      final File tempDir, final StateStorageMode storageMode, final StoreConfig storeConfig) {
    return FileBackedStorageSystemBuilder.create()
        .dataDir(tempDir.toPath())
        .version(DatabaseVersion.V6)
        .storageMode(storageMode)
        .stateStorageFrequency(1L)
        .storeConfig(storeConfig)
        .build();
  }
}
