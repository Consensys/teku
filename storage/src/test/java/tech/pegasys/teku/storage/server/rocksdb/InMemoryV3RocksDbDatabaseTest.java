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
import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.rocksdb.core.MockRocksDbInstance;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor;
import tech.pegasys.teku.storage.server.rocksdb.schema.V3Schema;
import tech.pegasys.teku.util.config.StateStorageMode;

public class InMemoryV3RocksDbDatabaseTest extends V3RocksDbDatabaseTest {

  private final Map<File, MockRocksDbInstance> existingDatabaseInstances = new HashMap<>();

  @Override
  protected Database createDatabase(final File tempDir, final StateStorageMode storageMode) {
    final RocksDbAccessor db =
        existingDatabaseInstances.compute(
            tempDir,
            (__, existingDb) -> {
              if (existingDb == null) {
                return MockRocksDbInstance.createEmpty(V3Schema.class);
              } else {
                return existingDb.reopen();
              }
            });

    return RocksDbDatabase.createV3(new StubMetricsSystem(), db, storageMode);
  }
}
