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
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaFinalized;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaHot;
import tech.pegasys.teku.util.config.StateStorageMode;

public class InMemoryV4RocksDbDatabaseTest extends V4RocksDbDatabaseTest {
  private final Map<File, MockRocksDbInstance> existingDatabaseInstances = new HashMap<>();

  @Override
  protected Database createDatabase(final File tempDir, final StateStorageMode storageMode) {
    final RocksDbAccessor hotDb =
        existingDatabaseInstances.compute(
            new File(tempDir, "db"),
            (__, existingDb) -> {
              if (existingDb == null) {
                return MockRocksDbInstance.createEmpty(V4SchemaHot.class);
              } else {
                return existingDb.reopen();
              }
            });
    final RocksDbAccessor finalizedDb =
        existingDatabaseInstances.compute(
            new File(tempDir, "archive"),
            (__, existingDb) -> {
              if (existingDb == null) {
                return MockRocksDbInstance.createEmpty(V4SchemaFinalized.class);
              } else {
                return existingDb.reopen();
              }
            });
    return RocksDbDatabase.createV4(new StubMetricsSystem(), hotDb, finalizedDb, storageMode);
  }
}
