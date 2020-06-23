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
import java.nio.file.Path;
import java.nio.file.Paths;
import tech.pegasys.teku.storage.storageSystem.FileBackedStorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.config.StateStorageMode;

public class V4RocksDbDatabaseTest extends AbstractRocksDbDatabaseTest {

  @Override
  protected StorageSystem createStorageSystem(
      final File tempDir, final StateStorageMode storageMode) {
    final Path dbDir = Paths.get(tempDir.getAbsolutePath(), "db");
    final Path archiveDir = Paths.get(tempDir.getAbsolutePath(), "archive");

    return FileBackedStorageSystem.createV4StorageSystem(dbDir, archiveDir, storageMode);
  }
}
