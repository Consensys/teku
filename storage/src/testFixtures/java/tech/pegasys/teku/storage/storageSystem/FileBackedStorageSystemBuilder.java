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

package tech.pegasys.teku.storage.storageSystem;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.nio.file.Path;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabase;
import tech.pegasys.teku.storage.server.sql.SqlDatabaseFactory;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.util.config.StateStorageMode;

public class FileBackedStorageSystemBuilder {
  // Optional
  private DatabaseVersion version = DatabaseVersion.DEFAULT_VERSION;
  private StateStorageMode storageMode = StateStorageMode.ARCHIVE;
  private StoreConfig storeConfig = StoreConfig.createDefault();

  // Version-dependent fields
  private Path dataDir;
  private Path hotDir;
  private Path archiveDir;
  private long stateStorageFrequency = 1L;

  private FileBackedStorageSystemBuilder() {}

  public static FileBackedStorageSystemBuilder create() {
    return new FileBackedStorageSystemBuilder();
  }

  public StorageSystem build() {
    final Database database;
    switch (version) {
      case V5:
        database = createV5Database();
        break;
      case V4:
        database = createV4Database();
        break;
      case V3:
        database = createV3Database();
        break;
      case SQL1:
        database = createSql1Database();
        break;
      default:
        throw new UnsupportedOperationException("Unsupported database version: " + version);
    }

    validate();
    return StorageSystem.create(database, createRestartSupplier(), storageMode, storeConfig);
  }

  private FileBackedStorageSystemBuilder copy() {
    return create()
        .version(version)
        .dataDir(dataDir)
        .storageMode(storageMode)
        .stateStorageFrequency(stateStorageFrequency)
        .storeConfig(storeConfig);
  }

  private void validate() {
    checkState(dataDir != null);
  }

  public FileBackedStorageSystemBuilder version(final DatabaseVersion version) {
    checkNotNull(version);
    this.version = version;
    return this;
  }

  public FileBackedStorageSystemBuilder dataDir(final Path dataDir) {
    checkNotNull(dataDir);
    this.dataDir = dataDir;
    this.hotDir = dataDir.resolve("hot");
    this.archiveDir = dataDir.resolve("archive");
    return this;
  }

  public FileBackedStorageSystemBuilder storageMode(final StateStorageMode storageMode) {
    checkNotNull(storageMode);
    this.storageMode = storageMode;
    return this;
  }

  public FileBackedStorageSystemBuilder stateStorageFrequency(final long stateStorageFrequency) {
    this.stateStorageFrequency = stateStorageFrequency;
    return this;
  }

  public FileBackedStorageSystemBuilder storeConfig(final StoreConfig storeConfig) {
    checkNotNull(storeConfig);
    this.storeConfig = storeConfig;
    return this;
  }

  private StorageSystem.RestartedStorageSupplier createRestartSupplier() {
    return (mode) -> copy().storageMode(mode).build();
  }

  private Database createSql1Database() {
    return SqlDatabaseFactory.create(
        hotDir, storageMode, stateStorageFrequency, new StubMetricsSystem());
  }

  private Database createV5Database() {
    return RocksDbDatabase.createV4(
        new StubMetricsSystem(),
        RocksDbConfiguration.v5HotDefaults().withDatabaseDir(hotDir),
        RocksDbConfiguration.v5ArchiveDefaults().withDatabaseDir(archiveDir),
        storageMode,
        stateStorageFrequency);
  }

  private Database createV4Database() {
    return RocksDbDatabase.createV4(
        new StubMetricsSystem(),
        RocksDbConfiguration.v3And4Settings(hotDir),
        RocksDbConfiguration.v3And4Settings(archiveDir),
        storageMode,
        stateStorageFrequency);
  }

  private Database createV3Database() {
    final RocksDbConfiguration rocksDbConfiguration = RocksDbConfiguration.v3And4Settings(dataDir);
    return RocksDbDatabase.createV3(new StubMetricsSystem(), rocksDbConfiguration, storageMode);
  }
}
