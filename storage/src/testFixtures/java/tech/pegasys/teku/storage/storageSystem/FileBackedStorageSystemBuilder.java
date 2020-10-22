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
import java.util.Optional;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabase;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaHot;
import tech.pegasys.teku.storage.server.rocksdb.schema.V6SchemaFinalized;
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
  private Optional<Path> v6ArchiveDir = Optional.empty();
  private long stateStorageFrequency = 1L;

  private FileBackedStorageSystemBuilder() {}

  public static FileBackedStorageSystemBuilder create() {
    return new FileBackedStorageSystemBuilder();
  }

  public StorageSystem build() {
    final Database database;
    switch (version) {
      case V6:
        database = createV6Database();
        break;
      case V5:
        database = createV5Database();
        break;
      case V4:
        database = createV4Database();
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
        .v6ArchiveDir(v6ArchiveDir)
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

  private FileBackedStorageSystemBuilder v6ArchiveDir(Optional<Path> v6ArchiveDir) {
    this.v6ArchiveDir = v6ArchiveDir;
    return this;
  }

  public FileBackedStorageSystemBuilder v6ArchiveDir(Path v6ArchiveDir) {
    checkNotNull(dataDir);
    this.v6ArchiveDir = Optional.of(v6ArchiveDir.resolve("archive"));
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

  private Database createV6Database() {
    RocksDbConfiguration hotConfigDefault =
        v6ArchiveDir.isPresent()
            ? RocksDbConfiguration.v5HotDefaults()
            : RocksDbConfiguration.v6SingleDefaults();
    Optional<RocksDbConfiguration> coldConfig =
        v6ArchiveDir.map(dir -> RocksDbConfiguration.v5ArchiveDefaults().withDatabaseDir(dir));

    return RocksDbDatabase.createV6(
        new StubMetricsSystem(),
        hotConfigDefault.withDatabaseDir(hotDir),
        coldConfig,
        V4SchemaHot.INSTANCE,
        V6SchemaFinalized.INSTANCE,
        storageMode,
        stateStorageFrequency);
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
        RocksDbConfiguration.v4Settings(hotDir),
        RocksDbConfiguration.v4Settings(archiveDir),
        storageMode,
        stateStorageFrequency);
  }
}
