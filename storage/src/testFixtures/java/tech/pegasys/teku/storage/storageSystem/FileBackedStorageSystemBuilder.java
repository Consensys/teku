/*
 * Copyright ConsenSys Software Inc., 2022
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
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.leveldb.LevelDbDatabaseFactory;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabaseFactory;
import tech.pegasys.teku.storage.store.StoreConfig;

public class FileBackedStorageSystemBuilder {
  // Optional
  private DatabaseVersion version = DatabaseVersion.DEFAULT_VERSION;
  private StateStorageMode storageMode = StateStorageMode.ARCHIVE;
  private StoreConfig storeConfig = StoreConfig.createDefault();

  private Optional<AsyncRunner> asyncRunner = Optional.empty();
  private Spec spec;

  // Version-dependent fields
  private Path dataDir;
  private Path hotDir;
  private Path archiveDir;
  private long stateStorageFrequency = 1L;
  private boolean storeNonCanonicalBlocks = false;
  private boolean storeVotesEquivocation = false;
  private boolean storeBlockExecutionPayloadSeparately = false;

  private FileBackedStorageSystemBuilder() {}

  public static FileBackedStorageSystemBuilder create() {
    return new FileBackedStorageSystemBuilder();
  }

  public StorageSystem build() {
    final Database database = buildDatabaseOnly();

    validate();
    return StorageSystem.create(
        database,
        createRestartSupplier(),
        storageMode,
        storeConfig,
        spec,
        ChainBuilder.create(spec));
  }

  public Database buildDatabaseOnly() {
    final Database database;
    switch (version) {
      case LEVELDB_TREE:
        database = createLevelDbTrieDatabase();
        break;
      case LEVELDB2:
        database = createLevelDb2Database();
        break;
      case LEVELDB1:
        database = createLevelDb1Database();
        break;
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
    return database;
  }

  private FileBackedStorageSystemBuilder copy() {
    return create()
        .specProvider(spec)
        .version(version)
        .dataDir(dataDir)
        .storageMode(storageMode)
        .asyncRunner(asyncRunner)
        .stateStorageFrequency(stateStorageFrequency)
        .storeBlockExecutionPayloadSeparately(storeBlockExecutionPayloadSeparately)
        .storeConfig(storeConfig);
  }

  private void validate() {
    checkNotNull(spec, "Must specify spec");
    checkState(dataDir != null);
  }

  public FileBackedStorageSystemBuilder version(final DatabaseVersion version) {
    checkNotNull(version);
    this.version = version;
    return this;
  }

  public FileBackedStorageSystemBuilder asyncRunner(final Optional<AsyncRunner> asyncRunner) {
    this.asyncRunner = asyncRunner;
    return this;
  }

  public FileBackedStorageSystemBuilder storeNonCanonicalBlocks(
      final boolean storeNonCanonicalBlocks) {
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    return this;
  }

  public FileBackedStorageSystemBuilder storeBlockExecutionPayloadSeparately(
      final boolean storeBlockExecutionPayloadSeparately) {
    this.storeBlockExecutionPayloadSeparately = storeBlockExecutionPayloadSeparately;
    return this;
  }

  public FileBackedStorageSystemBuilder specProvider(final Spec spec) {
    this.spec = spec;
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

  private Database createLevelDb1Database() {
    return LevelDbDatabaseFactory.createLevelDb(
        new StubMetricsSystem(),
        KvStoreConfiguration.v5HotDefaults().withDatabaseDir(hotDir),
        KvStoreConfiguration.v5ArchiveDefaults().withDatabaseDir(archiveDir),
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        storeVotesEquivocation,
        asyncRunner,
        spec);
  }

  private Database createV6Database() {
    KvStoreConfiguration configDefault = KvStoreConfiguration.v6SingleDefaults();

    final V6SchemaCombinedSnapshot schema =
        V6SchemaCombinedSnapshot.createV6(spec, storeVotesEquivocation);
    return RocksDbDatabaseFactory.createV6(
        new StubMetricsSystem(),
        configDefault.withDatabaseDir(hotDir),
        schema,
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        asyncRunner,
        spec);
  }

  private Database createLevelDb2Database() {
    KvStoreConfiguration configDefault = KvStoreConfiguration.v6SingleDefaults();
    return LevelDbDatabaseFactory.createLevelDbV2(
        new StubMetricsSystem(),
        configDefault.withDatabaseDir(hotDir),
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        storeVotesEquivocation,
        asyncRunner,
        spec);
  }

  private Database createLevelDbTrieDatabase() {
    KvStoreConfiguration configDefault = KvStoreConfiguration.v6SingleDefaults();
    return LevelDbDatabaseFactory.createLevelDbTree(
        new StubMetricsSystem(),
        configDefault.withDatabaseDir(hotDir),
        storageMode,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        10_000,
        storeVotesEquivocation,
        asyncRunner,
        spec);
  }

  private Database createV5Database() {
    return RocksDbDatabaseFactory.createV4(
        new StubMetricsSystem(),
        KvStoreConfiguration.v5HotDefaults().withDatabaseDir(hotDir),
        KvStoreConfiguration.v5ArchiveDefaults().withDatabaseDir(archiveDir),
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeVotesEquivocation,
        storeBlockExecutionPayloadSeparately,
        asyncRunner,
        spec);
  }

  private Database createV4Database() {
    return RocksDbDatabaseFactory.createV4(
        new StubMetricsSystem(),
        KvStoreConfiguration.v4Settings(hotDir),
        KvStoreConfiguration.v4Settings(archiveDir),
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeVotesEquivocation,
        storeBlockExecutionPayloadSeparately,
        asyncRunner,
        spec);
  }
}
