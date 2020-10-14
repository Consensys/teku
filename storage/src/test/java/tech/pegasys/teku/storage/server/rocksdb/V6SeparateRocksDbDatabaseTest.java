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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.storageSystem.FileBackedStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.util.config.StateStorageMode;

public class V6SeparateRocksDbDatabaseTest extends AbstractRocksDbDatabaseWithHotStatesTest {

  @Override
  protected StorageSystem createStorageSystem(
      final File tempDir, final StateStorageMode storageMode, final StoreConfig storeConfig) {
    Path mainDataDir = tempDir.toPath().resolve("mainDataDir");
    if (!mainDataDir.toFile().exists()) {
      assertThat(mainDataDir.toFile().mkdir()).isTrue();
    }
    Path archDataDir = tempDir.toPath().resolve("v6arch");
    if (!archDataDir.toFile().exists()) {
      assertThat(archDataDir.toFile().mkdir()).isTrue();
    }
    return FileBackedStorageSystemBuilder.create()
        .dataDir(mainDataDir)
        .v6ArchiveDir(archDataDir)
        .version(DatabaseVersion.V6)
        .storageMode(storageMode)
        .stateStorageFrequency(1L)
        .storeConfig(storeConfig)
        .build();
  }
}
